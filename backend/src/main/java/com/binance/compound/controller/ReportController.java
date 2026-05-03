package com.binance.compound.controller;

import com.binance.compound.service.AiPredictionService;
import com.binance.compound.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final AiPredictionService aiPredictionService;
    private final PdfReportService pdfReportService;

    @GetMapping("/btc-prediction/pdf")
    public ResponseEntity<byte[]> downloadBtcPredictionPdf() {
        log.info("Generating BTC AI prediction report...");
        String reportContent = aiPredictionService.generateBtcPredictionReport();
        byte[] pdfBytes = pdfReportService.generatePdfReport(reportContent);

        String filename = "BTC_AI_Report_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
    
    @GetMapping("/btc-prediction/text")
    public ResponseEntity<Map<String, String>> getBtcPredictionText() {
        log.info("Generating BTC AI prediction text...");
        String reportContent = aiPredictionService.generateBtcPredictionReport();
        return ResponseEntity.ok(Map.of("content", reportContent));
    }
}