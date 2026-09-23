package fr.cdaacademy.user;

/**
 * Calcul du niveau à partir des points d'expérience.
 * Le niveau n commence à 50 × (n − 1)² XP : 0, 50, 200, 450, 800...
 * La progression ralentit volontairement pour rester motivante sur la durée.
 */
public final class LevelCalculator {

    private static final int STEP = 50;

    private LevelCalculator() {
    }

    public static int level(int xp) {
        return (int) Math.floor(Math.sqrt(Math.max(xp, 0) / (double) STEP)) + 1;
    }

    /** XP nécessaire pour atteindre le début du niveau donné. */
    public static int xpForLevel(int level) {
        return STEP * (level - 1) * (level - 1);
    }
}
