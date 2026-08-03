package org.example.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

    @Value("classpath:materials/*")
    private Resource[] materialResources;

    public RagService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    public int ingestMaterials() {
        List<Document> all = new ArrayList<>();
        for (Resource res : materialResources) {
            String name = res.getFilename();
            if (name == null || name.startsWith("README")) {
                continue;
            }
            DocumentReader reader = new TikaDocumentReader(res);
            List<Document> docs = reader.get();
            all.addAll(splitter.apply(docs));
        }
        if (!all.isEmpty()) {
            vectorStore.add(all);
        }
        return all.size();
    }

    public String query(String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(4).build())
                        .build())
                .call()
                .content();
    }
}