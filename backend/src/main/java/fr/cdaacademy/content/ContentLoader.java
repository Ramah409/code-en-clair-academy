package fr.cdaacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import fr.cdaacademy.content.ContentFiles.ChapterFile;
import fr.cdaacademy.content.ContentFiles.CourseFile;
import fr.cdaacademy.content.ContentFiles.ExerciseDef;
import fr.cdaacademy.content.ContentFiles.LessonDef;
import fr.cdaacademy.content.ContentFiles.QuestionDef;

/**
 * Lecture et validation des fichiers de contenu. Toute incohérence (référence inconnue,
 * question sans bonne réponse, indice manquant...) est signalée avec le fichier en cause.
 */
public class ContentLoader {

    public record CourseBundle(Path directory, CourseFile course, List<ChapterFile> chapters) {
    }

    static final Set<String> QUESTION_KINDS = Set.of(
            "CHOIX_UNIQUE", "CHOIX_MULTIPLE", "VRAI_FAUX", "TEXTE", "COMPLETER_CODE", "RESULTAT_CODE");
    static final Set<String> SINGLE_ANSWER_KINDS = Set.of("CHOIX_UNIQUE", "VRAI_FAUX", "RESULTAT_CODE");
    static final Set<String> TEXT_KINDS = Set.of("TEXTE", "COMPLETER_CODE");
    static final Set<String> LEVELS = Set.of("DEBUTANT", "INTERMEDIAIRE", "AVANCE", "EXAMEN");
    static final Set<String> BLOCK_TYPES = Set.of(
            "text", "definition", "code", "demo", "callout", "question", "exercise", "steps", "compare");

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /** Lit tous les parcours présents dans {@code <racine>/parcours}. */
    public List<CourseBundle> loadAll(Path root) {
        Path dir = root.resolve("parcours");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> courses = Files.list(dir)) {
            List<CourseBundle> bundles = new ArrayList<>();
            for (Path courseDir : courses.filter(Files::isDirectory).sorted().toList()) {
                bundles.add(load(courseDir));
            }
            return bundles;
        } catch (IOException e) {
            throw new ContentException("Lecture impossible du dossier " + dir, e);
        }
    }

    public CourseBundle load(Path courseDir) {
        CourseFile course = read(courseDir.resolve("parcours.yml"), CourseFile.class);
        List<ChapterFile> chapters = new ArrayList<>();
        Path chaptersDir = courseDir.resolve("chapitres");
        if (Files.isDirectory(chaptersDir)) {
            try (Stream<Path> files = Files.list(chaptersDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".yml")).sorted().toList()) {
                    ChapterFile chapter = read(file, ChapterFile.class);
                    validate(chapter, file);
                    chapters.add(chapter);
                }
            } catch (IOException e) {
                throw new ContentException("Lecture impossible du dossier " + chaptersDir, e);
            }
        }
        chapters.sort(Comparator.comparingInt(ChapterFile::position));
        require(course.slug() != null && course.title() != null, courseDir, "slug et title obligatoires");
        return new CourseBundle(courseDir, course, chapters);
    }

    private <T> T read(Path file, Class<T> type) {
        try {
            return yaml.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new ContentException("Fichier de contenu invalide : " + file + " — " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ validation

    private void validate(ChapterFile chapter, Path file) {
        require(LEVELS.contains(chapter.level()), file, "niveau inconnu : " + chapter.level());
        Set<String> questionCodes = new HashSet<>();
        Set<String> exerciseSlugs = new HashSet<>();
        int bankSize = 0;
        for (LessonDef lesson : nullSafe(chapter.lessons())) {
            require(lesson.slug() != null && lesson.title() != null, file, "leçon sans slug ou titre");
            require(lesson.duration() >= 5 && lesson.duration() <= 15, file,
                    "durée de la leçon " + lesson.slug() + " hors de [5, 15] minutes");
            Set<String> lessonQuestions = new HashSet<>();
            Set<String> lessonExercises = new HashSet<>();
            for (QuestionDef q : nullSafe(lesson.questions())) {
                validate(q, file);
                require(questionCodes.add(q.code()), file, "code de question en double : " + q.code());
                lessonQuestions.add(q.code());
                bankSize++;
            }
            for (ExerciseDef ex : nullSafe(lesson.exercises())) {
                validate(ex, file);
                require(exerciseSlugs.add(ex.slug()), file, "exercice en double : " + ex.slug());
                lessonExercises.add(ex.slug());
            }
            for (JsonNode block : nullSafe(lesson.blocks())) {
                String type = block.path("type").asText();
                require(BLOCK_TYPES.contains(type), file, "type de bloc inconnu dans " + lesson.slug() + " : " + type);
                if (type.equals("question")) {
                    require(lessonQuestions.contains(block.path("ref").asText()), file,
                            "la leçon " + lesson.slug() + " référence une question absente : " + block.path("ref"));
                }
                if (type.equals("exercise")) {
                    require(lessonExercises.contains(block.path("ref").asText()), file,
                            "la leçon " + lesson.slug() + " référence un exercice absent : " + block.path("ref"));
                }
            }
        }
        for (ExerciseDef ex : nullSafe(chapter.exercises())) {
            validate(ex, file);
            require(exerciseSlugs.add(ex.slug()), file, "exercice en double : " + ex.slug());
        }
        for (QuestionDef q : nullSafe(chapter.bank())) {
            validate(q, file);
            require(questionCodes.add(q.code()), file, "code de question en double : " + q.code());
            bankSize++;
        }
        int quizSize = chapter.quizQuestionCount() == null ? 15 : chapter.quizQuestionCount();
        require(bankSize >= quizSize, file, "banque de " + bankSize + " questions, insuffisante pour un QCM de "
                + quizSize + " questions");
    }

    private void validate(QuestionDef q, Path file) {
        String where = "question " + q.code();
        require(q.code() != null && q.prompt() != null && q.explanation() != null, file,
                where + " : code, prompt et explanation obligatoires");
        require(QUESTION_KINDS.contains(q.kind()), file, where + " : type inconnu " + q.kind());
        require(q.difficulty() == null || (q.difficulty() >= 1 && q.difficulty() <= 4), file,
                where + " : difficulté entre 1 et 4");
        if (TEXT_KINDS.contains(q.kind())) {
            require(q.answers() != null && !q.answers().isEmpty(), file, where + " : answers obligatoire");
        } else {
            var choices = nullSafe(q.choices());
            require(choices.size() >= 2, file, where + " : au moins deux choix");
            long correct = choices.stream().filter(ContentFiles.ChoiceDef::correct).count();
            require(correct >= 1, file, where + " : aucune bonne réponse");
            require(!SINGLE_ANSWER_KINDS.contains(q.kind()) || correct == 1, file,
                    where + " : une seule bonne réponse attendue");
            require(choices.stream().allMatch(c -> c.label() != null && c.why() != null), file,
                    where + " : chaque choix doit avoir label et why (explication)");
        }
    }

    private void validate(ExerciseDef ex, Path file) {
        String where = "exercice " + ex.slug();
        require(ex.slug() != null && ex.title() != null && ex.statement() != null, file,
                where + " : slug, title et statement obligatoires");
        require(ex.hints() != null && ex.hints().size() == 3, file, where + " : exactement trois indices graduels");
        require(ex.solution() != null && ex.explanation() != null, file, where + " : solution et explication");
        require(ex.payload() != null && ex.payload().isObject(), file, where + " : payload obligatoire");
        if ("MCD".equals(ex.kind()) || "MCD_VERS_MLD".equals(ex.kind())) {
            require(ex.payload().has("expected") && ex.payload().has("solutionModel"), file,
                    where + " : un exercice de modélisation demande payload.expected et payload.solutionModel");
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void require(boolean condition, Path file, String message) {
        if (!condition) {
            throw new ContentException(file + " : " + message);
        }
    }

    public static class ContentException extends RuntimeException {
        public ContentException(String message) {
            super(message);
        }

        public ContentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
