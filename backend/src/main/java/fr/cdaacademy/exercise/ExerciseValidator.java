package fr.cdaacademy.exercise;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/** Correction automatique d'un type d'exercice. Toute validation est faite côté serveur. */
public interface ExerciseValidator {

    Set<String> kinds();

    ValidationResult validate(Exercise exercise, JsonNode answer);

    /** Partie du payload visible par l'apprenante (sans les éléments de correction). */
    JsonNode publicPayload(Exercise exercise);
}
