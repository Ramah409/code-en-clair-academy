package fr.cdaacademy.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.cdaacademy.auth.dto.AuthResponse;
import fr.cdaacademy.auth.dto.LoginRequest;
import fr.cdaacademy.auth.dto.RegisterRequest;
import fr.cdaacademy.common.ConflictException;
import fr.cdaacademy.common.NotFoundException;
import fr.cdaacademy.common.UnauthorizedException;
import fr.cdaacademy.config.AppProperties;
import fr.cdaacademy.security.JwtService;
import fr.cdaacademy.user.RoleCode;
import fr.cdaacademy.user.RoleRepository;
import fr.cdaacademy.user.User;
import fr.cdaacademy.user.UserMapper;
import fr.cdaacademy.user.UserRepository;

/**
 * Inscription, connexion et renouvellement de session.
 * <p>
 * Le jeton de rafraîchissement est aléatoire (256 bits), renvoyé une seule fois au navigateur
 * dans un cookie HttpOnly, et stocké en base sous forme d'empreinte SHA-256. À chaque
 * renouvellement il est remplacé (rotation) ; la réutilisation d'un jeton déjà révoqué révoque
 * toutes les sessions du compte (détection de vol).
 */
@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Adresse e-mail ou mot de passe incorrect.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter limiter;
    private final long refreshDays;

    public AuthService(UserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder, JwtService jwtService, LoginAttemptLimiter limiter,
            AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limiter = limiter;
        this.refreshDays = props.jwt().refreshDays();
    }

    /** Toute nouvelle inscription reçoit le rôle USER : le rôle n'est jamais choisi par le client. */
    @Transactional
    public Session register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Un compte existe déjà avec cette adresse e-mail.");
        }
        var role = roles.findByCode(RoleCode.USER).orElseThrow(() -> new NotFoundException("Rôle introuvable."));
        User user = users.save(new User(email, req.displayName().trim(), passwordEncoder.encode(req.password()), role));
        return openSession(user);
    }

    @Transactional
    public Session login(LoginRequest req) {
        String email = normalize(req.email());
        limiter.checkAllowed(email);
        User user = users.findByEmailIgnoreCase(email)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            limiter.recordFailure(email);
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Ce compte a été désactivé. Contacte la formatrice.");
        }
        limiter.reset(email);
        return openSession(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Session expirée. Connecte-toi à nouveau.");
        }
        Instant now = Instant.now();
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Session expirée. Connecte-toi à nouveau."));
        if (stored.getRevokedAt() != null) {
            // Un jeton déjà utilisé est présenté à nouveau : on coupe toutes les sessions du compte.
            refreshTokens.revokeAllForUser(stored.getUser().getId(), now);
            throw new UnauthorizedException("Session invalide. Connecte-toi à nouveau.");
        }
        if (!stored.isActive(now) || !stored.getUser().isEnabled()) {
            stored.revoke(now);
            throw new UnauthorizedException("Session expirée. Connecte-toi à nouveau.");
        }
        stored.revoke(now);
        return openSession(stored.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(Instant.now()));
        }
    }

    private Session openSession(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant refreshExpiry = Instant.now().plus(refreshDays, ChronoUnit.DAYS);
        refreshTokens.save(new RefreshToken(user, hash(raw), refreshExpiry));

        var access = jwtService.issueAccessToken(user);
        var response = new AuthResponse(access.value(), "Bearer", access.expiresAt(), UserMapper.toResponse(user));
        return new Session(response, raw, refreshExpiry);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Résultat d'une ouverture de session : corps de réponse + jeton de rafraîchissement brut. */
    public record Session(AuthResponse response, String refreshToken, Instant refreshExpiresAt) {
    }
}
