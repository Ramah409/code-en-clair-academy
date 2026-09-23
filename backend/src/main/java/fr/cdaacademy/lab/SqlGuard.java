package fr.cdaacademy.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import fr.cdaacademy.common.BusinessRuleException;

/**
 * Découpe et contrôle le SQL soumis au laboratoire.
 *
 * Chaque instruction est vérifiée isolément : seules les commandes d'interrogation, de
 * manipulation et de définition de tables sont acceptées. Les commandes de transaction
 * (COMMIT, ROLLBACK...), de session (SET, RESET...) et les fonctions sensibles sont refusées,
 * ce qui garantit que la transaction englobante sera toujours annulée.
 */
public final class SqlGuard {

    public enum Mode {
        /** Interrogation uniquement (SELECT, WITH, VALUES, TABLE, EXPLAIN). */
        READ,
        /** Interrogation, manipulation (INSERT, UPDATE, DELETE) et définition de tables, vues, index. */
        WRITE
    }

    static final int MAX_STATEMENTS = 20;
    static final int MAX_LENGTH = 20_000;

    private static final Set<String> READ_COMMANDS = Set.of("SELECT", "WITH", "VALUES", "TABLE", "EXPLAIN");
    private static final Set<String> WRITE_COMMANDS = Set.of("INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP");
    private static final Pattern ALLOWED_DDL = Pattern.compile(
            "^(CREATE|ALTER|DROP)\\s+(TABLE|VIEW|INDEX|UNIQUE\\s+INDEX|OR\\s+REPLACE\\s+VIEW|SEQUENCE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN_FUNCTIONS = Pattern.compile(
            "\\b(set_config|pg_terminate_backend|pg_cancel_backend|pg_reload_conf|pg_sleep\\w*|lo_\\w+|dblink\\w*"
                    + "|pg_read_\\w+|pg_ls_\\w+|pg_stat_file|pg_advisory\\w*|pg_notify|txid_\\w+|pg_logical\\w*)\"?\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private SqlGuard() {
    }

