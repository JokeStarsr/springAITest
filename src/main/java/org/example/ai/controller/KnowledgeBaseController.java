package org.example.ai.controller;

import org.example.ai.model.KnowledgeBase;
import org.example.ai.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    public KnowledgeBaseController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    @GetMapping
    public List<KnowledgeBase> list() {
        return kbService.list();
    }

    @PostMapping
    public KnowledgeBase create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.getOrDefault("description", "");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        return kbService.create(name.trim(), description.trim());
    }

    @GetMapping("/{id}")
    public KnowledgeBase get(@PathVariable long id) {
        KnowledgeBase kb = kbService.get(id);
        if (kb == null) throw new IllegalArgumentException("知识库不存在: " + id);
        return kb;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        kbService.delete(id);
        return Map.of("status", "ok", "id", id);
    }

    @PostMapping("/{id}/upload")
    public Map<String, Object> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        int chunks = kbService.uploadFile(id, file);
        return Map.of("status", "ok", "chunksIngested", chunks, "fileName", file.getOriginalFilename());
    }

    @PostMapping("/{id}/ingest")
    public Map<String, Object> ingest(@PathVariable long id) {
        int chunks = kbService.ingestFromDir(id);
        return Map.of("status", "ok", "chunksIngested", chunks);
    }

    @GetMapping("/{id}/query")
    public Map<String, String> query(@PathVariable long id, @RequestParam String question) {
        String answer = kbService.query(id, question);
        return Map.of("question", question, "answer", answer);
    }

    @GetMapping("/{id}/files")
    public List<Map<String, String>> listFiles(@PathVariable long id) {
        return kbService.listFiles(id);
    }
}