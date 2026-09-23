package fr.cdaacademy.common;

/** Données valides syntaxiquement mais refusées par une règle de gestion : traduit en 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
