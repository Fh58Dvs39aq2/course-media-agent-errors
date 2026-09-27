package dev.learning.media;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CreatorDelivery {
    private final MediaErrorClient errors;

    public CreatorDelivery(MediaErrorClient errors) { this.errors = errors; }

    public record Job(String courseId, String assetId, String jobId, String stage,
                      boolean processed, String failure) {}
    public record Result(String jobId, String delivery, String assetId) {}

    public Result deliver(Job job) {
        if (!job.processed()) {
            errors.capture(job.jobId(), "Course media processing failed",
                job.failure(), job.failure(), Map.of("courseId", job.courseId(),
                    "assetId", job.assetId(), "jobId", job.jobId(), "stage", job.stage()));
            return new Result(job.jobId(), "held", job.assetId());
        }
        return new Result(job.jobId(), "ready_for_creator", job.assetId());
    }
}
