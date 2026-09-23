package fr.cdaacademy.exercise;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Exercice tel que stocké (solution et données de correction comprises : usage serveur uniquement). */
public record Exercise(long id, String slug, String kind, String difficulty, String title, String statement,
        String criteria, List<String> hints, String solution, String explanation, int xp, JsonNode payload,
        Long lessonId, Long courseId, String skillCode) {
}
