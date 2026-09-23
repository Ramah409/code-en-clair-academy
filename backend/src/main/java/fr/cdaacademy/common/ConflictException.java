package fr.cdaacademy.common;

/** Conflit avec l'état existant (doublon, ressource déjà liée) : traduit en 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
