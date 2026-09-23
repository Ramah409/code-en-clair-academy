package fr.cdaacademy.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.lab.SqlGuard.Mode;

class SqlGuardTest {

    @Test
    void decoupeLesInstructionsHorsChainesEtCommentaires() {
        var statements = SqlGuard.check("""
                -- commentaire ; ignoré
                SELECT 'a;b' AS texte; /* bloc ; */ SELECT "col;onne" FROM produits;
                SELECT $$x;y$$
                """, Mode.READ);
        assertThat(statements).hasSize(3);
        assertThat(statements.get(0)).isEqualTo("SELECT 'a;b' AS texte");
    }

    @Test
    void refuseLesCommandesDeTransactionEtDeSession() {
        for (String sql : new String[] {"COMMIT", "ROLLBACK", "BEGIN", "SET ROLE postgres", "RESET ROLE",
                "COPY clients TO STDOUT", "DO $$ BEGIN END $$", "GRANT ALL ON clients TO public",
                "CREATE TEMP TABLE t (x int)", "CREATE FUNCTION f() RETURNS int AS $$ SELECT 1 $$ LANGUAGE sql",
                "CREATE ROLE pirate"}) {
            assertThatThrownBy(() -> SqlGuard.check(sql, Mode.WRITE)).as(sql)
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    void unCommitCacheDansUneChaineEchappeeEstDetecte() {
        assertThatThrownBy(() -> SqlGuard.check("SELECT E'\\''; COMMIT; SELECT ''", Mode.WRITE))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unDollarDansUnIdentifiantNOuvrePasDeChaine() {
        assertThatThrownBy(() -> SqlGuard.check("SELECT 1 AS x$a$; COMMIT; SELECT 1 AS y$a$", Mode.WRITE))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refuseLesFonctionsSensiblesMemeEntreGuillemets() {
        assertThatThrownBy(() -> SqlGuard.check("SELECT \"set_config\"('statement_timeout', '0', false)", Mode.READ))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> SqlGuard.check("SELECT pg_sleep(5)", Mode.READ))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void leModeLectureRefuseLesModifications() {
        assertThatThrownBy(() -> SqlGuard.check("DELETE FROM clients", Mode.READ))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SELECT");
        assertThat(SqlGuard.check("DELETE FROM clients WHERE id = 1", Mode.WRITE)).hasSize(1);
    }

    @Test
    void accepteUneColonneNommeeRoleDansUneCreationDeTable() {
        assertThat(SqlGuard.check("CREATE TABLE comptes (id int PRIMARY KEY, role varchar(20))", Mode.WRITE))
                .hasSize(1);
    }
}
