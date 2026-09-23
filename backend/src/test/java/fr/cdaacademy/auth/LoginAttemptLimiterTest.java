package fr.cdaacademy.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import fr.cdaacademy.common.TooManyRequestsException;

class LoginAttemptLimiterTest {

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    @Test
    void bloqueApresCinqEchecs() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("a@b.fr");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("A@B.FR")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void debloqueApresLaFenetre() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("a@b.fr");
        }
        clock.now = clock.now.plus(LoginAttemptLimiter.WINDOW).plus(Duration.ofSeconds(1));
        assertThatCode(() -> limiter.checkAllowed("a@b.fr")).doesNotThrowAnyException();
    }

    @Test
    void uneConnexionReussieRemetLeCompteurAZero() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("a@b.fr");
        }
        limiter.reset("a@b.fr");
        assertThatCode(() -> limiter.checkAllowed("a@b.fr")).doesNotThrowAnyException();
    }
}
