package dev.healthreport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient implements ReportDeliveryService.EmailGateway {
    private static final String EMAIL_SEND_PATH = "/v1/email/send";
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"(?:message|hint)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final InfraiConfig config;
    private final HttpClient http;
    private final Sleeper sleeper;

    public InfraiEmailClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build(), Thread::sleep);
    }

    InfraiEmailClient(InfraiConfig config, HttpClient http, Sleeper sleeper) {
        this.config = config;
        this.http = http;
        this.sleeper = sleeper;
    }

    // Canonical boundary: infrai.email.send -> POST /v1/email/send.
    @Override
    public String send(String to, String subject, String html, String idempotencyKey) throws IOException, InterruptedException {
        String payload = "{\"to\":\"" + json(to) + "\",\"subject\":\"" + json(subject)
                + "\",\"html\":\"" + json(html) + "\"}";
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(resolve(EMAIL_SEND_PATH))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = decode(response.body(), response.statusCode());
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                sleeper.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Email transport failed with HTTP " + response.statusCode());
            }
            if (envelope.messageId() == null || envelope.messageId().isBlank()) {
                throw new IOException("Email response did not contain message_id");
            }
            return envelope.messageId();
        }
        throw new IOException("Email request exhausted retry attempts");
    }

    private URI resolve(String path) {
        return config.baseUri().resolve(path);
    }

    private static Envelope decode(String body, int status) throws IOException {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) throw new IOException("Invalid Infrai envelope at HTTP " + status);
        boolean success = Boolean.parseBoolean(ok.group(1));
        return new Envelope(success, find(MESSAGE_ID, body), find(ERROR_CODE, body), find(ERROR_MESSAGE, body));
    }

    private static String find(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiEmailClient::parseRetryAfter)
                .orElse(Math.min(8_000L, 250L * (1L << (attempt - 1))));
    }

    private static long parseRetryAfter(String value) {
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1_000L);
        } catch (NumberFormatException ignored) {
            return 1_000L;
        }
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c < 0x20 ? String.format("\\u%04x", (int) c) : c);
            }
        }
        return escaped.toString();
    }

    private record Envelope(boolean ok, String messageId, String errorCode, String errorMessage) {}

    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    public static final class InfraiException extends IOException {
        private final String code;
        private final int statusCode;

        InfraiException(String code, String message, int statusCode) {
            super(message == null ? "Email request rejected" : message);
            this.code = code == null ? "UNKNOWN" : code;
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public int statusCode() { return statusCode; }
    }
}
