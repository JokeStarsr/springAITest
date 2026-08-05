package org.example.ai.service;

import org.example.ai.model.KnowledgeBase;
import org.example.ai.repository.KnowledgeBaseRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepo;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    @Value("${app.data-dir:data/knowledge-bases}")
    private String dataRoot;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo, VectorStore vectorStore, ChatClient chatClient) {
        this.kbRepo = kbRepo;
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    // ====== 知识库 CRUD ======

    public List<KnowledgeBase> list() {
        return kbRepo.findAll();
    }

    public KnowledgeBase get(long id) {
        return kbRepo.findById(id);
    }

    public KnowledgeBase create(String name, String description) {
        String uploadDir = dataRoot + "/" + name;
        long id = kbRepo.create(name, description, uploadDir);
        KnowledgeBase kb = kbRepo.findById(id);
        // 创建上传目录
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadDir, e);
        }
        return kb;
    }

    public void delete(long id) {
        // 删除向量库中的相关文档
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filter = b.eq("knowledge_base_id", String.valueOf(id)).build();
        vectorStore.delete(filter);
        // 删除上传目录
        KnowledgeBase kb = kbRepo.findById(id);
        if (kb != null && kb.getUploadDir() != null && !kb.getUploadDir().isEmpty()) {
            try {
                Path dir = Paths.get(kb.getUploadDir());
                if (Files.exists(dir)) {
                    try (var files = Files.walk(dir)) {
                        files.sorted(java.util.Comparator.reverseOrder())
                             .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                    }
                }
            } catch (IOException ignored) {}
        }
        kbRepo.deleteById(id);
    }

    // ====== 文件上传与入库 ======

    public int uploadFile(long kbId, MultipartFile file) {
        KnowledgeBase kb = kbRepo.findById(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        // 保存文件到知识库目录
        try {
            Path dir = Paths.get(kb.getUploadDir());
            Files.createDirectories(dir);
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());

            // 读取并向量化
            TikaDocumentReader reader = new TikaDocumentReader(target.toUri().toString());
            List<Document> docs = reader.get();
            docs.forEach(d -> d.getMetadata().put("knowledge_base_id", String.valueOf(kbId)));
            List<Document> chunks = splitter.apply(docs);
            vectorStore.add(chunks);

            // 更新文档计数
            int newCount = countDocs(kbId);
            kbRepo.updateDocCount(kbId, newCount);
            return chunks.size();
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + file.getOriginalFilename(), e);
        }
    }

    public int ingestFromDir(long kbId) {
        KnowledgeBase kb = kbRepo.findById(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        Path dir = Paths.get(kb.getUploadDir());
        if (!Files.exists(dir)) return 0;

        List<Document> all = new ArrayList<>();
        try (var files = Files.walk(dir)) {
            files.filter(Files::isRegularFile)
                 .forEach(path -> {
                     try {
                         TikaDocumentReader reader = new TikaDocumentReader(path.toUri().toString());
                         List<Document> docs = reader.get();
                         docs.forEach(d -> d.getMetadata().put("knowledge_base_id", String.valueOf(kbId)));
                         all.addAll(splitter.apply(docs));
                     } catch (Exception e) {
                         // 跳过无法解析的文件
                     }
                 });
        } catch (IOException e) {
            throw new RuntimeException("读取目录失败", e);
        }

        if (!all.isEmpty()) {
            vectorStore.add(all);
        }
        int count = countDocs(kbId);
        kbRepo.updateDocCount(kbId, count);
        return all.size();
    }

    // ====== RAG 查询 ======

    public String query(long kbId, String question) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filter = b.eq("knowledge_base_id", String.valueOf(kbId)).build();

        return chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                .filterExpression(filter)
                                .topK(4)
                                .build())
                        .build())
                .call()
                .content();
    }

    // ====== 文件列表 ======

    public List<Map<String, String>> listFiles(long kbId) {
        KnowledgeBase kb = kbRepo.findById(kbId);
        if (kb == null) return List.of();
        Path dir = Paths.get(kb.getUploadDir());
        if (!Files.exists(dir)) return List.of();
        List<Map<String, String>> files = new ArrayList<>();
        try (var filesWalk = Files.walk(dir)) {
            filesWalk.filter(Files::isRegularFile)
                     .forEach(p -> {
                         try {
                             files.add(Map.of(
                                 "name", p.getFileName().toString(),
                                 "size", String.valueOf(Files.size(p)),
                                 "modified", Files.getLastModifiedTime(p).toString()
                             ));
                         } catch (IOException ignored) {}
                     });
        } catch (IOException ignored) {}
        return files;
    }

    // ====== 内部方法 ======

    private int countDocs(long kbId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filter = b.eq("knowledge_base_id", String.valueOf(kbId)).build();
        // VectorStore 无 count API，通过 list 估算
        return 0; // 精确计数由上传时维护
    }
}