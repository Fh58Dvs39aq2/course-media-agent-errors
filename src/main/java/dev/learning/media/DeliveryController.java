package dev.learning.media;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryController {
    private final CreatorDelivery delivery;
    public DeliveryController(CreatorDelivery delivery) { this.delivery = delivery; }

    @PostMapping("/creator-deliveries")
    public CreatorDelivery.Result submit(@RequestBody CreatorDelivery.Job job) {
        return delivery.deliver(job);
    }

    @ExceptionHandler(MediaErrorClient.CaptureRejected.class)
    public ResponseEntity<Map<String, String>> rejected(MediaErrorClient.CaptureRejected error) {
        int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(Map.of("error", error.getMessage()));
    }
}
