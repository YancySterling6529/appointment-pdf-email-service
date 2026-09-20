package dev.healthreport;

import java.time.LocalDate;
import java.util.Objects;

public record AppointmentReport(
        String appointmentReference,
        String recipientEmail,
        LocalDate completedOn,
        String clinicianDisplayName,
        String followUpSummary,
        Status status,
        boolean emailConsent) {

    public AppointmentReport {
        Objects.requireNonNull(appointmentReference, "appointmentReference");
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        Objects.requireNonNull(completedOn, "completedOn");
        Objects.requireNonNull(clinicianDisplayName, "clinicianDisplayName");
        Objects.requireNonNull(followUpSummary, "followUpSummary");
        Objects.requireNonNull(status, "status");
    }

    public enum Status { SCHEDULED, COMPLETED, CANCELLED }
}
