package org.example.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

/**
 * 文件生成服务 -使用 AI 生成内容，Apache POI 创建 PPT/Word/Excel 文件
 * 输出目录可配置（app.generated-files-dir），生成文件按保留期自动清理。
 */
@Service
public class FileGenService {

    private static final Logger log = LoggerFactory.getLogger(FileGenService.class);
    private final ChatClient chatClient;
    private final UsageTracker usageTracker;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path outputDir;

    @Value("${app.gen-files-retention-days:7}")
    private int retentionDays;

    public FileGenService(ChatClient chatClient, UsageTracker usageTracker,
                          @Value("${app.generated-files-dir:/opt/springaitest/data/generated-files}") String outputDir) {
        this.chatClient = chatClient;
        this.usageTracker = usageTracker;
        this.outputDir = Path.of(outputDir);
        try {
            Files.createDirectories(this.outputDir);
        } catch (Exception e) {
            log.warn("无法创建文件输出目录: {}", this.outputDir, e);
        }
    }

    /** 每日凌晨清理超过保留期的生成文件（防磁盘增长） */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupExpiredFiles() {
        if (!Files.exists(outputDir)) return;
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600 * 1000;
        try (var files = Files.list(outputDir)) {
            files.filter(Files::isRegularFile)
                 .forEach(p -> {
                     try {
                         if (Files.getLastModifiedTime(p).toMillis() < cutoff) {
                             Files.deleteIfExists(p);
                             log.info("已清理过期生成文件: {}", p.getFileName());
                         }
                     } catch (IOException ignored) {}
                 });
        } catch (IOException e) {
            log.warn("生成文件清理失败", e);
        }
    }

