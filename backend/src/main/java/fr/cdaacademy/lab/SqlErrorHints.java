package fr.cdaacademy.lab;

import java.util.Map;

/** Explications en français des erreurs PostgreSQL les plus fréquentes chez les débutantes. */
final class SqlErrorHints {

    private static final Map<String, String> HINTS = Map.ofEntries(
            Map.entry("42601", "Erreur de syntaxe : vérifie l'ordre des mots-clés (SELECT … FROM … WHERE … GROUP BY … "
                    + "HAVING … ORDER BY … LIMIT), les virgules entre les colonnes et les parenthèses."),
            Map.entry("42P01", "Cette table n'existe pas. Vérifie son nom dans l'explorateur de schéma "
                    + "(les noms sont en minuscules et au pluriel : clients, produits, commandes…)."),
            Map.entry("42703", "Cette colonne n'existe pas dans la table interrogée. Vérifie l'orthographe, "
                    + "ou préfixe-la par le nom (ou l'alias) de sa table si plusieurs tables sont jointes."),
            Map.entry("42702", "Nom de colonne ambigu : elle existe dans plusieurs tables de la jointure. "
                    + "Préfixe-la avec l'alias de la table, par exemple c.id."),
            Map.entry("42803", "Avec GROUP BY, chaque colonne du SELECT doit soit figurer dans le GROUP BY, "
                    + "soit être utilisée dans une fonction d'agrégat (COUNT, SUM, AVG, MIN, MAX)."),
            Map.entry("42883", "Fonction ou opérateur inconnu pour ces types de données. Vérifie le nom de la "
                    + "fonction et le type des valeurs comparées (texte entre apostrophes, nombres sans)."),
            Map.entry("42804", "Types incompatibles : tu compares ou insères une valeur d'un type différent de celui "
                    + "de la colonne."),
            Map.entry("22P02", "Valeur invalide pour ce type (par exemple du texte à la place d'un nombre)."),
            Map.entry("22007", "Format de date invalide : utilise le format 'AAAA-MM-JJ', par exemple '2025-03-15'."),
            Map.entry("22008", "Date inexistante ou hors limites."),
            Map.entry("22012", "Division par zéro."),
            Map.entry("23505", "Violation d'unicité : une ligne possède déjà cette valeur (clé primaire ou colonne UNIQUE)."),
            Map.entry("23503", "Violation de clé étrangère : la valeur référencée n'existe pas, ou des lignes d'une "
                    + "autre table dépendent encore de celle que tu supprimes."),
            Map.entry("23502", "Une colonne NOT NULL n'a pas reçu de valeur."),
            Map.entry("23514", "Une contrainte CHECK est violée : la valeur ne respecte pas la règle définie sur la colonne."),
            Map.entry("42P07", "Un objet porte déjà ce nom dans le schéma."),
            Map.entry("42501", "Permission refusée : le laboratoire ne peut agir que sur ses propres tables pédagogiques."),
            Map.entry("25006", "Cet exercice s'exécute en lecture seule : seule une requête SELECT est attendue."),
            Map.entry("57014", "La requête a dépassé le temps maximal (3 secondes). Une jointure sans condition "
                    + "ou une boucle infinie de sous-requêtes en est souvent la cause."),
            Map.entry("21000", "La sous-requête renvoie plusieurs lignes alors qu'une seule valeur est attendue : "
                    + "utilise IN à la place de =, ou limite la sous-requête."),
            Map.entry("42P10", "Avec SELECT DISTINCT, les colonnes du ORDER BY doivent apparaître dans le SELECT."));

    private SqlErrorHints() {
    }

    static String explain(String sqlState, String message) {
        if (sqlState == null) {
            return null;
        }
        String hint = HINTS.get(sqlState);
        if (hint == null && sqlState.startsWith("42")) {
            return "Erreur dans l'écriture de la requête : relis attentivement le message ci-dessus.";
        }
        return hint;
    }
}
