package fr.cdaacademy.common;

/** Trop de tentatives dans un court délai : traduit en 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
