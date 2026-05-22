package com.cotisapp.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessions actives en mémoire (activité API ou connexion récente par organisation).
 */
@Service
public class PresenceService {

    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(15);
    private static final long TOUCH_THROTTLE_SECONDS = 30;

    private final ConcurrentHashMap<String, Instant> lastSeenByKey = new ConcurrentHashMap<>();

    public void touch(Long userId, Long organisationId) {
        if (userId == null || organisationId == null) {
            return;
        }
        String key = key(userId, organisationId);
        Instant now = Instant.now();
        lastSeenByKey.compute(key, (k, previous) -> {
            if (previous != null && ChronoUnit.SECONDS.between(previous, now) < TOUCH_THROTTLE_SECONDS) {
                return previous;
            }
            return now;
        });
    }

    public boolean isOnline(Long userId, Long organisationId) {
        if (userId == null || organisationId == null) {
            return false;
        }
        Instant last = lastSeenByKey.get(key(userId, organisationId));
        if (last == null) {
            return false;
        }
        return Duration.between(last, Instant.now()).compareTo(ONLINE_WINDOW) <= 0;
    }

    public Optional<LocalDateTime> derniereActivite(Long userId, Long organisationId) {
        if (userId == null || organisationId == null) {
            return Optional.empty();
        }
        Instant last = lastSeenByKey.get(key(userId, organisationId));
        if (last == null) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.ofInstant(last, ZoneId.systemDefault()));
    }

    private static String key(Long userId, Long organisationId) {
        return organisationId + ":" + userId;
    }
}
