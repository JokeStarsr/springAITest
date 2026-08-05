package org.example.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final TokenTextSplitter splitter = new TokenTextSplitter();
    private final KnowledgeBaseService kbService;

    @Value("classpath:materials/*")
    private Resource[] materialResources;

    public RagService(VectorStore vectorStore, ChatClient chatClient, KnowledgeBaseService kbService) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.kbService = kbService;
    }

    public int ingestMaterials() {
        var defaultKb = kbService.get(1);
        if (defaultKb == null) {
            defaultKb = kbService.create("default", "默认知识库（从 classpath:materials/ 导入）");
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

    public String query(String question) {
        return query(1, question);
    }

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
}