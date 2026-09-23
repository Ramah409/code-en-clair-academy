package fr.cdaacademy.lab;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.sql.DataSource;

import org.postgresql.core.BaseConnection;
import org.postgresql.core.Parser;
import org.postgresql.core.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import fr.cdaacademy.common.BusinessRuleException;
import fr.cdaacademy.config.AppProperties;
import jakarta.annotation.PreDestroy;

/**
 * Exécute du SQL d'apprenante dans la base pédagogique, avec le rôle restreint cda_lab.
 *
 * Chaque session ouvre une transaction qui est systématiquement annulée : les INSERT,
 * UPDATE, DELETE et CREATE TABLE sont visibles pendant l'exécution puis disparaissent.
 */
@Component
public class LabExecutor {

    private static final Logger log = LoggerFactory.getLogger(LabExecutor.class);

    public record ResultData(List<String> columns, List<List<String>> rows, boolean truncated) {
    }

    /** Résultat d'une instruction : un tableau (requête) ou un nombre de lignes affectées. */
    public record StatementResult(String statement, String command, ResultData data, Integer updateCount,
            long durationMs) {
    }

    /** Session de travail : toutes les instructions partagent la même transaction annulée à la fin. */
    public interface Session {
        StatementResult run(String sql);
    }

    private final DataSource appDataSource;
    private final AppProperties.Lab props;
    private volatile HikariDataSource labDataSource;

    public LabExecutor(DataSource appDataSource, AppProperties props) {
        this.appDataSource = appDataSource;
        this.props = props.lab();
    }

    public int maxRows() {
        return props.maxRows();
    }

    /**
     * Ouvre une connexion du laboratoire, exécute le travail puis annule la transaction.
     * @param readOnly transaction en lecture seule (exercices de SELECT)
     */
    public <T> T withSession(boolean readOnly, Function<Session, T> work) {
        try (Connection con = dataSource().getConnection()) {
            con.setAutoCommit(false);
            con.setReadOnly(readOnly);
            try {
                return work.apply(sql -> execute(con, sql));
            } finally {
                safeRollback(con);
            }
        } catch (SQLException e) {
            if ("08001".equals(e.getSQLState()) || "08004".equals(e.getSQLState())
                    || e.getMessage().contains("Connection is not available")) {
                throw new BusinessRuleException(
                        "Le laboratoire est très sollicité en ce moment. Réessaie dans quelques secondes.");
            }
            log.warn("Connexion au laboratoire impossible : {}", e.getMessage());
            throw new BusinessRuleException("Le laboratoire SQL est indisponible pour le moment.");
        }
    }

    private StatementResult execute(Connection con, String sql) {
        ensureSingleStatement(sql);
        long start = System.nanoTime();
        try (Statement st = con.createStatement()) {
            st.setQueryTimeout(props.timeoutSeconds());
            st.setMaxRows(props.maxRows() + 1);
            st.setFetchSize(100);
            boolean hasResultSet = st.execute(sql);
            ensureTransactionStillOpen(con);
            String command = SqlGuard.firstKeyword(SqlGuard.stripLiterals(sql));
            if (hasResultSet) {
                try (ResultSet rs = st.getResultSet()) {
                    return new StatementResult(sql, command, read(rs), null, elapsed(start));
                }
            }
            return new StatementResult(sql, command, null, st.getUpdateCount(), elapsed(start));
        } catch (SQLException e) {
            throw new LabSqlException(sql, e);
        }
    }

    private ResultData read(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        List<String> columns = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= props.maxRows()) {
                truncated = true;
                break;
            }
            List<String> row = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                row.add(format(rs, i, meta.getColumnType(i)));
            }
            rows.add(row);
        }
        return new ResultData(columns, rows, truncated);
    }

    private static String format(ResultSet rs, int index, int type) throws SQLException {
        if (type == Types.BOOLEAN || type == Types.BIT) {
            boolean value = rs.getBoolean(index);
            return rs.wasNull() ? null : Boolean.toString(value);
        }
        return rs.getString(index);
    }

    /**
     * Double contrôle avec l'analyseur du pilote PostgreSQL : c'est lui qui découpe réellement
     * le texte envoyé au serveur. S'il y voit plusieurs instructions, l'exécution est refusée.
     */
    private static void ensureSingleStatement(String sql) {
        try {
            if (Parser.parseJdbcSql(sql, true, false, true, false, false).size() > 1) {
                throw new BusinessRuleException("Instruction SQL ambiguë : sépare tes instructions par des « ; ».");
            }
        } catch (SQLException e) {
            throw new BusinessRuleException("Instruction SQL illisible : " + e.getMessage());
        }
    }

    /** Une instruction ne doit jamais pouvoir terminer la transaction englobante. */
    private static void ensureTransactionStillOpen(Connection con) throws SQLException {
        TransactionState state = con.unwrap(BaseConnection.class).getTransactionState();
        if (state == TransactionState.IDLE) {
            log.error("Une instruction du laboratoire a terminé la transaction : exécution interrompue");
            throw new BusinessRuleException("Instruction refusée par le laboratoire.");
        }
    }

    private static void safeRollback(Connection con) {
        try {
            con.rollback();
        } catch (SQLException e) {
            log.debug("Annulation de la transaction du laboratoire : {}", e.getMessage());
        }
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private HikariDataSource dataSource() {
        HikariDataSource ds = labDataSource;
        if (ds == null) {
            synchronized (this) {
                if (labDataSource == null) {
                    labDataSource = create();
                }
                ds = labDataSource;
            }
        }
        return ds;
    }

    private HikariDataSource create() {
        String url;
        try {
            url = appDataSource.unwrap(HikariDataSource.class).getJdbcUrl();
        } catch (SQLException e) {
            throw new IllegalStateException("URL de la base introuvable pour le laboratoire", e);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("laboratoire-sql");
        config.setJdbcUrl(url);
        config.setUsername(props.username());
        config.setPassword(props.password());
        config.setMaximumPoolSize(props.poolSize());
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setIdleTimeout(60_000);
        config.setAutoCommit(false);
        config.addDataSourceProperty("ApplicationName", "cda-academy-laboratoire");
        return new HikariDataSource(config);
    }

    @PreDestroy
    void close() {
        if (labDataSource != null) {
            labDataSource.close();
        }
    }

    /** Erreur SQL renvoyée à l'apprenante, accompagnée d'une explication en français. */
    public static class LabSqlException extends RuntimeException {
        private final String statement;
        private final String sqlState;
        private final String hint;

        LabSqlException(String statement, SQLException cause) {
            super(cleanMessage(cause.getMessage()), cause);
            this.statement = statement;
            this.sqlState = cause.getSQLState();
            this.hint = SqlErrorHints.explain(cause.getSQLState(), cause.getMessage());
        }

        public String statement() {
            return statement;
        }

        public String sqlState() {
            return sqlState;
        }

        public String hint() {
            return hint;
        }

        private static String cleanMessage(String message) {
            if (message == null) {
                return "Erreur SQL";
            }
            String first = message.strip().lines().findFirst().orElse(message).strip();
            return first.startsWith("ERROR: ") ? first.substring(7) : first;
        }
    }
}
