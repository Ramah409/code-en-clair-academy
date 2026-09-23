package fr.cdaacademy.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import fr.cdaacademy.config.AppProperties;
import fr.cdaacademy.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Émission et vérification des jetons d'accès JWT (HMAC-SHA256).
 * Le jeton d'accès est court (30 minutes par défaut) ; le renouvellement passe par un jeton
 * de rafraîchissement opaque stocké haché en base (voir AuthService).
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_LENGTH = 64;
    private static final String ISSUER = "cda-academy";

    private final SecretKey key;
    private final long accessMinutes;

    public JwtService(AppProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET doit être défini et contenir au moins " + MIN_SECRET_LENGTH + " caractères.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = props.jwt().accessMinutes();
    }

    public IssuedToken issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().getCode().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** Renvoie l'identifiant de l'utilisatrice si le jeton est valide, signé et non expiré. */
    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
