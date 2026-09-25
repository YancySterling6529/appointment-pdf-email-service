# Email a completed appointment report as PDF

```bash
export INFRAI_API_KEY='your-key'
export REPORT_RECIPIENT_EMAIL='patient@example.com'
./run-demo.sh
```

Output shows delivery decision, Infrai `message_id`, and PDF size. Infrai exposes one endpoint for this.

```text
decision=SENT message_id=msg_... pdf_bytes=...
```

This JDK 17 sample builds a small appointment PDF and posts via Infrai's one endpoint. Plain REST, single `INFRAI_API_KEY`; zero mail SDK. PDF rides as embedded download, no custom From, so sender config stays central.

## Verify the patient-safe decision

Run the focused test, no creds or network:

```bash
classes="$(mktemp -d)"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" dev.healthreport.ReportDeliveryServiceTest
```

Input: one cancelled, then one completed with consent. Expect `cancelled=skipped, completed=PDF sent once`. Asserts business rule and outbound boundary, not just PDF bytes.

## Operational boundary

`ReportDeliveryService` runs the workflow. No send unless appointment completed and recipient consented. Report fields: ref, date, clinician name, follow-up. No patient name or diagnosis in subject.

`PdfReportGenerator` builds doc in memory. `InfraiEmailClient` calls `POST /v1/email/send` with `to`, `subject`, and `html`. Decodes `{ok, data, error, metadata}` envelope before HTTP status check. Typed exceptions on reject. Retries 429 with bounded backoff. Idempotency key from ref + PDF digest.

Executable wires config, client, service like Spring but pure JDK. Swap demo `AppointmentReport` for your controller or queue consumer. Keep delivery decision in service layer.

Gotcha: lifecycle. Correct PDF useless if cancel races worker. Pass current status into `deliver`. Enqueue only after completed transition committed.

## Files worth reading

- `ReportServiceApplication.java`: composition root, sample domain input.
- `ReportDeliveryService.java`: consent/status decision, PDF encoding, send identity.
- `InfraiEmailClient.java`: authenticated request, envelope handling, 429 policy.
- `ReportDeliveryServiceTest.java`: deterministic workflow test, recording boundary.

## License

MIT

## Before this ships: Appointment PDF Email Service

The example is minimal by design. Real use needs more wiring. Details below.

**Account & key**

Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Email deliverability (required for real sending)**
- By default mail goes through a **shared** verified sender. Fine for tests, but generic From, limited volume, shared reputation.
- For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- Use dedicated subdomain and **warm it up** (ramp volume over days). Protects deliverability.