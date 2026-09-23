package fr.cdaacademy.quiz;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.quiz.QuestionBank.Answer;
import fr.cdaacademy.security.CurrentUser;
import fr.cdaacademy.user.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/quiz")
@Tag(name = "QCM")
public class QuizController {

    public record AnswerRequest(int position, Answer answer) {
    }

    private final QuizService quiz;
    private final QuizStatsService stats;

    public QuizController(QuizService quiz, QuizStatsService stats) {
        this.quiz = quiz;
        this.stats = stats;
    }

    @GetMapping("/options")
    @Operation(summary = "Matières, chapitres, niveaux et examens blancs disponibles")
    public QuizStatsService.Options options() {
        return stats.options(CurrentUser.id());
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques par thème et erreurs récurrentes")
    public QuizStatsService.Stats stats() {
        return stats.stats(CurrentUser.id());
    }

    @GetMapping("/history")
    @Operation(summary = "Historique des tentatives et des scores")
    public List<QuizService.HistoryItem> history(@RequestParam(required = false) String scope,
            @RequestParam(required = false) String course) {
        return quiz.history(CurrentUser.id(), emptyToNull(scope), emptyToNull(course));
    }

    @PostMapping("/start")
    @Operation(summary = "Démarrer un QCM (chapitre, parcours, entraînement, erreurs, aléatoire, examen blanc)")
    public QuizService.AttemptView start(@RequestBody QuizService.StartRequest req) {
        return quiz.start(CurrentUser.id(), CurrentUser.get().role() == RoleCode.ADMIN, req);
    }

    @GetMapping("/attempts/{id}")
    @Operation(summary = "Questions d'une tentative en cours")
    public QuizService.AttemptView attempt(@PathVariable long id) {
        return quiz.view(CurrentUser.id(), id);
    }

    @PostMapping("/attempts/{id}/answer")
    @Operation(summary = "Répondre à une question (correction immédiate en mode entraînement)")
    public QuizService.AnswerResult answer(@PathVariable long id, @RequestBody AnswerRequest req) {
        return quiz.answer(CurrentUser.id(), id, req.position(), req.answer());
    }

    @PostMapping("/attempts/{id}/submit")
    @Operation(summary = "Terminer et corriger le QCM")
    public QuizService.ResultView submit(@PathVariable long id) {
        return quiz.submit(CurrentUser.id(), id);
    }

    @GetMapping("/attempts/{id}/result")
    @Operation(summary = "Correction détaillée et notions à revoir")
    public QuizService.ResultView result(@PathVariable long id) {
        return quiz.result(CurrentUser.id(), id, null);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
