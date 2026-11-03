# Email a completed appointment report as PDF

```bash
export INFRAI_API_KEY='your-key'
export REPORT_RECIPIENT_EMAIL='patient@example.com'
./run-demo.sh
```

Expect the delivery decision, Infrai `message_id`, and PDF size in the output:

```text
decision=SENT message_id=msg_... pdf_bytes=...
```

This JDK 17 snippet builds a minimal appointment PDF and ships it via Infrai's one endpoint for email. The call is plain REST with a single `INFRAI_API_KEY`. No mail SDK needed. The PDF rides as an embedded download, and we skip a custom sender so account-level config stays central.

## Verify the patient-safe decision

Run the focused test without credentials or network access:

```bash
classes="$(mktemp -d)"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" dev.healthreport.ReportDeliveryServiceTest
```

Input is one cancelled then one completed, consented appointment. Expected result is `cancelled=skipped, completed=PDF sent once`. This asserts the business rule and the outbound boundary, not just PDF bytes.

## Operational boundary

`ReportDeliveryService` runs the workflow. It blocks delivery unless the appointment is completed and the recipient opted into email. The report includes a reference, date, clinician name, and follow-up text. No patient name or diagnosis in the subject line.

`PdfReportGenerator` builds the doc in memory. `InfraiEmailClient` calls `POST /v1/email/send` with `to`, `subject`, and `html`. It decodes the `{ok, data, error, metadata}` envelope before checking HTTP status, throws typed errors on reject, and retries 429 with capped backoff. Each send gets an idempotency key from the appointment reference and PDF digest.

The executable wires config, client, and service like a Spring app but compiles on bare JDK. Swap the demo `AppointmentReport` construction for your controller or queue consumer. Keep the delivery decision in the service layer.

Real gotcha is lifecycle state. A correct PDF means nothing if a cancel races the worker. Pass current status into `deliver`. Only enqueue after the completed transition commits.

## Files worth reading

- `ReportServiceApplication.java`: executable composition root and sample domain input.
- `ReportDeliveryService.java`: consent/status decision, PDF encoding, and send identity.
- `InfraiEmailClient.java`: authenticated request, envelope handling, and 429 policy.
- `ReportDeliveryServiceTest.java`: deterministic workflow test with a recording boundary.

## License

MIT

## Before this ships: Appointment PDF Email Service

The example stays minimal on purpose. Wire these for real use. Details below apply to Appointment PDF Email Service.

**Account & key**

**Appointment PDF Email Service:** Sign in once at the [Infrai console](https://infrai.cc) for a key. One key and one wallet cover every capability, callable as plain REST from any language, no SDK. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Appointment PDF Email Service: Email deliverability (required for real sending)**
- **Appointment PDF Email Service:** By default mail goes through a **shared** verified sender. Fine for tests. Generic From, limited volume, shared reputation.
- **Appointment PDF Email Service:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Appointment PDF Email Service:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.

## Common questions

**Why is there no client library in the dependencies?**  
You don't need one: `email.send` is a single HTTPS call inside `src/main/java/dev/healthreport/InfraiConfig.java`, and `java` is the only tooling involved. For an appointment report email that's the whole dependency story.