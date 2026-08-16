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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class KnowledgeBaseService {

    private static final Pattern KB_NAME_PATTERN = Pattern.compile("^[\\w\\u4e00-\\u9fa5-]{1,50}$");
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv");

    private final KnowledgeBaseRepository kbRepo;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    @Value("${app.data-dir:/opt/springaitest/data}")
    private String dataRoot;

    @Value("${app.max-upload-mb:20}")
    private long maxUploadMb;

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

    /**
     * 创建知识库：目录与名称解耦（kb-{id}），名称白名单校验，杜绝路径穿越。
     */
    public KnowledgeBase create(String name, String description) {
        if (name == null || !KB_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("知识库名称仅支持中文/字母/数字/下划线/连字符，1-50 个字符");
        }
        String rawDir = dataRoot + "/knowledge-bases/tmp-" + name;
        long id = kbRepo.create(name, description, rawDir);
        String uploadDir = dataRoot + "/knowledge-bases/kb-" + id;
        kbRepo.updateUploadDir(id, uploadDir);
        try {
            Files.createDirectories(Paths.get(uploadDir));
            cleanupTmpDirIfAny(Paths.get(rawDir));
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadDir, e);
        }
        return kbRepo.findById(id);
    }

    private void cleanupTmpDirIfAny(Path tmpDir) {
        if (!Files.exists(tmpDir)) return;
        try (var files = Files.walk(tmpDir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    public void delete(long id) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var filter = b.eq("knowledge_base_id", String.valueOf(id)).build();
        vectorStore.delete(filter);
        KnowledgeBase kb = kbRepo.findById(id);
        if (kb != null && kb.getUploadDir() != null && !kb.getUploadDir().isEmpty()) {
            Path dir = Paths.get(kb.getUploadDir());
            if (Files.exists(dir) && isInsideDataRoot(dir)) {
                try (var files = Files.walk(dir)) {
                    files.sorted(Comparator.reverseOrder())
                         .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                } catch (IOException ignored) {}
            }
        }
        kbRepo.deleteById(id);
    }

    /** 防御性校验：绝不允许删除/写出 dataRoot 之外的目录 */
    private boolean isInsideDataRoot(Path dir) {
        Path root = Paths.get(dataRoot).toAbsolutePath().normalize();
        return dir.toAbsolutePath().normalize().startsWith(root);
    }

    // ====== 文件上传与入库 ======

    public int uploadFile(long kbId, MultipartFile file) {
        KnowledgeBase kb = kbRepo.findById(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        String originalName = file.getOriginalFilename();
        String ext = originalName == null ? "" : originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型: " + (ext.isEmpty() ? "无扩展名" : ext)
                    + "，支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        if (file.getSize() > maxUploadMb * 1024 * 1024) {
            throw new IllegalArgumentException("文件超过大小限制（" + maxUploadMb + "MB）");
        }

        try {
            Path dir = Paths.get(kb.getUploadDir());
            if (!isInsideDataRoot(dir)) {
                throw new IllegalArgumentException("非法知识库目录配置");
            }
            Files.createDirectories(dir);
            String fileName = System.currentTimeMillis() + "_" + originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());

            TikaDocumentReader reader = new TikaDocumentReader(target.toUri().toString());
            List<Document> docs = reader.get();
            docs.forEach(d -> d.getMetadata().put("knowledge_base_id", String.valueOf(kbId)));
            List<Document> chunks = splitter.apply(docs);
            vectorStore.add(chunks);

            int newCount = kb.getDocCount() + 1;
            kbRepo.updateDocCount(kbId, newCount);
            return chunks.size();
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + originalName, e);
        }
    }

    public int ingestFromDir(long kbId) {
        KnowledgeBase kb = kbRepo.findById(kbId);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + kbId);

        Path dir = Paths.get(kb.getUploadDir());
        if (!Files.exists(dir)) return 0;

        List<Document> all = new ArrayList<>();
        final long[] fileCount = {0};
        try (var files = Files.walk(dir)) {
            files.filter(Files::isRegularFile)
                 .forEach(path -> {
                     try {
                         TikaDocumentReader reader = new TikaDocumentReader(path.toUri().toString());
                         List<Document> docs = reader.get();
                         docs.forEach(d -> d.getMetadata().put("knowledge_base_id", String.valueOf(kbId)));
                         all.addAll(splitter.apply(docs));
                         fileCount[0]++;
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
        kbRepo.updateDocCount(kbId, kb.getDocCount() + (int) fileCount[0]);
        return all.size();
    }

    /** 将 classpath:materials/ 下的材料写入默认知识库（原 RagService.ingestMaterials） */
    public int ingestClasspathMaterials(Resource[] materialResources) {
        KnowledgeBase defaultKb = kbRepo.findById(1);
        if (defaultKb == null) {
            defaultKb = create("default", "默认知识库（从 classpath:materials/ 导入）");
        }

        List<Document> all = new ArrayList<>();
        for (Resource res : materialResources) {
            String name = res.getFilename();
            if (name == null || name.startsWith("README")) {
                continue;
            }
            try {
                TikaDocumentReader reader = new TikaDocumentReader(res);
                List<Document> docs = reader.get();
                docs.forEach(d -> d.getMetadata().put("knowledge_base_id", "1"));
                all.addAll(splitter.apply(docs));
            } catch (Exception ignored) {
            }
        }
        if (!all.isEmpty()) {
            vectorStore.add(all);
        }
        return all.size();
    }

    // ====== RAG 查询 ======

    public String query(long kbId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
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
}