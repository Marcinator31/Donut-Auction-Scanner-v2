package net.donutmarket.ui;

/**
 * Design-Tokens.
 *
 * Bewusst NICHT der uebliche Dunkelblau-plus-Neon-Look. Die Palette kommt aus
 * dem Thema: geroestetes Pflaumenbraun als Grund, Keramik-Creme als Text.
 *
 * Wichtig sind die zwei Akzente - sie sind keine Deko, sondern tragen
 * Information. Himbeer heisst immer "verkaufen / Preis darueber", Pistazie
 * immer "kaufen / Preis darunter". Wer die Farbe einmal gelernt hat, muss das
 * Label nicht mehr lesen.
 *
 * Typografie: Minecraft hat genau eine Schrift in genau einer Groesse. Als
 * Ersatz fuer eine Schriftfamilien-Paarung bleiben zwei Achsen: Schatten an
 * oder aus (das ist unser "fett") und Buchstabenabstand (siehe Ui#tracked,
 * fuer Kleinlabels in Grossbuchstaben).
 */
public final class Theme {

    private Theme() {
    }

    // ---------------------------------------------------------------- Grundflaechen

    /** Abdunkelung ueber dem Spiel. */
    public static final int SCRIM = 0xE81A1216;

    public static final int GROUND = 0xFF1A1216;
    public static final int SURFACE = 0xFF241A1F;
    public static final int SURFACE_HI = 0xFF2E2128;
    public static final int SURFACE_SUNK = 0xFF150F13;
    public static final int HEADER = 0xFF2A1D24;

    public static final int LINE = 0xFF3D2C34;
    public static final int LINE_SOFT = 0xFF2F222A;

    // ---------------------------------------------------------------- Text

    public static final int CREAM = 0xFFF2E7DC;
    public static final int CREAM_DIM = 0xFFA8968C;
    public static final int CREAM_FAINT = 0xFF6E5C58;

    // ---------------------------------------------------------------- Semantik

    /** Verkaufen / Preis liegt ueber dem Ziel. */
    public static final int RASPBERRY = 0xFFE8548B;
    public static final int RASPBERRY_DEEP = 0xFFC0447A;

    /** Kaufen / Preis liegt unter dem Ziel. */
    public static final int PISTACHIO = 0xFF8FD17A;
    public static final int PISTACHIO_DEEP = 0xFF6BA85A;

    /** Fast am Ziel. */
    public static final int AMBER = 0xFFE8A33D;

    /** Fehler. */
    public static final int CHILI = 0xFFE2603F;

    // ---------------------------------------------------------------- Layout

    /** Alle Abstaende sind Vielfache davon. */
    public static final int UNIT = 4;

    public static int u(int n) {
        return UNIT * n;
    }

    // ---------------------------------------------------------------- Helfer

    /** Richtungsfarbe: Verkauf ist Himbeer, Kauf ist Pistazie. */
    public static int forDirection(boolean sellAbove) {
        return sellAbove ? RASPBERRY : PISTACHIO;
    }

    public static int forDirectionDeep(boolean sellAbove) {
        return sellAbove ? RASPBERRY_DEEP : PISTACHIO_DEEP;
    }

    /**
     * Farbe fuer einen gemessenen Preis: erreicht das Ziel die gewuenschte
     * Richtung, leuchtet es in der Richtungsfarbe. Kurz davor Amber, sonst
     * gedaempft.
     */
    public static int forPrice(boolean sellAbove, double current, double target) {
        if (target <= 0) {
            return CREAM;
        }
        boolean hit = sellAbove ? current >= target : current <= target;
        if (hit) {
            return forDirection(sellAbove);
        }
        double closeness = sellAbove ? current / target : target / current;
        return closeness > 0.9 ? AMBER : CREAM_DIM;
    }

    public static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0x00FFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
