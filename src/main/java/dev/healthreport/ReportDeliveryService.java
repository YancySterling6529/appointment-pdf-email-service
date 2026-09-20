package dev.healthreport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class ReportDeliveryService {
    private final PdfReportGenerator pdfGenerator;
    private final EmailGateway emailGateway;

    public ReportDeliveryService(PdfReportGenerator pdfGenerator, EmailGateway emailGateway) {
        this.pdfGenerator = pdfGenerator;
        this.emailGateway = emailGateway;
    }

    public DeliveryResult deliver(AppointmentReport report) throws IOException, InterruptedException {
        if (report.status() != AppointmentReport.Status.COMPLETED) {
            return new DeliveryResult(Decision.SKIPPED_NOT_COMPLETED, null, 0);
        }
        if (!report.emailConsent()) {
            return new DeliveryResult(Decision.SKIPPED_NO_CONSENT, null, 0);
        }

        byte[] pdf = pdfGenerator.generate(report);
        String encodedPdf = Base64.getEncoder().encodeToString(pdf);
        String html = "<p>Your appointment report is ready.</p>"
                + "<p><a download=\"appointment-report.pdf\" href=\"data:application/pdf;base64,"
                + encodedPdf + "\">Download PDF report</a></p>"
                + "<p>Reference: " + html(report.appointmentReference()) + "</p>";
        String key = "appointment-report-" + report.appointmentReference() + "-" + sha256(pdf).substring(0, 16);
        String messageId = emailGateway.send(
                report.recipientEmail(),
                "Your completed appointment report",
                html,
                key);
        return new DeliveryResult(Decision.SENT, messageId, pdf.length);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public enum Decision { SENT, SKIPPED_NOT_COMPLETED, SKIPPED_NO_CONSENT }
    public record DeliveryResult(Decision decision, String messageId, int pdfBytes) {}

    @FunctionalInterface
    public interface EmailGateway {
        String send(String to, String subject, String html, String idempotencyKey)
                throws IOException, InterruptedException;
    }
}