    /**
     * 生成文件，返回文件路径
     */
    public Path generateFile(String type, String topic) throws Exception {
        return switch (type.toLowerCase()) {
            case "pptx" -> generatePptx(topic);
            case "docx" -> generateDocx(topic);
            case "xlsx" -> generateXlsx(topic);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + type + "，支持: pptx, docx, xlsx");
        };
    }

    // ===== PPTX =====

    private Path generatePptx(String topic) throws Exception {
        String prompt = buildPptPrompt(topic);
        String aiResponse = callAi("files.generate.pptx", prompt);
        log.debug("AI PPT 响应: {}", aiResponse);

        Map<String, Object> data = parseJson(aiResponse);
        String title = (String) data.getOrDefault("title", topic);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> slides = (List<Map<String, String>>) data.get("slides");

        XMLSlideShow ppt = new XMLSlideShow();

        // 设置幻灯片大小 16:9
        ppt.setPageSize(new java.awt.Dimension(960, 540));

        // 封面页
        XSLFSlide coverSlide = ppt.createSlide();
        createCoverSlide(coverSlide, title, "AI 自动生成");

        // 内容页
        if (slides != null) {
            for (Map<String, String> slide : slides) {
                XSLFSlide contentSlide = ppt.createSlide();
                createContentSlide(contentSlide, slide.get("title"), slide.get("points"));
            }
        }

        // 如果AI没有返回有效slides，创建默认页
        if (slides == null || slides.isEmpty()) {
            XSLFSlide fallback = ppt.createSlide();
            createContentSlide(fallback, topic, aiResponse);
        }

        // 结束页
        XSLFSlide endSlide = ppt.createSlide();
        createEndSlide(endSlide, "感谢阅读");

        String filename = sanitize(topic) + "_" + System.currentTimeMillis() + ".pptx";
        Path filePath = outputDir.resolve(filename);
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            ppt.write(fos);
        }
        ppt.close();
        log.info("PPTX 已生成: {}", filePath);
        return filePath;
    }

    private void createCoverSlide(XSLFSlide slide, String title, String subtitle) {
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new java.awt.Rectangle(80, 160, 800, 120));
        XSLFTextParagraph tp = titleBox.addNewTextParagraph();
        tp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(title);
        tr.setFontSize(36.0);
        tr.setFontColor(new java.awt.Color(30, 30, 60));
        tr.setBold(true);

        XSLFTextBox subBox = slide.createTextBox();
        subBox.setAnchor(new java.awt.Rectangle(80, 300, 800, 60));
        XSLFTextParagraph sp = subBox.addNewTextParagraph();
        sp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        XSLFTextRun sr = sp.addNewTextRun();
        sr.setText(subtitle);
        sr.setFontSize(18.0);
        sr.setFontColor(new java.awt.Color(120, 120, 150));

        // 背景色
        slide.getBackground().setFillColor(new java.awt.Color(240, 245, 255));
    }

    private void createContentSlide(XSLFSlide slide, String title, String points) {
        // 标题
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new java.awt.Rectangle(60, 40, 840, 60));
        XSLFTextParagraph tp = titleBox.addNewTextParagraph();
        tp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT);
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(title != null ? title : "");
        tr.setFontSize(28.0);
        tr.setFontColor(new java.awt.Color(30, 30, 60));
        tr.setBold(true);

        // 分隔线
        XSLFAutoShape line = slide.createAutoShape();
        line.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
        line.setAnchor(new java.awt.Rectangle(60, 105, 840, 3));
        line.setFillColor(new java.awt.Color(100, 100, 200));

        // 内容
        XSLFTextBox contentBox = slide.createTextBox();
        contentBox.setAnchor(new java.awt.Rectangle(80, 130, 800, 370));
        if (points != null) {
            for (String point : points.split("\n")) {
                String trimmed = point.replaceAll("^[\\s•\\-\\*\\d+\\.]+", "").trim();
                if (trimmed.isEmpty()) continue;
                XSLFTextParagraph pp = contentBox.addNewTextParagraph();
                pp.setBullet(true);
                pp.setLeftMargin(20.0);
                XSLFTextRun pr = pp.addNewTextRun();
                pr.setText(trimmed);
                pr.setFontSize(20.0);
                pr.setFontColor(new java.awt.Color(60, 60, 80));
            }
        }
    }

    private void createEndSlide(XSLFSlide slide, String text) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new java.awt.Rectangle(80, 200, 800, 140));
        XSLFTextParagraph tp = box.addNewTextParagraph();
        tp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(text);
        tr.setFontSize(40.0);
        tr.setFontColor(new java.awt.Color(100, 100, 200));
        tr.setBold(true);
        slide.getBackground().setFillColor(new java.awt.Color(245, 248, 255));
    }

    // ===== DOCX =====

    private Path generateDocx(String topic) throws Exception {
        String prompt = buildDocxPrompt(topic);
        String aiResponse = callAi("files.generate.docx", prompt);
        log.debug("AI DOCX 响应: {}", aiResponse);

        Map<String, Object> data = parseJson(aiResponse);
        String title = (String) data.getOrDefault("title", topic);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> sections = (List<Map<String, String>>) data.get("sections");

        XWPFDocument doc = new XWPFDocument();

        // 标题
        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(title);
        titleRun.setBold(true);
        titleRun.setFontSize(24);
        titleRun.setFontFamily("微软雅黑");

        // 分隔线
        XWPFParagraph hr = doc.createParagraph();
        XWPFRun hrRun = hr.createRun();
        hrRun.setText("─".repeat(50));
        hrRun.setColor("999999");
        hrRun.setFontSize(10);

        if (sections != null) {
            for (Map<String, String> sec : sections) {
                // 章节标题
                XWPFParagraph heading = doc.createParagraph();
                heading.setSpacingBefore(300);
                XWPFRun hRun = heading.createRun();
                hRun.setText(sec.get("heading"));
                hRun.setBold(true);
                hRun.setFontSize(16);
                hRun.setFontFamily("微软雅黑");
                hRun.setColor("1a1a3c");

                // 正文
                String content = sec.get("content");
                if (content != null) {
                    for (String para : content.split("\n")) {
                        String trimmed = para.trim();
                        if (trimmed.isEmpty()) continue;
                        XWPFParagraph p = doc.createParagraph();
                        p.setSpacingAfter(120);
                        XWPFRun run = p.createRun();
                        run.setText(trimmed);
                        run.setFontSize(12);
                        run.setFontFamily("微软雅黑");
                    }
                }
            }
        }

        // 如果没有sections，直接输出AI内容
        if (sections == null || sections.isEmpty()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText(aiResponse);
            run.setFontSize(12);
        }

        // 页脚
        XWPFParagraph footer = doc.createParagraph();
        footer.setAlignment(ParagraphAlignment.CENTER);
        footer.setSpacingBefore(400);
        XWPFRun fRun = footer.createRun();
        fRun.setText("— AI 自动生成 —");
        fRun.setFontSize(10);
        fRun.setColor("999999");
        fRun.setItalic(true);

        String filename = sanitize(topic) + "_" + System.currentTimeMillis() + ".docx";
        Path filePath = outputDir.resolve(filename);
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            doc.write(fos);
        }
        doc.close();
        log.info("DOCX 已生成: {}", filePath);
        return filePath;
    }

    // ===== XLSX =====

    private Path generateXlsx(String topic) throws Exception {
        String prompt = buildXlsxPrompt(topic);
        String aiResponse = callAi("files.generate.xlsx", prompt);
        log.debug("AI XLSX 响应: {}", aiResponse);

        Map<String, Object> data = parseJson(aiResponse);
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) data.get("headers");
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) data.get("rows");
        String sheetName = (String) data.getOrDefault("title", topic);

        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        SXSSFSheet sheet = wb.createSheet(sheetName);

        // 样式
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        CellStyle dataStyle = wb.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);

        // 写表头
        if (headers != null && !headers.isEmpty()) {
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
        }

        // 写数据
        if (rows != null) {
            int startRow = (headers != null && !headers.isEmpty()) ? 1 : 0;
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(startRow + r);
                List<Object> rowData = rows.get(r);
                for (int c = 0; c < rowData.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object val = rowData.get(c);
                    if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                    } else {
                        String strVal = String.valueOf(val);
                        try {
                            cell.setCellValue(Double.parseDouble(strVal));
                        } catch (NumberFormatException e) {
                            cell.setCellValue(strVal);
                        }
                    }
                    cell.setCellStyle(dataStyle);
                }
            }
        }

        // 如果没有数据，创建示例数据
        if ((headers == null || headers.isEmpty()) && (rows == null || rows.isEmpty())) {
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("内容");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(aiResponse);
        }

        // 自动列宽
        if (headers != null) {
            for (int i = 0; i < headers.size(); i++) {
                sheet.trackColumnForAutoSizing(i);
                sheet.autoSizeColumn(i);
            }
        }

        String filename = sanitize(topic) + "_" + System.currentTimeMillis() + ".xlsx";
        Path filePath = outputDir.resolve(filename);
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            wb.write(fos);
        }
        wb.close();
        wb.dispose();
        log.info("XLSX 已生成: {}", filePath);
        return filePath;
    }

    private String callAi(String endpoint, String prompt) {
        long start = System.currentTimeMillis();
        ChatResponse response = chatClient.prompt().user(prompt).call().chatResponse();
        String result = response.getResult() != null ? response.getResult().getOutput().getText() : "";
        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        long in = -1, out = -1;
        if (usage != null) {
            in = usage.getPromptTokens() != null ? usage.getPromptTokens() : -1;
            out = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : -1;
        }
        usageTracker.recordWithUsage(endpoint, prompt, result, System.currentTimeMillis() - start, in, out);
        return result;
    }

    // ===== AI 提示词 =====

    private String buildPptPrompt(String topic) {
        return """
            你是一个专业的演示文稿内容生成器。请根据以下主题生成 PPT 内容。
            严格按 JSON 格式输出，不要输出任何其他文字：

            {
              "title": "演示文稿主标题",
              "slides": [
                {"title": "第1页标题", "points": "要点1\\n要点2\\n要点3"},
                {"title": "第2页标题", "points": "要点1\\n要点2\\n要点3"}
              ]
            }

            要求：
            - 生成 4-8 页内容页
            - 每页 3-5 个要点
            - 要点简洁有力，适合演示

            主题：""" + topic;
    }

    private String buildDocxPrompt(String topic) {
        return """
            你是一个专业的文档内容生成器。请根据以下主题生成 Word 文档内容。
            严格按 JSON 格式输出，不要输出任何其他文字：

            {
              "title": "文档标题",
              "sections": [
                {"heading": "章节1标题", "content": "段落1\\n段落2\\n段落3"},
                {"heading": "章节2标题", "content": "段落1\\n段落2"}
              ]
            }

            要求：
            - 生成 3-6 个章节
            - 每个章节 2-4 个段落
            - 内容专业、结构清晰

            主题：""" + topic;
    }

    private String buildXlsxPrompt(String topic) {
        return """
            你是一个专业的表格数据生成器。请根据以下主题生成 Excel 表格内容。
            严格按 JSON 格式输出，不要输出任何其他文字：

            {
              "title": "表格标题",
              "headers": ["列1", "列2", "列3", "列4"],
              "rows": [
                ["数据1", "数据2", "数据3", "数据4"],
                ["数据1", "数据2", "数据3", "数据4"]
              ]
            }

            要求：
            - 生成 4-8 列
            - 生成 8-20 行数据
            - 数据合理、有参考价值

            主题：""" + topic;
    }

    // ===== 工具方法 =====

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String aiResponse) {
        try {
            // 提取 JSON 部分（AI 可能包裹在 ```json ... ``` 中）
            String json = aiResponse;
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                if (json.contains("```")) {
                    json = json.substring(0, json.indexOf("```"));
                }
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                if (json.contains("```")) {
                    json = json.substring(0, json.indexOf("```"));
                }
            }
            json = json.trim();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON 解析失败，使用原始响应: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", "");
            fallback.put("slides", List.of());
            fallback.put("sections", List.of());
            fallback.put("raw", aiResponse);
            return fallback;
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").substring(0, Math.min(50, name.length()));
    }
}
