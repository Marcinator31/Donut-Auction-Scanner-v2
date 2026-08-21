package net.donutmarket.market;

import java.util.Locale;

/**
 * Preis-Eingaben parsen und hübsch formatieren.
 *
 * Akzeptiert alles, was man beim Zocken so tippt:
 *   250k        -> 250000
 *   250.000     -> 250000   (deutscher Tausenderpunkt)
 *   250,000     -> 250000   (englischer Tausenderpunkt)
 *   1.5m / 1,5m -> 1500000
 *   2b          -> 2000000000
 *   $250k       -> 250000
 */
public final class PriceUtil {

    private PriceUtil() {
    }

    public static Double parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace("$", "")
                .replace("€", "")
                .replace(" ", "")
                .replace("_", "");
        if (s.isEmpty()) {
            return null;
        }

        double factor = 1.0;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> {
                factor = 1_000.0;
                s = s.substring(0, s.length() - 1);
            }
            case 'm' -> {
                factor = 1_000_000.0;
                s = s.substring(0, s.length() - 1);
            }
            case 'b' -> {
                factor = 1_000_000_000.0;
                s = s.substring(0, s.length() - 1);
            }
            case 't' -> {
                factor = 1_000_000_000_000.0;
                s = s.substring(0, s.length() - 1);
            }
            default -> {
                // kein Suffix
            }
        }
        if (s.isEmpty()) {
            return null;
        }

        s = normalizeSeparators(s);

        try {
            double v = Double.parseDouble(s);
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) {
                return null;
            }
            return v * factor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Entscheidet, ob Punkt/Komma Dezimal- oder Tausendertrenner ist.
     * Regel: Der *letzte* Trenner zählt. Stehen danach genau 3 Ziffern und gab
     * es davor schon Ziffern, ist es ein Tausendertrenner.
     */
    private static String normalizeSeparators(String s) {
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        int last = Math.max(lastDot, lastComma);

        if (last < 0) {
            return s;
        }

        String tail = s.substring(last + 1);
        boolean thousandsGrouped = tail.length() == 3 && tail.chars().allMatch(Character::isDigit);

        if (thousandsGrouped) {
            // alle Trenner entfernen: 1.250.000 -> 1250000
            return s.replace(".", "").replace(",", "");
        }
        // letzter Trenner ist das Dezimalzeichen, alle anderen fliegen raus
        String head = s.substring(0, last).replace(".", "").replace(",", "");
        return head + "." + tail;
    }

    // ---------------------------------------------------------------- formatting

    /** 1234567 -> "1.23m" - kompakt für enge GUI-Spalten. */
    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000_000_000.0) {
            return trim(v / 1_000_000_000_000.0) + "t";
        }
        if (a >= 1_000_000_000.0) {
            return trim(v / 1_000_000_000.0) + "b";
        }
        if (a >= 1_000_000.0) {
            return trim(v / 1_000_000.0) + "m";
        }
        if (a >= 1_000.0) {
            return trim(v / 1_000.0) + "k";
        }
        return trim(v);
    }

    /** 1234567 -> "1.234.567" - für Tooltips, wo Platz ist. */
    public static String full(double v) {
        long rounded = Math.round(v);
        String digits = Long.toString(Math.abs(rounded));
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            if (++c % 3 == 0 && i > 0) {
                sb.append('.');
            }
        }
        if (rounded < 0) {
            sb.append('-');
        }
        return sb.reverse().toString();
    }

    /** "+12.4%" / "-3.1%" */
    public static String percent(double from, double to) {
        if (from <= 0) {
            return "n/a";
        }
        double pct = (to - from) / from * 100.0;
        return (pct >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", pct) + "%";
    }

    private static String trim(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) {
            return Long.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
