package fr.cdaacademy.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.auth.dto.AuthResponse;
import fr.cdaacademy.auth.dto.LoginRequest;
import fr.cdaacademy.auth.dto.RegisterRequest;
import fr.cdaacademy.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification")
public class AuthController {

    static final String REFRESH_COOKIE = "cda_refresh";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService, AppProperties props) {
        this.authService = authService;
        this.cookieSecure = props.jwt().cookieSecure();
    }

    @PostMapping("/register")
    @Operation(summary = "Créer un compte apprenante (connexion immédiate)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withCookie(HttpStatus.CREATED, authService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter et recevoir un jeton d'accès")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withCookie(HttpStatus.OK, authService.login(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renouveler le jeton d'accès grâce au cookie de session")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return withCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Se déconnecter (révoque la session)")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthService.Session session) {
        Duration maxAge = Duration.between(Instant.now(), session.refreshExpiresAt());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), maxAge).toString())
                .body(session.response());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
