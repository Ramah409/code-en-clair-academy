package fr.cdaacademy.common;

/** Authentification absente ou invalide : traduit en 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
