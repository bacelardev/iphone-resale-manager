package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.application.port.security.LoginAttemptLimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixed-window counter per key, bounded to {@code maxTrackedKeys} entries. Rejected attempts never
 * allocate entries. When the map is full, the least recently used key that is not blocking is
 * evicted first, so flooding the map with new keys cannot silently lift an active block.
 */
public class InMemoryLoginRateLimiter implements LoginAttemptLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final int maxTrackedKeys;
    private final Clock clock;
    private final LinkedHashMap<String, AttemptWindow> attempts = new LinkedHashMap<>(16, 0.75f, true);

    public InMemoryLoginRateLimiter(int maxAttempts, Duration window, int maxTrackedKeys, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = clock;
    }

    @Override
    public synchronized Decision tryAcquire(String key) {
        Instant now = clock.instant();
        purgeExpired(now);
        AttemptWindow current = attempts.get(key);

        if (current == null) {
            ensureCapacity();
            attempts.put(key, new AttemptWindow(1, now.plus(window)));
            return Decision.permit();
        }
        if (current.attempts >= maxAttempts) {
            long seconds = Math.max(1, Duration.between(now, current.expiresAt).toSeconds());
            return Decision.rejected(seconds);
        }

        attempts.put(key, new AttemptWindow(current.attempts + 1, current.expiresAt));
        return Decision.permit();
    }

    @Override
    public synchronized void release(String key) {
        AttemptWindow current = attempts.get(key);
        if (current == null) {
            return;
        }
        if (current.attempts <= 1) {
            attempts.remove(key);
        } else {
            attempts.put(key, new AttemptWindow(current.attempts - 1, current.expiresAt));
        }
    }

    @Override
    public synchronized void reset(String key) {
        attempts.remove(key);
    }

    synchronized int trackedKeyCount() {
        return attempts.size();
    }

    private void purgeExpired(Instant now) {
        Iterator<Map.Entry<String, AttemptWindow>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().expiresAt.isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private void ensureCapacity() {
        if (attempts.size() < maxTrackedKeys) {
            return;
        }
        Iterator<AttemptWindow> windows = attempts.values().iterator();
        while (windows.hasNext()) {
            if (windows.next().attempts < maxAttempts) {
                windows.remove();
                return;
            }
        }
        // Every tracked key is blocking: drop the least recently used one to stay bounded. Reaching
        // this state requires maxTrackedKeys keys that each accumulated maxAttempts attempts.
        Iterator<String> eldest = attempts.keySet().iterator();
        if (eldest.hasNext()) {
            eldest.next();
            eldest.remove();
        }
    }

    private record AttemptWindow(int attempts, Instant expiresAt) {
    }
}
