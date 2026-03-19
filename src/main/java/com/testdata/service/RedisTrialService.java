package com.testdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Manages the 5-parse free trial per user (identified by X-Api-Key header or
 * fallback to "anon:<ip>") using Redis counters and a spec result cache.
 *
 * Redis key layout:
 *   trial:<apiKey>           - INCR counter; TTL = trial.free.ttl.days
 *   speccache:<sha256(spec)> - cached parse/schema JSON string; TTL = speccache.ttl.days
 *
 * Set dev.trial.disabled=true (or env DEV_TRIAL_DISABLED=true) to bypass for local dev.
 */
@Service
public class RedisTrialService {

    @Value("${redis.uri:redis://localhost:6379}")
    private String redisUri;

    @Value("${trial.free.count:5}")
    private int trialFreeCount;

    @Value("${trial.free.ttl.days:30}")
    private int trialTtlDays;

    @Value("${speccache.ttl.days:7}")
    private int specCacheTtlDays;

    @Value("${dev.trial.disabled:false}")
    private boolean devTrialDisabled;

    @Value("${openapi.max.spec.size:3145728}")   // 3 MB default
    private int maxSpecBytes;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        redisClient = RedisClient.create(redisUri);
        connection = redisClient.connect();
    }

    @PreDestroy
    public void close() {
        if (connection != null) connection.close();
        if (redisClient != null) redisClient.shutdown();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validate spec size. Returns error message if too large, null if OK.
     */
    public String validateSpecSize(String spec) {
        if (spec == null) return null;
        if (spec.getBytes(StandardCharsets.UTF_8).length > maxSpecBytes) {
            return "Spec too large (max " + (maxSpecBytes / 1048576) + " MB).";
        }
        return null;
    }

    /**
     * Check Redis cache for this spec. Returns cached JSON string or null.
     * Does NOT count against the trial if a cache hit is returned.
     */
    public String getCachedResult(String specContent) {
        String key = "speccache:" + sha256(specContent);
        RedisCommands<String, String> cmd = connection.sync();
        return cmd.get(key);
    }

    /**
     * Store a parsed result in the spec cache.
     */
    public void cacheResult(String specContent, Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            String key = "speccache:" + sha256(specContent);
            long ttlSeconds = (long) specCacheTtlDays * 86400;
            connection.sync().setex(key, ttlSeconds, json);
        } catch (Exception e) {
            // cache write failure is non-critical; log and continue
        }
    }

    /**
     * Try to increment the trial counter for the given API key.
     * Returns null if allowed (counter ≤ trialFreeCount or dev mode bypassed).
     * Returns a TrialExhaustedResult if the user has exceeded their free trial.
     */
    public TrialExhaustedResult tryConsumeTrial(String apiKey) {
        if (devTrialDisabled) return null;

        RedisCommands<String, String> cmd = connection.sync();
        String trialKey = "trial:" + apiKey;

        long count = cmd.incr(trialKey);
        if (count == 1) {
            // First use — set TTL
            cmd.expire(trialKey, (long) trialTtlDays * 86400);
        }

        if (count > trialFreeCount) {
            // Revert the increment so this call isn't double-counted when paid
            cmd.decr(trialKey);
            return new TrialExhaustedResult(trialFreeCount);
        }

        return null; // allowed
    }

    /**
     * Returns remaining trial count for the given key (informational only).
     */
    public long remainingTrials(String apiKey) {
        String raw = connection.sync().get("trial:" + apiKey);
        long used = raw != null ? Long.parseLong(raw) : 0;
        return Math.max(0, trialFreeCount - used);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(input.hashCode()); // fallback
        }
    }

    // -------------------------------------------------------------------------
    // Models
    // -------------------------------------------------------------------------

    public static class TrialExhaustedResult {
        private final int limit;

        public TrialExhaustedResult(int limit) {
            this.limit = limit;
        }

        public Map<String, Object> toResponseBody() {
            return Map.of(
                "error",      "trial_exhausted",
                "message",    "Free trial exhausted (" + limit + " parses used). Upgrade to continue.",
                "upgradeUrl", "https://your-paywall.example.com/upgrade"
            );
        }
    }
}
