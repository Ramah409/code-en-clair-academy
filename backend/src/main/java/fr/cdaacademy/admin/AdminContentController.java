package fr.cdaacademy.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.cdaacademy.admin.AdminContentService.ChapterForm;
import fr.cdaacademy.admin.AdminContentService.ChapterRow;
import fr.cdaacademy.admin.AdminContentService.CheckResult;
import fr.cdaacademy.admin.AdminContentService.CourseForm;
import fr.cdaacademy.admin.AdminContentService.CourseRow;
import fr.cdaacademy.admin.AdminContentService.ExerciseDetail;
import fr.cdaacademy.admin.AdminContentService.ExerciseForm;
import fr.cdaacademy.admin.AdminContentService.ExerciseRow;
import fr.cdaacademy.admin.AdminContentService.ExerciseSaved;
import fr.cdaacademy.admin.AdminContentService.LessonDetail;
import fr.cdaacademy.admin.AdminContentService.LessonForm;
import fr.cdaacademy.admin.AdminContentService.QuestionDetail;
import fr.cdaacademy.admin.AdminContentService.QuestionForm;
import fr.cdaacademy.admin.AdminContentService.QuestionRow;
import fr.cdaacademy.common.PageResponse;
import fr.cdaacademy.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Administration des contenus (réservée au rôle ADMIN par SecurityConfig : /api/admin/**). */
@RestController
@RequestMapping("/api/admin/content")
@Tag(name = "Administration des contenus", description = "Parcours, chapitres, leçons, questions et exercices")
public class AdminContentController {

    private final AdminContentService admin;

    public AdminContentController(AdminContentService admin) {
        this.admin = admin;
    }

    // Parcours
    @GetMapping("/courses")
    public List<CourseRow> courses() {
        return admin.courses();
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseRow createCourse(@RequestBody CourseForm form) {
        return admin.createCourse(form);
    }

    @PutMapping("/courses/{id}")
    public CourseRow updateCourse(@PathVariable long id, @RequestBody CourseForm form) {
        return admin.updateCourse(id, form);
    }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@PathVariable long id) {
        admin.deleteCourse(id);
    }

    // Chapitres
    @GetMapping("/courses/{id}/chapters")
    public List<ChapterRow> chapters(@PathVariable long id) {
        return admin.chapters(id);
    }

    @PostMapping("/courses/{id}/chapters")
    public List<ChapterRow> createChapter(@PathVariable long id, @RequestBody ChapterForm form) {
        return admin.createChapter(id, form);
    }

    @PutMapping("/chapters/{id}")
    public List<ChapterRow> updateChapter(@PathVariable long id, @RequestBody ChapterForm form) {
        return admin.updateChapter(id, form);
    }

    @PostMapping("/chapters/{id}/move")
    public List<ChapterRow> moveChapter(@PathVariable long id, @RequestParam int delta) {
        return admin.moveChapter(id, delta);
    }

    @DeleteMapping("/chapters/{id}")
    public List<ChapterRow> deleteChapter(@PathVariable long id) {
        return admin.deleteChapter(id);
    }

    // Leçons
    @GetMapping("/lessons/{id}")
    public LessonDetail lesson(@PathVariable long id) {
        return admin.lesson(id);
    }

    @PostMapping("/chapters/{id}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonDetail createLesson(@PathVariable long id, @RequestBody LessonForm form) {
        return admin.createLesson(id, form);
    }

    @PutMapping("/lessons/{id}")
    public LessonDetail updateLesson(@PathVariable long id, @RequestBody LessonForm form) {
        return admin.updateLesson(id, form);
    }

    @PostMapping("/lessons/{id}/move")
    public LessonDetail moveLesson(@PathVariable long id, @RequestParam int delta) {
        return admin.moveLesson(id, delta);
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(@PathVariable long id) {
        admin.deleteLesson(id);
    }

    // Questions
    @GetMapping("/questions")
    public PageResponse<QuestionRow> questions(@RequestParam(required = false) Long course,
            @RequestParam(required = false) Long chapter, @RequestParam(required = false) Long lesson,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page) {
        return admin.questions(course, chapter, lesson, search, page);
    }

    @GetMapping("/questions/{id}")
    public QuestionDetail question(@PathVariable long id) {
        return admin.question(id);
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDetail createQuestion(@RequestBody QuestionForm form) {
        return admin.saveQuestion(null, form);
    }

    @PutMapping("/questions/{id}")
    public QuestionDetail updateQuestion(@PathVariable long id, @RequestBody QuestionForm form) {
        return admin.saveQuestion(id, form);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable long id) {
        admin.deleteQuestion(id);
    }

    // Exercices
    @GetMapping("/exercises")
    public List<ExerciseRow> exercises(@RequestParam(required = false) Long course,
            @RequestParam(required = false) String search) {
        return admin.exerciseList(course, search);
    }

    @GetMapping("/exercises/{id}")
    public ExerciseDetail exercise(@PathVariable long id) {
        return admin.exercise(id);
    }

    @PostMapping("/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseSaved createExercise(@RequestBody ExerciseForm form) {
        return admin.saveExercise(null, form, CurrentUser.id());
    }

    @PutMapping("/exercises/{id}")
    public ExerciseSaved updateExercise(@PathVariable long id, @RequestBody ExerciseForm form) {
        return admin.saveExercise(id, form, CurrentUser.id());
    }

    @PostMapping("/exercises/{id}/check")
    public CheckResult check(@PathVariable long id) {
        return admin.check(id, CurrentUser.id());
    }

    @DeleteMapping("/exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable long id) {
        admin.deleteExercise(id);
    }
}
