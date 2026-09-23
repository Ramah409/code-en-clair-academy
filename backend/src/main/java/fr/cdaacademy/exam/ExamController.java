package fr.cdaacademy.exam;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/exam")
@Tag(name = "Examen CDA", description = "Examens blancs, études de cas et questions du jury")
public class ExamController {

    public record AnswersRequest(Map<String, String> answers) {
    }

    public record ChecksRequest(Map<String, List<Integer>> checked) {
    }

    public record ReviewRequest(String key, boolean known) {
    }

    private final ExamService exams;

    public ExamController(ExamService exams) {
        this.exams = exams;
    }

    @GetMapping
    @Operation(summary = "Examens blancs (avec historique), études de cas et statistiques d'entraînement au jury")
    public ExamService.Overview overview() {
        return exams.overview(CurrentUser.id());
    }

    @GetMapping("/cases/{slug}")
    @Operation(summary = "Une étude de cas ; le corrigé n'est envoyé qu'après la rédaction des réponses")
    public ExamService.CaseStudy caseStudy(@PathVariable String slug) {
        return exams.caseStudy(CurrentUser.id(), slug);
    }

    @PutMapping("/cases/{slug}/answers")
    @Operation(summary = "Enregistrer les réponses rédigées")
    public ExamService.CaseStudy saveAnswers(@PathVariable String slug, @RequestBody AnswersRequest req) {
        return exams.saveAnswers(CurrentUser.id(), slug, req.answers());
    }

    @PostMapping("/cases/{slug}/reveal")
    @Operation(summary = "Afficher le corrigé et la grille (une réponse par tâche exigée)")
    public ExamService.CaseStudy reveal(@PathVariable String slug) {
        return exams.reveal(CurrentUser.id(), slug);
    }

    @PutMapping("/cases/{slug}/checks")
    @Operation(summary = "Enregistrer l'auto-évaluation avec la grille de correction")
    public ExamService.CaseStudy saveChecks(@PathVariable String slug, @RequestBody ChecksRequest req) {
        return exams.saveChecks(CurrentUser.id(), slug, req.checked());
    }

    @GetMapping("/jury")
    @Operation(summary = "Séance de questions du jury (les questions à revoir d'abord)")
    public List<ExamService.JuryCard> jury(@RequestParam(required = false) String course,
            @RequestParam(defaultValue = "false") boolean all, @RequestParam(defaultValue = "false") boolean toReview,
            @RequestParam(defaultValue = "15") int size) {
        return exams.session(CurrentUser.id(), course, all, toReview, size);
    }

    @PostMapping("/jury/review")
    @Operation(summary = "Auto-évaluation d'une question du jury : je savais / à revoir")
    public void review(@RequestBody ReviewRequest req) {
        exams.review(CurrentUser.id(), req.key(), req.known());
    }
}
