package dev.healthreport;

import java.time.LocalDate;

public final class ReportServiceApplication {
    private ReportServiceApplication() {}

    public static void main(String[] args) throws Exception {
        String recipient = System.getenv("REPORT_RECIPIENT_EMAIL");
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("REPORT_RECIPIENT_EMAIL is required");
        }

        InfraiConfig config = InfraiConfig.fromEnvironment();
        ReportDeliveryService service = new ReportDeliveryService(
                new PdfReportGenerator(), new InfraiEmailClient(config));
        AppointmentReport report = new AppointmentReport(
                "APT-DEMO-2026-09",
                recipient,
                LocalDate.now(),
                "Care Team",
                "Review the care plan in the patient portal.",
                AppointmentReport.Status.COMPLETED,
                true);

        ReportDeliveryService.DeliveryResult result = service.deliver(report);
        System.out.printf("decision=%s message_id=%s pdf_bytes=%d%n",
                result.decision(), result.messageId(), result.pdfBytes());
    }
}
