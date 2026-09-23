package fr.cdaacademy.common;

/** Ressource introuvable : traduite en 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
