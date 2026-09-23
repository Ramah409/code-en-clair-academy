package fr.cdaacademy.course;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.quiz.QuestionBank.Answer;
import fr.cdaacademy.security.CurrentUser;
import fr.cdaacademy.user.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Parcours et leçons")
public class CourseController {

    public record StepRequest(int step) {
    }

    public record CompleteRequest(Integer secondsSpent) {
    }

    private final CourseService courses;
    private final LessonService lessons;

    public CourseController(CourseService courses, LessonService lessons) {
        this.courses = courses;
        this.lessons = lessons;
    }

    @GetMapping("/courses")
    @Operation(summary = "Parcours disponibles avec la progression de l'apprenante")
    public List<CourseService.CourseSummary> catalog() {
        return courses.catalog(CurrentUser.id(), isAdmin());
    }

    @GetMapping("/courses/{slug}")
    @Operation(summary = "Chapitres, leçons, QCM, examen final et projet d'un parcours")
    public CourseService.CourseDetail course(@PathVariable String slug) {
        return courses.detail(CurrentUser.id(), isAdmin(), slug);
    }

    @GetMapping("/lessons/{slug}")
    @Operation(summary = "Contenu d'une leçon (si elle est débloquée)")
    public LessonService.LessonView lesson(@PathVariable String slug) {
        return lessons.view(CurrentUser.id(), isAdmin(), slug);
    }

    @PostMapping("/lessons/{slug}/questions/{code}/answer")
    @Operation(summary = "Répondre à une question du cours : correction immédiate et expliquée")
    public LessonService.AnswerResult answer(@PathVariable String slug, @PathVariable String code,
            @RequestBody Answer answer) {
        return lessons.answer(CurrentUser.id(), isAdmin(), slug, code, answer);
    }

    @PostMapping("/lessons/{slug}/step")
    @Operation(summary = "Mémoriser l'avancement dans la leçon (reprise)")
    public ResponseEntity<Void> step(@PathVariable String slug, @RequestBody StepRequest req) {
        lessons.saveStep(CurrentUser.id(), isAdmin(), slug, req.step());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lessons/{slug}/complete")
    @Operation(summary = "Valider la leçon (toutes les questions et tous les exercices réussis)")
    public LessonService.Completion complete(@PathVariable String slug, @RequestBody(required = false)
            CompleteRequest req) {
        int seconds = req == null || req.secondsSpent() == null ? 0 : req.secondsSpent();
        return lessons.complete(CurrentUser.id(), isAdmin(), slug, seconds);
    }

    static boolean isAdmin() {
        return CurrentUser.get().role() == RoleCode.ADMIN;
    }
}
