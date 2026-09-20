package dev.healthreport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PdfReportGenerator {
    public byte[] generate(AppointmentReport report) {
        List<String> lines = List.of(
                "Appointment report",
                "Reference: " + report.appointmentReference(),
                "Completed: " + report.completedOn(),
                "Clinician: " + report.clinicianDisplayName(),
                "Follow-up: " + report.followUpSummary());

        StringBuilder stream = new StringBuilder("BT /F1 12 Tf 54 750 Td 16 TL ");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) stream.append("T* ");
            stream.append('(').append(pdfEscape(lines.get(i))).append(") Tj ");
        }
        stream.append("ET");

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + stream.toString().getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + stream + "\nendstream");
        return assemble(objects);
    }

    private static byte[] assemble(List<String> objects) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) write(out, String.format("%010d 00000 n \n", offset));
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