    /** Découpe le texte en instructions et vérifie chacune d'elles. */
    public static List<String> check(String sql, Mode mode) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessRuleException("Écris une requête SQL avant de l'exécuter.");
        }
        if (sql.length() > MAX_LENGTH) {
            throw new BusinessRuleException("La requête est trop longue (20 000 caractères au maximum).");
        }
        List<String> statements = split(sql);
        if (statements.isEmpty()) {
            throw new BusinessRuleException("Aucune instruction SQL détectée (le texte ne contient que des commentaires ?).");
        }
        if (statements.size() > MAX_STATEMENTS) {
            throw new BusinessRuleException("Vingt instructions au maximum par exécution.");
        }
        for (String statement : statements) {
            checkStatement(statement, mode);
        }
        return statements;
    }

    private static void checkStatement(String statement, Mode mode) {
        String code = stripLiterals(statement);
        String keyword = firstKeyword(code);
        boolean read = READ_COMMANDS.contains(keyword);
        boolean write = WRITE_COMMANDS.contains(keyword);

        if (!read && !(write && mode == Mode.WRITE)) {
            if (write) {
                throw new BusinessRuleException(
                        "Cet exercice attend une requête d'interrogation (SELECT) : " + keyword + " n'est pas autorisé ici.");
            }
            throw new BusinessRuleException("La commande « " + keyword + " » n'est pas autorisée dans le laboratoire. "
                    + "Utilise SELECT, INSERT, UPDATE, DELETE, CREATE TABLE, CREATE VIEW, CREATE INDEX ou ALTER TABLE.");
        }
        if (Set.of("CREATE", "ALTER", "DROP").contains(keyword)
                && !ALLOWED_DDL.matcher(code.strip()).find()) {
            throw new BusinessRuleException("Seules les tables, vues, index et séquences peuvent être créés dans le laboratoire.");
        }
        if (FORBIDDEN_FUNCTIONS.matcher(code).find()) {
            throw new BusinessRuleException("Cette fonction système n'est pas autorisée dans le laboratoire.");
        }
    }

    static String firstKeyword(String code) {
        String s = code.strip();
        while (s.startsWith("(")) {
            s = s.substring(1).strip();
        }
        int end = 0;
        while (end < s.length() && Character.isLetter(s.charAt(end))) {
            end++;
        }
        return s.substring(0, end).toUpperCase(Locale.ROOT);
    }

    /**
     * Découpe sur les « ; » situés hors des chaînes, identifiants entre guillemets,
     * chaînes dollar ($$...$$) et commentaires.
     */
    static List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                int end = skipQuoted(sql, i, c, c == '\'' && isEscapeString(sql, i));
                current.append(sql, i, end);
                i = end;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? n : end;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i = skipBlockComment(sql, i);
                current.append(' ');
            } else if (c == '$' && !(i > 0 && isIdentifierPart(sql.charAt(i - 1)))) {
                int end = skipDollar(sql, i);
                current.append(sql, i, end);
                i = end;
            } else if (c == ';') {
                addIfNotBlank(result, current);
                current.setLength(0);
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        addIfNotBlank(result, current);
        return result;
    }

    /** Neutralise le contenu des chaînes pour l'analyse des mots-clés (les identifiants "..." sont conservés). */
    static String stripLiterals(String statement) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < statement.length()) {
            char c = statement.charAt(i);
            if (c == '\'') {
                int end = skipQuoted(statement, i, c, isEscapeString(statement, i));
                out.append("''");
                i = end;
            } else if (c == '"') {
                int end = skipQuoted(statement, i, c, false);
                out.append(statement, i, end);
                i = end;
            } else if (c == '$' && !(i > 0 && isIdentifierPart(statement.charAt(i - 1)))) {
                int end = skipDollar(statement, i);
                out.append(end - i > 1 ? "''" : "$");
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Chaîne E'...' : la barre oblique inverse y échappe le caractère suivant. */
    private static boolean isEscapeString(String s, int quoteIndex) {
        if (quoteIndex == 0 || Character.toUpperCase(s.charAt(quoteIndex - 1)) != 'E') {
            return false;
        }
        return quoteIndex < 2 || !isIdentifierPart(s.charAt(quoteIndex - 2));
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int skipQuoted(String s, int start, char quote, boolean backslashEscapes) {
        int i = start + 1;
        while (i < s.length()) {
            if (backslashEscapes && s.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (s.charAt(i) == quote) {
                if (i + 1 < s.length() && s.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        throw new BusinessRuleException("Guillemet non fermé : vérifie tes chaînes de caractères ('...').");
    }

    private static int skipBlockComment(String s, int start) {
        int depth = 0;
        int i = start;
        while (i < s.length() - 1) {
            if (s.charAt(i) == '/' && s.charAt(i + 1) == '*') {
                depth++;
                i += 2;
            } else if (s.charAt(i) == '*' && s.charAt(i + 1) == '/') {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        throw new BusinessRuleException("Commentaire /* ... */ non fermé.");
    }

    /** Chaîne dollar : $$...$$ ou $tag$...$tag$. Un « $1 » (paramètre) est laissé tel quel. */
    private static int skipDollar(String s, int start) {
        int j = start + 1;
        while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
            j++;
        }
        if (j >= s.length() || s.charAt(j) != '$' || (j > start + 1 && Character.isDigit(s.charAt(start + 1)))) {
            return start + 1;
        }
        String tag = s.substring(start, j + 1);
        int end = s.indexOf(tag, j + 1);
        if (end < 0) {
            throw new BusinessRuleException("Chaîne " + tag + " non fermée.");
        }
        return end + tag.length();
    }

    private static void addIfNotBlank(List<String> list, StringBuilder sb) {
        String s = sb.toString().strip();
        if (!s.isEmpty()) {
            list.add(s);
        }
    }
}
