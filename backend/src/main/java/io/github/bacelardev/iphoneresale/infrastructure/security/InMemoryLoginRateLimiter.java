package io.github.bacelardev.iphoneresale.infrastructure.security;

import io.github.bacelardev.iphoneresale.application.port.security.LoginAttemptLimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class InMemoryLoginRateLimiter implements LoginAttemptLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final int maxTrackedClients;
    private final Clock clock;
    private final LinkedHashMap<String, AttemptWindow> attempts = new LinkedHashMap<>(16, 0.75f, true);

    public InMemoryLoginRateLimiter(int maxAttempts, Duration window, int maxTrackedClients, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.maxTrackedClients = maxTrackedClients;
        this.clock = clock;
    }

    @Override
    public synchronized Decision tryAcquire(String clientAddress) {
        Instant now = clock.instant();
        purgeExpired(now);
        AttemptWindow current = attempts.get(clientAddress);

        if (current == null) {
            ensureCapacity();
            attempts.put(clientAddress, new AttemptWindow(1, now.plus(window)));
            return Decision.permit();
        }
        if (current.attempts >= maxAttempts) {
            long seconds = Math.max(1, Duration.between(now, current.expiresAt).toSeconds());
            return Decision.rejected(seconds);
        }

        attempts.put(clientAddress, new AttemptWindow(current.attempts + 1, current.expiresAt));
        return Decision.permit();
    }

    @Override
    public synchronized void reset(String clientAddress) {
        attempts.remove(clientAddress);
    }

    synchronized int trackedClientCount() {
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
        if (attempts.size() < maxTrackedClients) {
            return;
        }
        Iterator<String> iterator = attempts.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record AttemptWindow(int attempts, Instant expiresAt) {
    }
}
