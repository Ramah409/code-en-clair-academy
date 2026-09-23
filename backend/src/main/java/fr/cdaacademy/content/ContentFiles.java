package fr.cdaacademy.content;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Structure des fichiers YAML de contenu pédagogique (database/content/parcours/&lt;parcours&gt;/).
 * <ul>
 *   <li>{@code parcours.yml} : description du parcours, examen final et projet ;</li>
 *   <li>{@code chapitres/NN-*.yml} : un chapitre, ses leçons, questions, exercices et banque de QCM.</li>
 * </ul>
 */
public final class ContentFiles {

    private ContentFiles() {
    }

    public record CourseFile(
            String slug,
            int version,
            int position,
            String title,
            String category,
            String icon,
            String summary,
            String description,
            Integer examQuestionCount,
            Integer examTimeLimitMinutes,
            Boolean mandatory,
            ProjectDef project) {

        /** Un parcours compte pour la certification globale sauf mention contraire. */
        public boolean isMandatory() {
            return mandatory == null || mandatory;
        }
    }

    /**
     * Examen de l'espace « Examen CDA » ({@code examens/*.yml}) :
     * <ul>
     *   <li>EXAMEN_BLANC : QCM chronométré tiré de la banque de questions (éventuellement limitée à des parcours) ;</li>
     *   <li>ETUDE_DE_CAS : mise en situation (contexte, documents, tâches, corrigé et grille d'auto-évaluation) ;</li>
     *   <li>ORAL : questions du jury avec réponses modèles.</li>
     * </ul>
     */
    public record ExamFile(
            String slug,
            int version,
            int position,
            String kind,
            String level,
            String title,
            String description,
            int durationMinutes,
            Integer passingScore,
            Integer questionCount,
            Boolean finalExam,
            List<String> courses,
            JsonNode content) {
    }

    public record ChapterFile(
            String slug,
            int position,
            String level,
            String title,
            String summary,
            Integer quizQuestionCount,
            List<LessonDef> lessons,
            List<ExerciseDef> exercises,
            List<QuestionDef> bank) {
    }

    public record LessonDef(
            String slug,
            String title,
            int duration,
            String objective,
            String prerequisites,
            List<String> skills,
            String memo,
            String commonMistakes,
            Integer xp,
            List<JsonNode> blocks,
            List<QuestionDef> questions,
            List<ExerciseDef> exercises) {
    }

    /** Question de QCM. {@code answers} : réponses acceptées pour COMPLETER_CODE et TEXTE. */
    public record QuestionDef(
            String code,
            String kind,
            Integer difficulty,
            String skill,
            String prompt,
            String snippet,
            String language,
            List<ChoiceDef> choices,
            List<String> answers,
            String explanation) {
    }

    public record ChoiceDef(String label, boolean correct, String why) {
    }

    public record ExerciseDef(
            String slug,
            String kind,
            String title,
            String difficulty,
            String skill,
            String statement,
            String criteria,
            List<String> hints,
            String solution,
            String explanation,
            Integer xp,
            JsonNode payload) {
    }

    public record ProjectDef(
            String slug,
            String title,
            String difficulty,
            String statement,
            String needs,
            String businessRules,
            String userStories,
            String mockup,
            String mcd,
            String mld,
            String acceptanceCriteria,
            String tests,
            String correction,
            String improvements,
            List<ProjectStepDef> steps) {
    }

    public record ProjectStepDef(
            String title,
            String instructions,
            String deliverable,
            List<String> checklist,
            String exercise) {
    }
}
