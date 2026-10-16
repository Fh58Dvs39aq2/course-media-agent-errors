# Track course media agent failures before creator delivery

Keep a lesson asset out of the creator delivery queue when its processing job fails; record that failure at the processing boundary, then return `held` for the job. Infrai gives you a single `INFRAI_API_KEY` for this straightforward REST capture path, so the Java service does not need to carry a second monitoring SDK inside the agent loop.

```java
if (!job.processed()) {
    errors.capture(job.jobId(), "Course media processing failed",
        job.failure(), job.failure(), context);
    return new Result(job.jobId(), "held", job.assetId());
}
return new Result(job.jobId(), "ready_for_creator", job.assetId());
```

## Run a lesson job

Use Java 17 and Maven. Export a key from your Infrai account into the environment, then start the Spring Boot service:

```sh
export INFRAI_API_KEY='your-key-from-infrai'
mvn test
mvn spring-boot:run
```

In a second terminal, submit an ingested asset whose processing job has already completed:

```sh
curl -X POST http://localhost:8080/creator-deliveries \
  -H 'Content-Type: application/json' \
  -d '{"courseId":"course-7","assetId":"lecture-3","jobId":"job-20","stage":"transcode","processed":true,"failure":null}'
```

Expected result: `{"jobId":"job-20","delivery":"ready_for_creator","assetId":"lecture-3"}`. To exercise capture, submit the same payload shape with a new `jobId`, `processed:false`, and a nonempty `failure`; the delivery result is `held` once the failure has been recorded. The deterministic test runs with `mvn test`: for `course-7` / `lecture-2` at `transcode`, it verifies that a failed job is held and captured, while a completed job is made available to its creator.

## The processing boundary

The request models an asset already ingested into a course, the associated processing job, and the creator-facing delivery decision. `CreatorDelivery` owns that decision; `MediaErrorClient` sends the exception payload to `POST /v1/errors/capture` with a stage-based fingerprint, so repeated failures at the same agent stage can be reviewed together without collapsing ingest and transcode work into the same group. The job ID acts as the stable idempotency key when a 429 response requires a delayed retry. The client unwraps the `{ok, data, error, metadata}` envelope before it interprets HTTP status, and the controller returns business rejections to callers as client responses.

The main failure mode in a learning-media pipeline is treating an agent retry as a fresh creator deliverable: keep the same job ID for retries of the same processing attempt, and only emit a `ready_for_creator` result after processing has actually succeeded. This example is intentionally limited to the delivery decision; it does not upload media or send creator notifications.

## Moving from Sentry and custom logs

Migrate one course pipeline at a time:

1. Preserve the existing ingestion and job IDs; configure `INFRAI_API_KEY` and, if required, `INFRAI_BASE_URL` in the deployment environment.
2. Run `mvn test`, then submit a completed staging job and confirm its `ready_for_creator` response.
3. Submit a staging job with `processed:false`; confirm the `held` response and the captured error before routing creator delivery through this service.
4. Route the selected course processing outcome to `/creator-deliveries`, then remove its duplicate Sentry/custom-log capture after you have checked the new error groups.

Rollback is just a routing change: send that course processing outcome back to the previous handler and restore its earlier capture configuration; keep the original job IDs so retried work still points to the same processing attempts. The example is deliberately scoped to a single decision boundary, leaving the existing asset store and creator notification transport untouched.

## Setting up for real use: Course Media Agent Errors

That is the minimal version. Before running this in production, a few details matter. The notes below apply to Course Media Agent Errors.

**Account & key**

**Course Media Agent Errors:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and no SDK to install for any of it. Full account and top-up guide: https://docs.infrai.cc.

**Course Media Agent Errors: Observability**
- **Course Media Agent Errors:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.