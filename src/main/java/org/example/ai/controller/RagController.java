package org.example.ai.controller;

import org.example.ai.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    @PostMapping("/ingest")
    public Map<String, Object> ingest() {
        int count = ragService.ingestMaterials();
        return Map.of("status", "ok", "chunksIngested", count);
    }

    @GetMapping("/query")
    public Map<String, String> query(@RequestParam String question) {
        String answer = ragService.query(question);
        return Map.of("question", question, "answer", answer);
    }
}