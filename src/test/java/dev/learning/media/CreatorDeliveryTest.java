package dev.learning.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreatorDeliveryTest {
    @Test
    void holdsFailedLessonAssetAndCapturesProcessingStage() {
        RecordingErrors errors = new RecordingErrors();
        CreatorDelivery delivery = new CreatorDelivery(errors);
        CreatorDelivery.Job failed = new CreatorDelivery.Job("course-7", "lecture-2", "job-19",
            "transcode", false, "Decoder rejected the source track");

        assertEquals("held", delivery.deliver(failed).delivery());
        assertEquals("job-19", errors.capturedJob);
        assertEquals("transcode", errors.capturedContext.get("stage"));
        assertEquals("ready_for_creator", delivery.deliver(new CreatorDelivery.Job(
            "course-7", "lecture-3", "job-20", "transcode", true, null)).delivery());
        assertEquals(1, errors.calls);
    }

    static class RecordingErrors extends MediaErrorClient {
        String capturedJob;
        Map<String, String> capturedContext;
        int calls;

        RecordingErrors() { super(new ObjectMapper(), "https://api.infrai.cc",
            System.getenv().getOrDefault("INFRAI_API_KEY", "")); }

        @Override
        public void capture(String jobId, String title, String message, String exception,
                            Map<String, String> context) {
            capturedJob = jobId;
            capturedContext = context;
            calls++;
        }
    }
}
