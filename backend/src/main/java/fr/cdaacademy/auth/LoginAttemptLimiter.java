package fr.cdaacademy.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import fr.cdaacademy.common.TooManyRequestsException;

/**
 * Limite les échecs de connexion par adresse e-mail (fenêtre glissante en mémoire)
 * pour freiner les attaques par force brute.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String email) {
        Deque<Instant> recent = prune(key(email));
        if (recent != null && recent.size() >= MAX_FAILURES) {
            throw new TooManyRequestsException(
                    "Trop de tentatives de connexion. Réessaie dans quelques minutes.");
        }
    }

    public void recordFailure(String email) {
        failures.computeIfAbsent(key(email), k -> new ArrayDeque<>()).addLast(clock.instant());
    }

    public void reset(String email) {
        failures.remove(key(email));
    }

    private Deque<Instant> prune(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return null;
        }
        Instant limit = clock.instant().minus(WINDOW);
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(limit)) {
                recent.pollFirst();
            }
        }
        return recent;
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
