package dev.learning.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MediaErrorClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public MediaErrorClient(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                            @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl;
        this.key = key;
    }

    public void capture(String jobId, String title, String message, String exception,
                        Map<String, String> context) {
        try {
            byte[] payload = json.writeValueAsBytes(Map.of(
                "title", title, "message", message, "level", "error",
                "fingerprint", new String[]{"course-media-agent", context.get("stage")},
                "exception", exception, "context", context));
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/errors/capture"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", jobId + ":" + context.get("stage"))
                    .method("POST", HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                        .flatMap(value -> {
                            try { return java.util.Optional.of(Long.parseLong(value)); }
                            catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
                        }).orElse(1L << attempt);
                    Thread.sleep(Math.min(seconds, 30) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new CaptureRejected(response.statusCode(), error.path("code").asText(),
                        error.toString());
                }
                if (response.statusCode() >= 500) {
                    throw new IllegalStateException("Capture transport status: " + response.statusCode());
                }
                return;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Capture transport error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Capture interrupted", e);
        }
    }

    public static class CaptureRejected extends RuntimeException {
        private final int status;
        public CaptureRejected(int status, String code, String detail) {
            super(code + ": " + detail);
            this.status = status;
        }
        public int status() { return status; }
    }
}
