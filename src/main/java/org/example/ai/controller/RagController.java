package org.example.ai.controller;

import org.example.ai.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG 快捷路由（默认知识库 = id 1）。多知识库能力见 /api/knowledge-bases。
 */
@RestController
@RequestMapping("/rag")
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class RagController {

    private final KnowledgeBaseService kbService;

    @Value("classpath:materials/*")
    private Resource[] materialResources;

    public RagController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest() {
        int count = kbService.ingestClasspathMaterials(materialResources);
        return Map.of("status", "ok", "chunksIngested", count);
    }

    @GetMapping("/query")
    public Map<String, String> query(@RequestParam String question) {
        String answer = kbService.query(1, question);
        return Map.of("question", question, "answer", answer);
    }
}