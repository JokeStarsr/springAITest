package org.example.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 管理控制器 - 支持通过 HTTP 触发 git pull + 编译 + 重启
 * 使用方式: POST /api/admin/deploy
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final String PROJECT_DIR = "/opt/springaitest";
    private static final String SERVICE_NAME = "springaitest";

    /**
     * 触发部署：git pull -> mvn package -> systemctl restart
     */
    @PostMapping("/deploy")
    public Map<String, Object> deploy() {
        log.info("收到部署请求");
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> steps = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: git pull
            steps.add("git pull: " + runCommand(PROJECT_DIR, "git", "pull"));
            
            // Step 2: mvn clean package
            steps.add("mvn package: " + runCommand(PROJECT_DIR, "mvn", "clean", "package", "-DskipTests", "-q"));
            
            // Step 3: 确保数据目录存在
            runCommand(PROJECT_DIR, "mkdir", "-p", "/opt/springaitest/data/generated-files");
            
            // Step 4: restart service
            steps.add("systemctl restart: " + runCommand(PROJECT_DIR, "systemctl", "restart", SERVICE_NAME));
            
            // Step 5: check status
            steps.add("service status: " + runCommand(PROJECT_DIR, "systemctl", "is-active", SERVICE_NAME));

            result.put("success", true);
            result.put("duration", (System.currentTimeMillis() - startTime) + "ms");
            result.put("steps", steps);
            log.info("部署完成, 耗时: {}ms", System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("部署失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("steps", steps);
        }

        return result;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("timestamp", new Date().toString());
        return info;
    }

    private String runCommand(String dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new java.io.File(dir));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "TIMEOUT (120s)";
        }
        
        String out = output.toString().trim();
        if (process.exitValue() != 0) {
            throw new RuntimeException("Exit code " + process.exitValue() + ": " + out);
        }
        return out.isEmpty() ? "OK" : out.substring(0, Math.min(out.length(), 500));
    }
}