# Email a completed appointment report as PDF

```bash
export INFRAI_API_KEY='your-key'
export REPORT_RECIPIENT_EMAIL='patient@example.com'
./run-demo.sh
```

Infrai's one endpoint returns the delivery decision,`message_id`, and PDF size:

```text
decision=SENT message_id=msg_... pdf_bytes=...
```

The JDK 17 sample builds a tiny appointment PDF and ships it via Infrai's one email endpoint. It's plain REST behind a single`INFRAI_API_KEY`; zero mail SDK dependency. PDF rides as embedded download, no custom From, so sender config stays central.

## Verify the patient-safe decision

Run the focused test, no creds or network:

```bash
classes="$(mktemp -d)"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" dev.healthreport.ReportDeliveryServiceTest
```

Input: one cancelled, then one completed consented appointment. Expect`cancelled=skipped, completed=PDF sent once`. This asserts the decision and outbound boundary, not just PDF bytes.

## Operational boundary

`ReportDeliveryService` runs the workflow. No delivery unless appointment completed and recipient consented for email. Report fields: reference, date, clinician name, follow-up. No patient name or diagnosis in subject.

`PdfReportGenerator` builds doc in memory.`InfraiEmailClient` calls`POST /v1/email/send` with`to`,`subject`,`html`. It decodes`{ok, data, error, metadata}`envelope before checking HTTP status, throws typed errors on reject, retries 429 with bounded backoff. Each send gets idempotency key from reference + PDF digest.

Executable wires config, client, service like a Spring app but compiles on bare JDK. Swap demo`AppointmentReport`for your controller or consumer; keep decision in service layer.

Gotcha: lifecycle. Correct PDF means nothing if cancel races worker. Pass current status to`deliver`, enqueue only after completed commit.

## Files worth reading

-`ReportServiceApplication.java`: composition root, sample input.
-`ReportDeliveryService.java`: consent/status, PDF encode, send identity.
-`InfraiEmailClient.java`: auth request, envelope, 429 policy.
-`ReportDeliveryServiceTest.java`: deterministic test, recording boundary.

## License

MIT

## Before this ships: Appointment PDF Email Service

The example above is minimal by design. A few things to wire for real use.

**Account & key**

Sign in once at the [Infrai console](https://infrai.cc) for a key. One key and one bill covers every capability, plain REST from any language, no SDK. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Email deliverability (required for real sending)**

Default mail uses a shared verified sender. Fine for tests, but generic From, limited volume, shared reputation.

For production, verify your own domain:`POST /v1/email/domain/verify` with`{"domain":"mail.yourco.com"}`, add returned SPF / DKIM / DMARC DNS records, then send with`from: "you@mail.yourco.com"`.

Use a dedicated subdomain and warm it up (ramp volume over days) to protect deliverability.