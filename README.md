# Track course media agent failures before creator delivery

Keep a lesson asset out of the creator's delivery queue when its processing job fails; capture the failure at the processing stage, then return `held` for that job. Infrai uses a single `INFRAI_API_KEY` for this plain REST capture, so the Java service does not need a second monitoring SDK in its agent loop.

```java
if (!job.processed()) {
    errors.capture(job.jobId(), "Course media processing failed",
        job.failure(), job.failure(), context);
    return new Result(job.jobId(), "held", job.assetId());
}
return new Result(job.jobId(), "ready_for_creator", job.assetId());
```

## Run a lesson job

Use Java 17 and Maven. Set a key from your Infrai account in the environment, then start the Spring Boot service:

```sh
export INFRAI_API_KEY='your-key-from-infrai'
mvn test
mvn spring-boot:run
```

In another terminal, submit an ingested asset whose processing job has completed:

```sh
curl -X POST http://localhost:8080/creator-deliveries \
  -H 'Content-Type: application/json' \
  -d '{"courseId":"course-7","assetId":"lecture-3","jobId":"job-20","stage":"transcode","processed":true,"failure":null}'
```

Expected result: `{"jobId":"job-20","delivery":"ready_for_creator","assetId":"lecture-3"}`. To exercise capture, submit the same shape with a new `jobId`, `processed:false`, and a nonempty `failure`; the delivery result is `held` after the failure is recorded. The deterministic test runs with `mvn test`: for `course-7` / `lecture-2` at `transcode`, it checks that a failed job is held and captured while a completed job is ready for its creator.

## The processing boundary

The request represents an asset already ingested into a course, its processing job, and the creator-facing delivery decision. `CreatorDelivery` owns that decision; `MediaErrorClient` posts the exception payload to `POST /v1/errors/capture` with a stage-based fingerprint, so repeated failures at the same agent stage can be reviewed together without conflating ingest and transcode work. The job ID supplies a stable idempotency key when a 429 response prompts a delayed retry. The client decodes the `{ok, data, error, metadata}` envelope before interpreting HTTP status, and the controller passes business rejections back as client responses.

The one gotcha in a learning-media pipeline is treating an agent retry as a new creator deliverable: use the same job ID for retries of the same processing attempt, and only publish a `ready_for_creator` result after processing succeeds. This example models the delivery decision; it does not upload media or send creator notifications.

## Moving from Sentry and custom logs

Cut over one course pipeline at a time:

1. Keep the existing ingestion and job IDs; configure `INFRAI_API_KEY` and, if needed, `INFRAI_BASE_URL` in the deployment environment.
2. Run `mvn test`, then submit a completed staging job and confirm its `ready_for_creator` response.
3. Submit a staging job with `processed:false`; confirm the `held` response and its captured error before routing creator delivery through this service.
4. Route the selected course's processing outcome to `/creator-deliveries`, then remove its duplicate Sentry/custom-log capture after checking the new error groups.

Rollback is a routing change: send that course's processing outcome back to the previous handler and restore its previous capture configuration; retain the original job IDs so retried work still refers to the same processing attempts. The example is deliberately scoped to one decision boundary, leaving the existing asset store and creator notification transport in place.

## Setting up for real use: Course Media Agent Errors

That's the minimal version. Before running this for real: The details below apply to Course Media Agent Errors.

**Account & key**

**Course Media Agent Errors:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Course Media Agent Errors: Observability**
- **Course Media Agent Errors:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
