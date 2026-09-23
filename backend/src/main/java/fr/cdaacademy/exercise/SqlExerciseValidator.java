package fr.cdaacademy.exercise;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.cdaacademy.lab.LabExecutor;
import fr.cdaacademy.lab.LabExecutor.LabSqlException;
import fr.cdaacademy.lab.LabExecutor.ResultData;
import fr.cdaacademy.lab.LabService.LabError;
import fr.cdaacademy.lab.SqlGuard;

/**
 * Exercices SQL corrigés par exécution réelle dans la base pédagogique.
 *
 * La requête de l'apprenante et la requête de référence sont exécutées chacune dans une
 * transaction annulée, puis leurs résultats sont comparés (colonnes, lignes, ordre si demandé).
 * Pour les exercices de modification (INSERT, UPDATE, DELETE, CREATE), une requête de
 * vérification est exécutée après les instructions, dans la même transaction.
 *
 * Payload : { mode: READ|WRITE, ordered: bool, check: "SELECT…", starter: "…",
 *             requiredKeywords: [{pattern, message}] }
 */
@Component
public class SqlExerciseValidator implements ExerciseValidator {

    /** Résultat obtenu et résultat attendu, affichés côte à côte. */
    public record SqlDetails(ResultData actual, ResultData expected, LabError error) {
    }

    private static final int PREVIEW_ROWS = 30;

    private final LabExecutor executor;
    private final ObjectMapper json;

    public SqlExerciseValidator(LabExecutor executor, ObjectMapper json) {
        this.executor = executor;
        this.json = json;
    }

    @Override
    public Set<String> kinds() {
        return Set.of("SQL");
    }

    @Override
    public JsonNode publicPayload(Exercise ex) {
        ObjectNode p = json.createObjectNode();
        p.put("language", "sql");
        p.put("mode", mode(ex).name());
        p.put("ordered", ex.payload().path("ordered").asBoolean(false));
        p.put("starter", ex.payload().path("starter").asText(""));
        return p;
    }

    @Override
    public ValidationResult validate(Exercise ex, JsonNode answer) {
        String sql = answer == null ? "" : answer.asText("");
        SqlGuard.Mode mode = mode(ex);
        List<String> statements = SqlGuard.check(sql, mode);
        String check = ex.payload().path("check").asText(null);

        ResultData expected = run(SqlGuard.check(ex.solution(), SqlGuard.Mode.WRITE), mode, check);
        ResultData actual;
        try {
            actual = run(statements, mode, check);
        } catch (LabSqlException e) {
            LabError error = new LabError(0, e.statement(), e.getMessage(), e.hint(), e.sqlState());
            return ValidationResult.ko("Ta requête provoque une erreur SQL.",
                    e.hint() == null ? List.of() : List.of(e.hint()), new SqlDetails(null, preview(expected), error));
        }
        if (actual == null) {
            return ValidationResult.ko("Ta dernière instruction doit renvoyer un résultat (un SELECT).", List.of(),
                    new SqlDetails(null, preview(expected), null));
        }

        List<String> problems = compare(actual, expected, ex.payload().path("ordered").asBoolean(false));
        problems.addAll(missingKeywords(ex, sql));
        SqlDetails details = new SqlDetails(preview(actual), preview(expected), null);
        if (problems.isEmpty()) {
            return ValidationResult.ok("Bravo ! Ton résultat correspond exactement au résultat attendu.", details);
        }
        return ValidationResult.ko("Pas encore : compare ton résultat avec le résultat attendu.", problems, details);
    }

    private ResultData run(List<String> statements, SqlGuard.Mode mode, String check) {
        return executor.withSession(mode == SqlGuard.Mode.READ, session -> {
            ResultData last = null;
            for (String statement : statements) {
                var result = session.run(statement);
                if (result.data() != null) {
                    last = result.data();
                }
            }
            if (mode == SqlGuard.Mode.WRITE && check != null) {
                last = session.run(check).data();
            }
            return last;
        });
    }

    static List<String> compare(ResultData actual, ResultData expected, boolean ordered) {
        List<String> problems = new ArrayList<>();
        int ac = actual.columns().size();
        int ec = expected.columns().size();
        if (ac != ec) {
            problems.add("Ton résultat contient " + ac + " colonne(s) au lieu de " + ec + " : vérifie la liste des "
                    + "colonnes après SELECT (attendues : " + String.join(", ", expected.columns()) + ").");
            return problems;
        }
        int ar = actual.rows().size();
        int er = expected.rows().size();
        if (ar != er) {
            problems.add("Ton résultat contient " + ar + " ligne(s) au lieu de " + er + ". "
                    + (ar > er ? "Il manque sans doute une condition de filtre (WHERE, HAVING) ou un DISTINCT."
                            : "Ton filtre est peut-être trop strict, ou une jointure élimine des lignes."));
            return problems;
        }
        List<List<String>> a = normalize(actual.rows());
        List<List<String>> e = normalize(expected.rows());
        if (a.equals(e)) {
            return problems;
        }
        if (sameMultiset(a, e)) {
            if (ordered) {
                problems.add("Les bonnes lignes sont là, mais pas dans l'ordre demandé : vérifie ton ORDER BY "
                        + "(colonne et sens ASC / DESC).");
            }
            return problems;
        }
        for (List<String> row : e) {
            if (!a.contains(row)) {
                problems.add("La ligne attendue " + row + " n'apparaît pas dans ton résultat.");
                break;
            }
        }
        if (problems.isEmpty()) {
            problems.add("Certaines valeurs diffèrent du résultat attendu.");
        }
        return problems;
    }

    private static boolean sameMultiset(List<List<String>> a, List<List<String>> b) {
        Map<List<String>, Integer> counts = new HashMap<>();
        a.forEach(r -> counts.merge(r, 1, Integer::sum));
        b.forEach(r -> counts.merge(r, -1, Integer::sum));
        return counts.values().stream().allMatch(v -> v == 0);
    }

    /** Les nombres sont comparés par valeur : 29.90 et 29.9 sont égaux. */
    private static List<List<String>> normalize(List<List<String>> rows) {
        return rows.stream().map(r -> r.stream().map(SqlExerciseValidator::normalizeCell).toList()).toList();
    }

    private static String normalizeCell(String v) {
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.strip()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private List<String> missingKeywords(Exercise ex, String sql) {
        List<String> problems = new ArrayList<>();
        for (JsonNode rule : ex.payload().path("requiredKeywords")) {
            Pattern p = Pattern.compile(rule.path("pattern").asText(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            if (!p.matcher(sql).find()) {
                problems.add(rule.path("message").asText("Une notion attendue n'est pas utilisée."));
            }
        }
        return problems;
    }

    private static ResultData preview(ResultData data) {
        if (data == null || data.rows().size() <= PREVIEW_ROWS) {
            return data;
        }
        return new ResultData(data.columns(), data.rows().subList(0, PREVIEW_ROWS), true);
    }

    private static SqlGuard.Mode mode(Exercise ex) {
        return "WRITE".equalsIgnoreCase(ex.payload().path("mode").asText()) ? SqlGuard.Mode.WRITE : SqlGuard.Mode.READ;
    }
}
