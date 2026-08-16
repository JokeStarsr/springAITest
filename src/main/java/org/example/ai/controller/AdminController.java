package org.example.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理控制器 - 支持通过 HTTP 触发部署 + 定时自动拉取部署
 * 部署流程在后台脚本中执行，避免 systemctl restart 杀死当前进程
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final String PROJECT_DIR = "/opt/springaitest";
    private static final String DEPLOY_SCRIPT = PROJECT_DIR + "/deploy.sh";
    private static final String SERVICE_NAME = "springaitest";

    private final AtomicBoolean deploying = new AtomicBoolean(false);

    @Value("${app.auto-deploy:false}")
    private boolean autoDeploy;

    /**
     * 手动触发部署 - 启动后台脚本，立即返回
     */
    @PostMapping("/deploy")
    public Map<String, Object> deploy() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!deploying.compareAndSet(false, true)) {
            result.put("status", "already_running");
            result.put("message", "部署正在进行中，请稍后查看日志");
            return result;
        }

        try {
            log.info("收到部署请求，启动后台部署脚本...");
            ProcessBuilder pb = new ProcessBuilder("nohup", "bash", DEPLOY_SCRIPT);
            pb.redirectOutput(new File("/tmp/deploy.log"));
            pb.redirectErrorStream(true);
            pb.directory(new File(PROJECT_DIR));
            Process process = pb.start();
            process.getInputStream().close();

            result.put("status", "started");
            result.put("message", "部署已启动，查看日志: /tmp/deploy.log");
            result.put("pid", process.pid());
            log.info("部署脚本已启动, PID: {}", process.pid());

            // 锁保持到部署进程结束，期间拒绝新的部署请求
            process.onExit().whenComplete((p, ex) -> deploying.set(false));

        } catch (Exception e) {
            log.error("启动部署脚本失败", e);
            deploying.set(false);
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 定时检查 GitHub 是否有新提交，有则自动部署
     * 每5分钟执行一次（可通过 app.auto-deploy 配置开关）
     */
    @Scheduled(fixedRate = 300000)
    public void autoDeployCheck() {
        if (!autoDeploy) return;

        try {
            // git fetch 获取远程最新状态
            ProcessBuilder pb = new ProcessBuilder("git", "fetch", "origin");
            pb.directory(new File(PROJECT_DIR));
            pb.redirectErrorStream(true);
            Process fetch = pb.start();
            fetch.waitFor(30, TimeUnit.SECONDS);

            // 比较本地和远程 HEAD
            String localHead = runQuick("git", "rev-parse", "HEAD");
            String remoteHead = runQuick("git", "rev-parse", "origin/main");

            if (!localHead.equals(remoteHead)) {
                log.info("检测到新提交: {} -> {}", localHead.substring(0, 7), remoteHead.substring(0, 7));
                deploy();
            }
        } catch (Exception e) {
            log.debug("自动部署检查失败: {}", e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("timestamp", new Date().toString());
        info.put("autoDeploy", autoDeploy);
        info.put("deploying", deploying.get());
        return info;
    }

    private String runQuick(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(PROJECT_DIR));
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        p.waitFor(10, TimeUnit.SECONDS);
        return sb.toString().trim();
    }
}