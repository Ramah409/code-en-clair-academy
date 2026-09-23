package fr.cdaacademy.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import fr.cdaacademy.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Réponses 401 et 403 au même format JSON que le reste de l'API. */
public final class JsonSecurityHandlers {

    private JsonSecurityHandlers() {
    }

    public static AuthenticationEntryPoint unauthorized(ObjectMapper mapper) {
        return (req, res, ex) -> write(mapper, req, res, 401, "Unauthorized",
                "Connecte-toi pour accéder à cette ressource.");
    }

    public static AccessDeniedHandler forbidden(ObjectMapper mapper) {
        return (req, res, ex) -> write(mapper, req, res, 403, "Forbidden",
                "Vous n'avez pas les droits nécessaires pour cette action.");
    }

    private static void write(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res,
            int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), ApiError.of(status, error, message, req.getRequestURI()));
    }
}
