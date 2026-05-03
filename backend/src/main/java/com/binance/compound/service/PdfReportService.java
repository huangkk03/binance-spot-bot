package com.binance.compound.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PdfReportService {

    public byte[] generatePdfReport(String reportContent) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, baos);
            document.open();

            // Try to use a built-in font or standard font for Chinese characters
            // STSong-Light is a standard CJK font available in iTextAsian, but we are using OpenPDF.
            // OpenPDF supports STSong-Light with UniGB-UTF16-H encoding if itext-asian is present.
            // Since we didn't explicitly add itext-asian, we might need a fallback or use Helvetica if English.
            // For robust Chinese support in OpenPDF without external fonts, we can try STSong-Light.
            Font titleFont;
            Font bodyFont;
            try {
                BaseFont bfChinese = BaseFont.createFont("/fonts/simhei.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                titleFont = new Font(bfChinese, 18, Font.BOLD);
                bodyFont = new Font(bfChinese, 12, Font.NORMAL);
            } catch (Exception e) {
                log.warn("Chinese font not found, falling back to default font. Chinese characters may not render correctly.", e);
                titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
                bodyFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            } catch (Error e) {
                log.warn("Error loading Chinese font, falling back to default font. Chinese characters may not render correctly.", e);
                titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
                bodyFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            }

            // Title
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            Paragraph title = new Paragraph("BTC AI Analysis Report", titleFont);
            title.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(title);
            
            Paragraph datePara = new Paragraph("Generated at: " + dateStr, bodyFont);
            datePara.setAlignment(Paragraph.ALIGN_CENTER);
            datePara.setSpacingAfter(20f);
            document.add(datePara);

            // Content
            Paragraph content = new Paragraph(reportContent, bodyFont);
            document.add(content);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report", e);
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
}