package fr.cdaacademy.lab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import fr.cdaacademy.PostgresIntegrationTest;

class LabIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    LabService lab;
    @Autowired
    JdbcTemplate jdbc;

    private long userId() {
        return jdbc.queryForObject("select id from users where email = 'apprenante@cda-academy.local'", Long.class);
    }

    @Test
    void executeUneRequeteSurLaBasePedagogique() {
        var run = lab.run(userId(), "SELECT nom FROM categories ORDER BY id");
        assertThat(run.error()).isNull();
        assertThat(run.results().getFirst().data().rows()).hasSize(8);
    }

    @Test
    void lesTablesDeLApplicationSontInaccessibles() {
        var run = lab.run(userId(), "SELECT email, password_hash FROM public.users");
        assertThat(run.error()).isNotNull();
        assertThat(run.error().sqlState()).isEqualTo("42501");
    }

    @Test
    void lesModificationsSontToujoursAnnulees() {
        var run = lab.run(userId(), """
                DELETE FROM lignes_commande;
                UPDATE produits SET prix = 0;
                CREATE TABLE essai (id int PRIMARY KEY);
                SELECT count(*) FROM lignes_commande
                """);
        assertThat(run.error()).isNull();
        assertThat(run.results().getLast().data().rows().getFirst().getFirst()).isEqualTo("0");

        assertThat(jdbc.queryForObject("select count(*) from lab.lignes_commande", Integer.class)).isGreaterThan(50);
        assertThat(jdbc.queryForObject("select min(prix) from lab.produits", java.math.BigDecimal.class))
                .isPositive();
        assertThat(jdbc.queryForObject("select to_regclass('lab.essai') is null", Boolean.class)).isTrue();
    }

    @Test
    void uneRequeteTropLongueEstInterrompue() {
        var run = lab.run(userId(), "SELECT count(*) FROM generate_series(1, 500000000)");
        assertThat(run.error()).isNotNull();
        assertThat(run.error().sqlState()).isEqualTo("57014");
        assertThat(run.error().hint()).contains("temps maximal");
    }

    @Test
    void leSchemaDecritLesTablesEtLeursCles() {
        var produits = lab.schema().stream().filter(t -> t.name().equals("produits")).findFirst().orElseThrow();
        assertThat(produits.rowCount()).isEqualTo(30);
        assertThat(produits.columns()).anyMatch(c -> c.name().equals("id") && c.primaryKey());
        assertThat(produits.columns()).anyMatch(c -> c.name().equals("categorie_id")
                && "categories.id".equals(c.references()));
    }
}
