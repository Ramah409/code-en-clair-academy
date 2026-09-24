package fr.cdaacademy.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import fr.cdaacademy.PostgresIntegrationTest;

class DemoAccountsGuardIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;

    private String hash(String email) {
        return jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
    }

    /** Les autres tests utilisent les comptes de démonstration : on remet les empreintes d'origine. */
    @AfterEach
    void restaurer() {
        DemoAccountsGuard.PUBLIC_HASHES.forEach((email, publicHash) ->
                jdbc.update("update users set password_hash = ? where email = ?", publicHash, email));
    }

    @Test
    void remplaceLesMotsDePassePublicsEnProduction() {
        new DemoAccountsGuard(jdbc, passwordEncoder, "Admin-Production-Solide-42").run(null);

        String admin = hash(DemoAccountsGuard.ADMIN_EMAIL);
        String learner = hash(DemoAccountsGuard.LEARNER_EMAIL);
        assertThat(passwordEncoder.matches("Formatrice-2026!", admin)).isFalse();
        assertThat(passwordEncoder.matches("Admin-Production-Solide-42", admin)).isTrue();
        assertThat(passwordEncoder.matches("Apprenante-2026!", learner)).isFalse();
    }

    @Test
    void sansMotDePasseAdministrateurLeCompteDevientInutilisable() {
        new DemoAccountsGuard(jdbc, passwordEncoder, "").run(null);

        assertThat(passwordEncoder.matches("Formatrice-2026!", hash(DemoAccountsGuard.ADMIN_EMAIL))).isFalse();
        assertThat(hash(DemoAccountsGuard.ADMIN_EMAIL)).isNotEqualTo(DemoAccountsGuard.PUBLIC_HASHES.get(DemoAccountsGuard.ADMIN_EMAIL));
    }

    @Test
    void neTouchePasUnMotDePasseDejaChange() {
        String personnel = passwordEncoder.encode("Deja-Change-Par-La-Formatrice");
        jdbc.update("update users set password_hash = ? where email = ?", personnel, DemoAccountsGuard.ADMIN_EMAIL);

        new DemoAccountsGuard(jdbc, passwordEncoder, "Admin-Production-Solide-42").run(null);

        assertThat(hash(DemoAccountsGuard.ADMIN_EMAIL)).isEqualTo(personnel);
    }
}
