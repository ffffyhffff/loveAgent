package com.aichat.app.tools;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * PDF 生成工具（iText 9 美化版）
 * 生成约会计划 PDF，包含封面、行程表格、POI 信息、预算汇总
 */
@Component
@Slf4j
public class PdfGenerationTool {

    // 品牌色
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(139, 92, 246);    // 紫色
    private static final DeviceRgb SECONDARY_COLOR = new DeviceRgb(236, 72, 153);  // 粉色
    private static final DeviceRgb BG_LIGHT = new DeviceRgb(250, 245, 255);        // 浅紫背景
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(30, 30, 30);
    private static final DeviceRgb TEXT_GRAY = new DeviceRgb(100, 100, 100);

    public String generate(String location, String budget, String style,
                           String occasion, String activity,
                           List<Map<String, Object>> itineraryPois,
                           String planDescription) {
        String dir = System.getProperty("user.dir") + "/tmp";
        new File(dir).mkdirs();
        String pdfPath = dir + "/date-plan-" + System.currentTimeMillis() + ".pdf";

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfPath));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            PdfFont font = loadChineseFont();
            doc.setFont(font);

            // 封面
            addCoverPage(doc, location, budget, style, occasion, activity);

            // 新页 - 行程详情
            doc.add(new AreaBreak());
            addItinerarySection(doc, planDescription);

            // POI 信息
            addPoiSection(doc, itineraryPois);

            // 预算汇总
            addBudgetSection(doc, budget);

            // 页脚
            addFooter(doc);

            log.info("PDF 生成完成: {}", pdfPath);
            return pdfPath;

        } catch (Exception e) {
            log.error("PDF 生成失败", e);
            return null;
        }
    }

    private PdfFont loadChineseFont() {
        // 尝试常见中文字体路径
        String[] fontPaths = {
                "C:/Windows/Fonts/msyh.ttc",       // 微软雅黑
                "C:/Windows/Fonts/simsun.ttc",      // 宋体
                "C:/Windows/Fonts/simhei.ttf",      // 黑体
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", // Linux 文泉驿
                "/System/Library/Fonts/PingFang.ttc" // macOS
        };

        for (String path : fontPaths) {
            try {
                if (new File(path).exists()) {
                    log.info("使用中文字体: {}", path);
                    return PdfFontFactory.createFont(path);
                }
            } catch (Exception e) {
                log.debug("字体加载失败: {}", path);
            }
        }

        // 兜底：使用内置字体（不支持中文，但不会报错）
        log.warn("未找到中文字体，PDF 中文可能显示为方块");
        try {
            return PdfFontFactory.createFont("Helvetica");
        } catch (Exception e) {
            throw new RuntimeException("无法加载任何字体", e);
        }
    }

    private void addCoverPage(Document doc, String location, String budget, String style,
                              String occasion, String activity) {
        // 标题
        Paragraph title = new Paragraph("约会计划")
                .setFontColor(PRIMARY_COLOR)
                .setFontSize(36)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(120);
        doc.add(title);

        // 副标题
        Paragraph subtitle = new Paragraph("AI 为你精心策划的浪漫行程")
                .setFontColor(TEXT_GRAY)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10);
        doc.add(subtitle);

        // 分隔线
        doc.add(new LineSeparator(new SolidLine(2))
                .setStrokeColor(PRIMARY_COLOR)
                .setMarginTop(30)
                .setMarginBottom(30));

        // 基本信息卡片
        Table infoCard = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .useAllAvailableWidth()
                .setMarginLeft(80)
                .setMarginRight(80);

        addInfoRow(infoCard, "地点", location);
        addInfoRow(infoCard, "预算", budget);
        addInfoRow(infoCard, "风格", style);
        if (occasion != null && !occasion.isEmpty()) {
            addInfoRow(infoCard, "场景", occasion);
        }
        if (activity != null && !activity.isEmpty()) {
            addInfoRow(infoCard, "活动偏好", activity);
        }
        doc.add(infoCard);

        // 日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        Paragraph datePara = new Paragraph(date)
                .setFontColor(TEXT_GRAY)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(60);
        doc.add(datePara);
    }

    private void addInfoRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label)
                        .setFontColor(TEXT_GRAY).setFontSize(11))
                .setBorder(Border.NO_BORDER)
                .setPadding(8)
                .setBackgroundColor(BG_LIGHT));
        table.addCell(new Cell().add(new Paragraph(value != null ? value : "-")
                        .setFontColor(TEXT_DARK).setFontSize(11))
                .setBorder(Border.NO_BORDER)
                .setPadding(8));
    }

    private void addItinerarySection(Document doc, String planDesc) {
        // 章节标题
        doc.add(createSectionTitle("行程安排"));

        if (planDesc != null && !planDesc.isEmpty()) {
            // 解析 Markdown 格式的计划，转为 PDF 元素
            for (String line : planDesc.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith("## ")) {
                    doc.add(new Paragraph(trimmed.substring(3))
                            .setFontSize(16).setFontColor(PRIMARY_COLOR)
                            .setMarginTop(15).setMarginBottom(8));
                } else if (trimmed.startsWith("### ")) {
                    doc.add(new Paragraph(trimmed.substring(4))
                            .setFontSize(13).setFontColor(SECONDARY_COLOR)
                            .setMarginTop(10).setMarginBottom(5));
                } else if (trimmed.startsWith("- **")) {
                    // 粗体列表项
                    String content = trimmed.replace("- **", "").replace("**", "");
                    doc.add(new Paragraph("  • " + content)
                            .setFontSize(10).setFontColor(TEXT_DARK)
                            .setMarginLeft(15).setMarginBottom(3));
                } else if (trimmed.startsWith("- ")) {
                    doc.add(new Paragraph("  • " + trimmed.substring(2))
                            .setFontSize(10).setFontColor(TEXT_DARK)
                            .setMarginLeft(15).setMarginBottom(3));
                } else if (trimmed.startsWith("|")) {
                    // 表格行 - 简单处理为文本
                    String content = trimmed.replaceAll("\\|", "  ").trim();
                    doc.add(new Paragraph(content)
                            .setFontSize(9).setFontColor(TEXT_GRAY)
                            .setMarginLeft(10).setMarginBottom(2));
                } else {
                    doc.add(new Paragraph(trimmed)
                            .setFontSize(10).setFontColor(TEXT_DARK)
                            .setMarginBottom(5));
                }
            }
        } else {
            doc.add(new Paragraph("暂无详细行程")
                    .setFontSize(10).setFontColor(TEXT_GRAY));
        }
    }

    private void addPoiSection(Document doc, List<Map<String, Object>> itineraryPois) {
        doc.add(createSectionTitle("推荐地点"));

        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 35, 45}))
                .useAllAvailableWidth();

        // 表头
        addTableHeader(table, "类型");
        addTableHeader(table, "名称");
        addTableHeader(table, "地址");

        String[] labels = {"第一站", "第二站", "第三站", "第四站", "第五站"};
        if (itineraryPois != null) {
            for (int i = 0; i < itineraryPois.size(); i++) {
                addPoiRow(table, i < labels.length ? labels[i] : "第" + (i + 1) + "站", itineraryPois.get(i));
            }
        }

        if (itineraryPois == null || itineraryPois.isEmpty()) {
            table.addCell(new Cell(1, 3).add(new Paragraph("暂无推荐地点"))
                    .setFontColor(TEXT_GRAY).setFontSize(10));
        }

        doc.add(table);
    }

    private void addTableHeader(Table table, String text) {
        table.addHeaderCell(new Cell()
                .add(new Paragraph(text).setFontColor(ColorConstants.WHITE).setFontSize(10))
                .setBackgroundColor(PRIMARY_COLOR)
                .setPadding(8));
    }

    private void addPoiRow(Table table, String type, Map<String, Object> poi) {
        table.addCell(new Cell().add(new Paragraph(type)).setFontSize(10).setPadding(6));
        table.addCell(new Cell().add(new Paragraph(poi.getOrDefault("name", "-").toString())).setFontSize(10).setPadding(6));
        table.addCell(new Cell().add(new Paragraph(poi.getOrDefault("address", "-").toString())).setFontSize(10).setPadding(6));
    }

    private void addBudgetSection(Document doc, String budget) {
        doc.add(createSectionTitle("预算汇总"));
        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .useAllAvailableWidth();

        addTableHeader(table, "项目");
        addTableHeader(table, "预算");

        table.addCell(new Cell().add(new Paragraph("约会总预算")).setFontSize(10).setPadding(6));
        table.addCell(new Cell().add(new Paragraph(budget != null ? budget : "待定")).setFontSize(10).setPadding(6));

        doc.add(table);

        // 提示
        doc.add(new Paragraph("* 实际花费可能因具体选择而有所浮动，建议预留 10-20% 弹性空间")
                .setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(10));
    }

    private Paragraph createSectionTitle(String title) {
        return new Paragraph(title)
                .setFontSize(18)
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(20)
                .setMarginBottom(12);
    }

    private void addFooter(Document doc) {
        doc.add(new LineSeparator(new SolidLine(1))
                .setStrokeColor(new DeviceRgb(200, 200, 200))
                .setMarginTop(30)
                .setMarginBottom(10));

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        doc.add(new Paragraph("Generated by AI Love Agent - " + date)
                .setFontSize(8)
                .setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }
}
