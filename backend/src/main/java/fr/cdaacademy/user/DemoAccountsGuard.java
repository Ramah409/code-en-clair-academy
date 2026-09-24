package fr.cdaacademy.user;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Production uniquement : les comptes de démonstration créés par la migration V2 ont des mots de
 * passe publiés dans le dépôt. Tant qu'un compte porte encore l'empreinte d'origine, il est protégé
 * au démarrage :
 * <ul>
 *   <li>la formatrice (administratrice) reçoit le mot de passe de la variable ADMIN_PASSWORD ;</li>
 *   <li>l'apprenante de démonstration reçoit un mot de passe aléatoire que personne ne connaît.</li>
 * </ul>
 * Un mot de passe déjà changé n'est jamais touché.
 */
@Component
@Profile("prod")
@Order(0)
public class DemoAccountsGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountsGuard.class);

    static final String ADMIN_EMAIL = "formatrice@cda-academy.local";
    static final String LEARNER_EMAIL = "apprenante@cda-academy.local";

    /** Empreintes insérées par V2__donnees_de_reference.sql (mots de passe publics). */
    static final Map<String, String> PUBLIC_HASHES = Map.of(
            LEARNER_EMAIL, "$2a$10$QwzDGBmHSIK3BzvVb5gVIuLGCPv1beW2hduliVWXBoBR.UqrUChG.",
            ADMIN_EMAIL, "$2a$10$Oz7K766.hrrB0ibstQLCrOvs9C9ONWvdhJrcHYSM9mNkScjA7UXRa");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DemoAccountsGuard(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                             @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        PUBLIC_HASHES.forEach((email, publicHash) -> {
            String replacement = ADMIN_EMAIL.equals(email) && adminPassword.length() >= 12
                    ? adminPassword
                    : randomPassword();
            int updated = jdbc.update("update users set password_hash = ? where email = ? and password_hash = ?",
                    passwordEncoder.encode(replacement), email, publicHash);
            if (updated > 0) {
                log.info("Compte de démonstration {} protégé : mot de passe public remplacé", email);
            }
        });
    }

    private static String randomPassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
