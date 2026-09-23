package fr.cdaacademy.lab;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remet la base pédagogique dans son état de référence : supprime les objets créés par le
 * rôle du laboratoire et recopie les données depuis le schéma lab_seed. Exécuté à chaque
 * démarrage (filet de sécurité) et depuis l'espace d'administration.
 */
@Component
@Order(5)
public class LabMaintenance implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LabMaintenance.class);

    /** Ordre de rechargement compatible avec les clés étrangères. */
    static final List<String> TABLES = List.of(
            "categories", "produits", "clients", "commandes", "lignes_commande", "avis", "services", "employes");

    private final JdbcTemplate jdbc;

    public LabMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            restore();
        } catch (RuntimeException e) {
            log.error("Restauration du laboratoire impossible : {}", e.getMessage());
        }
    }

    @Transactional
    public void restore() {
        List<String> foreignObjects = jdbc.queryForList("""
                select format('drop %s if exists lab.%I cascade',
                              case c.relkind when 'v' then 'view' when 'm' then 'materialized view'
                                             when 'S' then 'sequence' when 'i' then 'index' else 'table' end,
                              c.relname)
                from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'lab' and c.relkind in ('r', 'v', 'm', 'S', 'i', 'p')
                  and pg_get_userbyid(c.relowner) <> current_user
                """, String.class);
        foreignObjects.forEach(jdbc::execute);

        jdbc.execute("truncate " + String.join(", ", TABLES.stream().map(t -> "lab." + t).toList()));
        for (String table : TABLES) {
            jdbc.execute("insert into lab." + table + " select * from lab_seed." + table);
        }
        jdbc.execute("grant select, insert, update, delete on all tables in schema lab to cda_lab");
        if (!foreignObjects.isEmpty()) {
            log.warn("Laboratoire : {} objet(s) créé(s) par les apprenantes supprimé(s)", foreignObjects.size());
        }
    }
}
