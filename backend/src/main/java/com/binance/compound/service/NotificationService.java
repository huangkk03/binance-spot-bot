package com.binance.compound.service;

import com.binance.compound.repository.ApiConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ApiConfigRepository apiConfigRepository;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public void notifyTradeEvent(String eventType, String symbol, String details, boolean isSimulation) {
        String mode = isSimulation ? "【模拟交易】" : "【真实交易】";
        String title = mode + " " + eventType + " - " + symbol;
        String content = title + "\n\n" + details;

        sendWeChatNotification(title, content);
        sendEmailNotification(title, content);
    }

    public void sendWeChatNotification(String title, String content) {
        String webhookUrl = getConfigValue("WECHAT_WEBHOOK_URL");
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        try {
            // Support Server酱 (ServerChan) format or simple GET/POST
            // ServerChan format: https://sctapi.ftqq.com/{SendKey}.send?title={title}&desp={content}
            String url = webhookUrl;
            HttpRequest request;
            
            if (url.contains("sctapi.ftqq.com")) {
                // ServerChan
                String encodedTitle = java.net.URLEncoder.encode(title, "UTF-8");
                String encodedContent = java.net.URLEncoder.encode(content, "UTF-8");
                String fullUrl = url + (url.contains("?") ? "&" : "?") + "title=" + encodedTitle + "&desp=" + encodedContent;
                request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .GET()
                        .build();
            } else {
                // Generic POST JSON (e.g., Enterprise WeChat or DingTalk)
                String jsonBody = String.format("{\"msgtype\":\"text\",\"text\":{\"content\":\"%s\\n%s\"}}", title, content.replace("\"", "\\\"").replace("\n", "\\n"));
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Failed to send WeChat notification. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Exception sending WeChat notification", e);
        }
    }

    public void sendEmailNotification(String title, String content) {
        String to = getConfigValue("EMAIL_TO");
        String host = getConfigValue("EMAIL_SMTP_HOST");
        String portStr = getConfigValue("EMAIL_SMTP_PORT");
        String username = getConfigValue("EMAIL_SMTP_USERNAME");
        String password = getConfigValue("EMAIL_SMTP_PASSWORD");

        if (to == null || to.trim().isEmpty() || host == null || host.trim().isEmpty() || username == null || username.trim().isEmpty()) {
            return;
        }

        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setPort(portStr != null && !portStr.isEmpty() ? Integer.parseInt(portStr) : 465);
            mailSender.setUsername(username);
            mailSender.setPassword(password);

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.debug", "false");
            
            if (mailSender.getPort() == 465) {
                props.put("mail.smtp.ssl.enable", "true");
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(to);
            helper.setSubject(title);
            helper.setText(content.replace("\n", "<br/>"), true); // Send as HTML for better formatting

            mailSender.send(message);
        } catch (Exception e) {
            log.error("Exception sending Email notification", e);
        }
    }

    private String getConfigValue(String key) {
        return apiConfigRepository.findByConfigKey(key)
                .map(c -> c.getConfigValue())
                .orElse(null);
    }
}
