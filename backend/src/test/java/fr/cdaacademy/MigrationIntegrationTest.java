package fr.cdaacademy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void toutesLesTablesDuModeleExistent() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains("users", "roles", "courses", "modules", "lessons", "exercises", "questions",
                "choices", "attempts", "answers", "progress", "skills", "user_skills", "badges", "user_badges",
                "revisions", "projects", "project_steps", "exams", "exam_questions", "exam_attempts",
                "mcd_diagrams", "tutor_conversations", "tutor_messages", "refresh_tokens", "daily_activity");
    }

    @Test
    void lesDonneesDeReferenceSontChargees() {
        assertThat(jdbc.queryForObject("select count(*) from roles", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from skills", Integer.class)).isGreaterThanOrEqualTo(40);
        assertThat(jdbc.queryForObject("select count(*) from badges", Integer.class)).isGreaterThanOrEqualTo(10);
    }

    @Test
    void lUniciteDeLEmailIgnoreLaCasse() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into users (email, display_name, password_hash, role_id)
                values ('APPRENANTE@cda-academy.local', 'Doublon', 'x', (select id from roles where code = 'USER'))
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uneLeconDoitDurerEntre5Et15Minutes() {
        jdbc.update("insert into courses (slug, level_number, title, summary, description, category) "
                + "values ('test-duree', 20, 'T', 'S', 'D', 'WEB')");
        jdbc.update("insert into modules (course_id, slug, title, summary, position) "
                + "values ((select id from courses where slug = 'test-duree'), 'm', 'M', 'S', 1)");
        assertThatThrownBy(() -> jdbc.update("""
                insert into lessons (module_id, slug, title, position, duration_minutes, prerequisites, objective,
                    course_content, example, demonstration, common_mistakes, memo, challenge)
                values ((select id from modules where slug = 'm'), 'trop-longue', 'L', 1, 45,
                    'p', 'o', 'c', 'e', 'd', 'm', 'm', 'c')
                """)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from courses where slug = 'test-duree'");
    }
}
