package fr.cdaacademy.lab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import fr.cdaacademy.lab.LabExecutor.LabSqlException;
import fr.cdaacademy.lab.LabExecutor.StatementResult;

/** Laboratoire SQL libre : exécution de scripts et description de la base pédagogique. */
@Service
public class LabService {

    public record LabError(int statementIndex, String statement, String message, String hint, String sqlState) {
    }

    public record RunResponse(List<StatementResult> results, LabError error) {
    }

    public record Column(String name, String type, boolean nullable, boolean primaryKey, String references) {
    }

    public record Table(String name, long rowCount, List<Column> columns) {
    }

    private final LabExecutor executor;
    private final LabRateLimiter limiter;
    private final JdbcTemplate jdbc;
    private volatile List<Table> schemaCache;

    public LabService(LabExecutor executor, LabRateLimiter limiter, JdbcTemplate jdbc) {
        this.executor = executor;
        this.limiter = limiter;
        this.jdbc = jdbc;
    }

    /** Exécute les instructions dans l'ordre et s'arrête à la première erreur. Rien n'est conservé. */
    public RunResponse run(Long userId, String sql) {
        limiter.acquire(userId);
        List<String> statements = SqlGuard.check(sql, SqlGuard.Mode.WRITE);
        return executor.withSession(false, session -> {
            List<StatementResult> results = new ArrayList<>();
            for (int i = 0; i < statements.size(); i++) {
                try {
                    results.add(session.run(statements.get(i)));
                } catch (LabSqlException e) {
                    return new RunResponse(results,
                            new LabError(i, e.statement(), e.getMessage(), e.hint(), e.sqlState()));
                }
            }
            return new RunResponse(results, null);
        });
    }

    public List<Table> schema() {
        List<Table> cached = schemaCache;
        if (cached == null) {
            cached = loadSchema();
            schemaCache = cached;
        }
        return cached;
    }

    private List<Table> loadSchema() {
        Map<String, List<Column>> columns = new LinkedHashMap<>();
        jdbc.query("""
                select c.table_name, c.column_name, c.data_type, c.character_maximum_length, c.numeric_precision,
                       c.numeric_scale, c.is_nullable = 'YES' as nullable,
                       exists (select 1 from information_schema.table_constraints tc
                               join information_schema.key_column_usage k
                                 on k.constraint_name = tc.constraint_name and k.table_schema = tc.table_schema
                               where tc.table_schema = 'lab' and tc.table_name = c.table_name
                                 and tc.constraint_type = 'PRIMARY KEY' and k.column_name = c.column_name) as pk,
                       (select ccu.table_name || '.' || ccu.column_name
                          from information_schema.table_constraints tc
                          join information_schema.key_column_usage k
                            on k.constraint_name = tc.constraint_name and k.table_schema = tc.table_schema
                          join information_schema.constraint_column_usage ccu
                            on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema
                         where tc.table_schema = 'lab' and tc.table_name = c.table_name
                           and tc.constraint_type = 'FOREIGN KEY' and k.column_name = c.column_name
                         limit 1) as fk
                from information_schema.columns c
                join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name
                where c.table_schema = 'lab' and t.table_type = 'BASE TABLE'
                order by c.table_name, c.ordinal_position
                """, rs -> {
            String type = rs.getString("data_type");
            if (rs.getObject("character_maximum_length") != null) {
                type = "varchar(" + rs.getInt("character_maximum_length") + ")";
            } else if ("numeric".equals(type) && rs.getObject("numeric_precision") != null) {
                type = "numeric(" + rs.getInt("numeric_precision") + ", " + rs.getInt("numeric_scale") + ")";
            }
            columns.computeIfAbsent(rs.getString("table_name"), k -> new ArrayList<>())
                    .add(new Column(rs.getString("column_name"), type, rs.getBoolean("nullable"),
                            rs.getBoolean("pk"), rs.getString("fk")));
        });
        List<Table> tables = new ArrayList<>();
        for (String name : LabMaintenance.TABLES) {
            if (columns.containsKey(name)) {
                Long count = jdbc.queryForObject("select count(*) from lab." + name, Long.class);
                tables.add(new Table(name, count == null ? 0 : count, columns.get(name)));
            }
        }
        return tables;
    }
}
