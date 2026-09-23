package fr.cdaacademy.lab;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fr.cdaacademy.common.TooManyRequestsException;
import fr.cdaacademy.config.AppProperties;

/** Limite le nombre d'exécutions SQL par apprenante et par minute (fenêtre glissante en mémoire). */
@Component
public class LabRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<Long, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final int maxPerMinute;
    private final Clock clock;

    @Autowired
    public LabRateLimiter(AppProperties props) {
        this(props.lab().requestsPerMinute(), Clock.systemUTC());
    }

    LabRateLimiter(int maxPerMinute, Clock clock) {
        this.maxPerMinute = maxPerMinute;
        this.clock = clock;
    }

    public void acquire(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> recent = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(WINDOW))) {
                recent.pollFirst();
            }
            if (recent.size() >= maxPerMinute) {
                throw new TooManyRequestsException(
                        "Beaucoup d'exécutions en peu de temps : patiente quelques secondes avant de relancer.");
            }
            recent.addLast(now);
        }
    }
}
