package gg.casualchallenge.application.api.security.ratelimit;

import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final long refillPerMinute;

    public RateLimiterService(
            @Value("${casual-challenge.security.rate-limit.capacity}") long capacity,
            @Value("${casual-challenge.security.rate-limit.refill-per-minute}") long refillPerMinute
    ) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    public Bucket resolveBucket(String token) {
        return buckets.computeIfAbsent(token, this::createBucket);
    }

    private Bucket createBucket(String token) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(this.capacity)
                        .refillGreedy(this.refillPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    public boolean consumeToken(String token) {
        Bucket bucket = resolveBucket(token);
        return bucket.tryConsume(1); // Consume 1 token
    }
}
