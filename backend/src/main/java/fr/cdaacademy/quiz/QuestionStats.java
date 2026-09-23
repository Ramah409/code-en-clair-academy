package fr.cdaacademy.quiz;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Historique des réponses par question. Une question ratée reste dans « Mes erreurs »
 * jusqu'à ce qu'elle soit réussie deux fois de suite.
 */
@Component
public class QuestionStats {

    /** Nombre de bonnes réponses consécutives pour considérer une erreur comme corrigée. */
    public static final int MASTERY_STREAK = 2;

    private final JdbcTemplate jdbc;

    public QuestionStats(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true si c'est la toute première bonne réponse de l'apprenante à cette question */
    public boolean record(long userId, long questionId, boolean correct) {
        Integer previousCorrect = jdbc.query(
                "select times_correct from user_question_stats where user_id = ? and question_id = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, userId, questionId);
        jdbc.update("""
                insert into user_question_stats (user_id, question_id, times_answered, times_correct, times_wrong,
                                                 consecutive_correct, last_answered_at, last_wrong_at)
                values (?, ?, 1, ?, ?, ?, now(), case when ? then null else now() end)
                on conflict (user_id, question_id) do update set
                    times_answered = user_question_stats.times_answered + 1,
                    times_correct = user_question_stats.times_correct + excluded.times_correct,
                    times_wrong = user_question_stats.times_wrong + excluded.times_wrong,
                    consecutive_correct = case when ? then user_question_stats.consecutive_correct + 1 else 0 end,
                    last_answered_at = now(),
                    last_wrong_at = coalesce(excluded.last_wrong_at, user_question_stats.last_wrong_at)
                """, userId, questionId, correct ? 1 : 0, correct ? 0 : 1, correct ? 1 : 0, correct, correct);
        return correct && (previousCorrect == null || previousCorrect == 0);
    }
}
