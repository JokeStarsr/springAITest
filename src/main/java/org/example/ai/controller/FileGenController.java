package org.example.ai.controller;

import org.example.ai.service.FileGenService;
import org.example.ai.service.UsageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;

/**
 * 文件生成控制器 - 通过 AI 生成 PPT/Word/Excel 文件并提供下载
 */
@RestController
@RequestMapping("/api/files")
public class FileGenController {

    private static final Logger log = LoggerFactory.getLogger(FileGenController.class);
    private final FileGenService fileGenService;
    private final UsageTracker usageTracker;

    // MIME 类型映射
    private static final Map<String, String> MIME_TYPES = Map.of(
        "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    // 文件扩展名中文名映射
    private static final Map<String, String> TYPE_NAMES = Map.of(
        "pptx", "演示文稿",
        "docx", "Word文档",
        "xlsx", "Excel表格"
    );

    public FileGenController(FileGenService fileGenService, UsageTracker usageTracker) {
        this.fileGenService = fileGenService;
        this.usageTracker = usageTracker;
    }

    /**
     * 生成文件并返回下载
     * POST /api/files/generate
     * Body: { "type": "pptx|docx|xlsx", "topic": "主题描述" }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody GenerateRequest request) {
        String type = request.getType();
        String topic = request.getTopic();

        if (type == null || type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请指定文件类型 (pptx/docx/xlsx)"));
        }
        type = type.toLowerCase().trim();
        if (!MIME_TYPES.containsKey(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不支持的文件类型: " + type + "，支持: pptx, docx, xlsx"));
        }
        if (topic == null || topic.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入主题/内容描述"));
        }

        log.info("开始生成 {} 文件, 主题: {}", type, topic);
        long startTime = System.currentTimeMillis();

        try {
            Path filePath = fileGenService.generateFile(type, topic);
            long duration = System.currentTimeMillis() - startTime;

            // 记录用量
            usageTracker.record("files/generate/" + type, topic, filePath.getFileName().toString(), duration);

            Resource resource = new FileSystemResource(filePath);
            String filename = filePath.getFileName().toString();
            String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(MIME_TYPES.get(type)))
                .body(resource);

        } catch (Exception e) {
            log.error("文件生成失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "文件生成失败: " + e.getMessage());
            error.put("type", type);
            error.put("topic", topic);
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 获取支持的文件类型列表
     */
    @GetMapping("/types")
    public List<Map<String, String>> getSupportedTypes() {
        List<Map<String, String>> types = new ArrayList<>();
        for (var entry : TYPE_NAMES.entrySet()) {
            types.add(Map.of(
                "type", entry.getKey(),
                "name", entry.getValue(),
                "icon", getIcon(entry.getKey())
            ));
        }
        return types;
    }

    private String getIcon(String type) {
        return switch (type) {
            case "pptx" -> "📊";
            case "docx" -> "📝";
            case "xlsx" -> "📈";
            default -> "📄";
        };
    }

    public static class GenerateRequest {
        private String type;
        private String topic;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }
}