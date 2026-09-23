package fr.cdaacademy.exercise;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/exercises")
@Tag(name = "Exercices")
public class ExerciseController {

    /** Réponse : texte (code, SQL) ou tableau (trous, ordre des lignes) ou objet (modélisation). */
    /** Taille maximale d'une réponse (code, modèle MCD…), sérialisée en JSON. */
    static final int MAX_ANSWER_LENGTH = 100_000;

    public record SubmitRequest(JsonNode answer, Integer secondsSpent) {
    }

    private final ExerciseService exercises;

    public ExerciseController(ExerciseService exercises) {
        this.exercises = exercises;
    }

    @GetMapping
    @Operation(summary = "Catalogue des exercices, filtrable par parcours, type et difficulté")
    public List<ExerciseService.CatalogItem> catalog(@RequestParam(required = false) String course,
            @RequestParam(required = false) String kind, @RequestParam(required = false) String difficulty) {
        return exercises.catalog(CurrentUser.id(), blankToNull(course), blankToNull(kind), blankToNull(difficulty));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Énoncé d'un exercice (la correction n'est incluse qu'une fois débloquée)")
    public ExerciseService.ExerciseView view(@PathVariable String slug) {
        return exercises.view(CurrentUser.id(), slug);
    }

    @PostMapping("/{slug}/hints")
    @Operation(summary = "Dévoiler l'indice suivant (trois indices graduels)")
    public ExerciseService.Hint hint(@PathVariable String slug) {
        return exercises.nextHint(CurrentUser.id(), slug);
    }

    @PostMapping("/{slug}/solution")
    @Operation(summary = "Afficher la correction détaillée (réduit l'XP gagnée)")
    public ExerciseService.ExerciseView solution(@PathVariable String slug) {
        return exercises.revealSolution(CurrentUser.id(), slug);
    }

    @PostMapping("/{slug}/submit")
    @Operation(summary = "Soumettre une réponse : correction automatique côté serveur")
    public ExerciseService.SubmitResult submit(@PathVariable String slug, @RequestBody SubmitRequest req) {
        int seconds = req.secondsSpent() == null ? 0 : req.secondsSpent();
        if (req.answer() != null && req.answer().toString().length() > MAX_ANSWER_LENGTH) {
            throw new BusinessRuleException("Ta réponse est trop volumineuse pour être corrigée.");
        }
        return exercises.submit(CurrentUser.id(), slug, req.answer(), seconds);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
