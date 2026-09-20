package dev.healthreport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ReportDeliveryServiceTest {
    public static void main(String[] args) throws Exception {
        RecordingGateway gateway = new RecordingGateway();
        ReportDeliveryService service = new ReportDeliveryService(new PdfReportGenerator(), gateway);

        AppointmentReport cancelled = report(AppointmentReport.Status.CANCELLED, true);
        ReportDeliveryService.DeliveryResult skipped = service.deliver(cancelled);
        check(skipped.decision() == ReportDeliveryService.Decision.SKIPPED_NOT_COMPLETED,
                "cancelled appointment must be skipped");
        check(gateway.calls.isEmpty(), "skip must not call email boundary");

        AppointmentReport completed = report(AppointmentReport.Status.COMPLETED, true);
        ReportDeliveryService.DeliveryResult sent = service.deliver(completed);
        check(sent.decision() == ReportDeliveryService.Decision.SENT, "completed report must be sent");
        check(sent.pdfBytes() > 100, "generated PDF must contain content");
        check(gateway.calls.size() == 1, "email boundary must be called once");
        check(gateway.calls.get(0).contains("data:application/pdf;base64,JVBERi0"),
                "email must carry a generated PDF download");
        System.out.println("PASS: cancelled=skipped, completed=PDF sent once");
    }

    private static AppointmentReport report(AppointmentReport.Status status, boolean consent) {
        return new AppointmentReport("APT-9001", "patient@example.test", LocalDate.of(2026, 9, 17),
                "Care Team", "Use the portal for follow-up.", status, consent);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGateway implements ReportDeliveryService.EmailGateway {
        private final List<String> calls = new ArrayList<>();

        @Override
        public String send(String to, String subject, String html, String idempotencyKey) {
            calls.add(to + "|" + subject + "|" + html + "|" + idempotencyKey);
            return "msg_test_9001";
        }
    }
}
