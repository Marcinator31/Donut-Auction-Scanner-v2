package net.donutmarket.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Selbst gezeichnete Bedienelemente.
 *
 * Absichtlich KEINE Vanilla-Widgets: Vanilla-Buttons sehen aus wie Minecraft,
 * und genau das soll die Oberflaeche nicht. Ausserdem umgeht das die
 * Widget-API, die sich in 26.1 stark geaendert hat (renderWidget ->
 * extractWidgetRenderState) - hier gibt es nichts, was daran brechen kann.
 *
 * Es bleibt bei fill(), text() und item(). Nichts anderes.
 *
 * Layout-Prinzip: Jeder Screen berechnet seine Rechtecke in eigenen
 * layout-Methoden. Zeichnen und Klick-Auswertung nutzen dieselben Rechtecke,
 * es kann also nichts auseinanderlaufen.
 */
public final class Ui {

    private Ui() {
    }

    /** Rechteck mit Treffer-Pruefung. */
    public record Rect(int x, int y, int w, int h) {
        public boolean has(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public int centerX() {
            return x + w / 2;
        }

        public int centerY() {
            return y + h / 2;
        }

        public Rect inset(int by) {
            return new Rect(x + by, y + by, w - by * 2, h - by * 2);
        }
    }

    // ================================================================ Flaechen

    public static void fill(GuiGraphicsExtractor g, Rect r, int color) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), color);
    }

    /** Ecken weglassen - im Pixel-Look sauberer als eine harte Ecke. */
    public static void card(GuiGraphicsExtractor g, Rect r, int bg) {
        g.fill(r.x() + 1, r.y(), r.right() - 1, r.bottom(), bg);
        g.fill(r.x(), r.y() + 1, r.x() + 1, r.bottom() - 1, bg);
        g.fill(r.right() - 1, r.y() + 1, r.right(), r.bottom() - 1, bg);
    }

    public static void outline(GuiGraphicsExtractor g, Rect r, int color) {
        g.fill(r.x() + 1, r.y(), r.right() - 1, r.y() + 1, color);
        g.fill(r.x() + 1, r.bottom() - 1, r.right() - 1, r.bottom(), color);
        g.fill(r.x(), r.y() + 1, r.x() + 1, r.bottom() - 1, color);
        g.fill(r.right() - 1, r.y() + 1, r.right(), r.bottom() - 1, color);
    }

    /** Haarlinie, waagerecht. */
    public static void rule(GuiGraphicsExtractor g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    /** Senkrechter Verlauf, in 1px-Streifen. */
    public static void gradient(GuiGraphicsExtractor g, Rect r, int top, int bottom) {
        for (int i = 0; i < r.h(); i++) {
            float t = r.h() == 1 ? 0f : (float) i / (r.h() - 1);
            g.fill(r.x(), r.y() + i, r.right(), r.y() + i + 1, Theme.mix(top, bottom, t));
        }
    }

    // ================================================================ Text

    public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        g.text(f, s, x, y, color, false);
    }

    /** Schatten ist unser Ersatz fuer Fettschrift. */
    public static void strong(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        g.text(f, s, x, y, color, true);
    }

    public static void right(GuiGraphicsExtractor g, Font f, String s, int rightX, int y, int color) {
        g.text(f, s, rightX - f.width(s), y, color, false);
    }

    public static void center(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color) {
        g.text(f, s, cx - f.width(s) / 2, y, color, false);
    }

    /**
     * Gesperrter Text: jedes Zeichen einzeln mit zusaetzlichem Abstand.
     *
     * Da Minecraft nur eine Schrift in einer Groesse hat, ist das das einzige
     * echte typografische Mittel, das uebrig bleibt. Wird ausschliesslich fuer
     * Kleinlabels in Grossbuchstaben benutzt - der Kontrast zum normalen Text
     * schafft die Hierarchie, die sonst eine zweite Schrift liefern wuerde.
     */
    public static void tracked(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, int extra) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.text(f, ch, cx, y, color, false);
            cx += f.width(ch) + extra;
        }
    }

    public static int trackedWidth(Font f, String s, int extra) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += f.width(String.valueOf(s.charAt(i))) + extra;
        }
        return Math.max(0, w - extra);
    }

    /** Kleinlabel: Grossbuchstaben, gesperrt, gedaempft. Unsere "Eyebrow". */
    public static void label(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
        tracked(g, f, s.toUpperCase(java.util.Locale.ROOT), x, y, color, 1);
    }

    /** Statusanzeige rechtsbuendig: Text mit farbigem Punkt davor. */
    public static void dotRight(GuiGraphicsExtractor g, Font f, String s, int rightX, int y, int color) {
        int tw = f.width(s);
        g.text(f, s, rightX - tw, y, color, false);
        int dx = rightX - tw - 8;
        g.fill(dx, y + 1, dx + 3, y + 4, color);
        g.fill(dx + 1, y, dx + 2, y + 5, color);
    }

    public static String clip(Font f, String s, int maxWidth) {
        if (f.width(s) <= maxWidth) {
            return s;
        }
        int ellW = f.width("...");
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (char c : s.toCharArray()) {
            int cw = f.width(String.valueOf(c));
            if (w + cw + ellW > maxWidth) {
                break;
            }
            sb.append(c);
            w += cw;
        }
        return sb + "...";
    }

    // ================================================================ Bedienelemente

    /** Vollflaechiger Aktionsknopf in Akzentfarbe. */
    public static void primaryButton(GuiGraphicsExtractor g, Font f, Rect r, String labelText,
                                     boolean hovered, int accent) {
        card(g, r, hovered ? accent : Theme.mix(accent, Theme.GROUND, 0.18f));
        center(g, f, labelText, r.centerX(), r.centerY() - 4, Theme.GROUND);
    }

    /** Zurueckhaltender Knopf: nur Rahmen. */
    public static void ghostButton(GuiGraphicsExtractor g, Font f, Rect r, String labelText,
                                   boolean hovered) {
        if (hovered) {
            card(g, r, Theme.SURFACE_HI);
        }
        outline(g, r, hovered ? Theme.LINE : Theme.LINE_SOFT);
        center(g, f, labelText, r.centerX(), r.centerY() - 4,
                hovered ? Theme.CREAM : Theme.CREAM_DIM);
    }

    /** Kleiner Knopf fuer Zeilenaktionen. */
    public static void chip(GuiGraphicsExtractor g, Font f, Rect r, String labelText,
                           boolean selected, boolean hovered, int accent) {
        if (selected) {
            card(g, r, Theme.mix(accent, Theme.GROUND, 0.25f));
            outline(g, r, accent);
        } else {
            card(g, r, hovered ? Theme.SURFACE_HI : Theme.SURFACE);
            outline(g, r, hovered ? Theme.LINE : Theme.LINE_SOFT);
        }
        center(g, f, labelText, r.centerX(), r.centerY() - 4,
                selected ? Theme.CREAM : hovered ? Theme.CREAM : Theme.CREAM_DIM);
    }

    /** Schiebeschalter. Breite 22, Hoehe 12. */
    public static void toggle(GuiGraphicsExtractor g, Rect r, boolean on, boolean hovered, int accent) {
        int track = on ? Theme.mix(accent, Theme.GROUND, 0.35f) : Theme.SURFACE_SUNK;
        card(g, r, track);
        outline(g, r, on ? accent : hovered ? Theme.LINE : Theme.LINE_SOFT);
        int knob = r.h() - 4;
        int kx = on ? r.right() - knob - 2 : r.x() + 2;
        g.fill(kx, r.y() + 2, kx + knob, r.y() + 2 + knob, on ? accent : Theme.CREAM_FAINT);
    }

    /** Zeile mit Beschriftung links und Schalter rechts. */
    public static Rect toggleRow(GuiGraphicsExtractor g, Font f, Rect row, String labelText,
                                 String hint, boolean on, int mouseX, int mouseY, int accent) {
        Rect sw = new Rect(row.right() - 22, row.centerY() - 6, 22, 12);
        boolean hovered = row.has(mouseX, mouseY);
        if (hovered) {
            card(g, row, Theme.SURFACE_HI);
        }
        text(g, f, labelText, row.x() + 6, row.centerY() - (hint == null ? 4 : 8),
                hovered ? Theme.CREAM : Theme.CREAM_DIM);
        if (hint != null) {
            text(g, f, hint, row.x() + 6, row.centerY() + 2, Theme.CREAM_FAINT);
        }
        toggle(g, sw, on, hovered, accent);
        return row;
    }

    /** Item-Feld mit Rahmen. */
    public static void slot(GuiGraphicsExtractor g, Rect r, ItemStack stack, boolean hovered) {
        card(g, r, hovered ? Theme.SURFACE_HI : Theme.SURFACE_SUNK);
        outline(g, r, hovered ? Theme.LINE : Theme.LINE_SOFT);
        if (stack != null && !stack.isEmpty()) {
            g.item(stack, r.centerX() - 8, r.centerY() - 8);
        }
    }

    /**
     * Zielskala - das Signature-Element.
     *
     * Die Mitte ist das Ziel. Der aktuelle Preis sitzt als Markierung links
     * (darunter) oder rechts (darueber) davon, und die Strecke dazwischen wird
     * eingefaerbt. Man sieht den Abstand als Position, nicht als Prozentzahl -
     * ein Blick genuegt fuer "bin ich da?".
     */
    public static void targetGauge(GuiGraphicsExtractor g, Rect r, double current, double target,
                                   boolean sellAbove) {
        card(g, r, Theme.SURFACE_SUNK);

        if (target <= 0 || current <= 0) {
            return;
        }
        // Fenster: Ziel plus/minus 50 Prozent
        double lo = target * 0.5;
        double hi = target * 1.5;
        double t = (current - lo) / (hi - lo);
        t = Math.max(0.0, Math.min(1.0, t));

        int center = r.x() + r.w() / 2;
        int pos = r.x() + (int) Math.round(t * (r.w() - 1));

        boolean hit = sellAbove ? current >= target : current <= target;
        int color = hit ? Theme.forDirection(sellAbove) : Theme.CREAM_FAINT;

        // Strecke zwischen Ziel und Ist
        int from = Math.min(center, pos);
        int to = Math.max(center, pos);
        g.fill(from, r.y() + 1, to + 1, r.bottom() - 1, Theme.alpha(color, hit ? 0xFF : 0x77));

        // Ziel-Markierung
        g.fill(center, r.y(), center + 1, r.bottom(), Theme.CREAM_DIM);

        // Ist-Markierung
        g.fill(pos - 1, r.y() - 1, pos + 2, r.bottom() + 1, color);
    }

    // ================================================================ Tooltip

    /**
     * Eigener Tooltip. Selbst gebaut, weil setTooltipForNextFrame zwischen
     * Versionen die Signatur wechselt - und weil wir hier die Farben bestimmen.
     */
    public static void tooltip(GuiGraphicsExtractor g, Font f, List<Component> lines,
                               int mouseX, int mouseY, int screenW, int screenH) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int lineH = 10;
        int w = 0;
        for (Component c : lines) {
            w = Math.max(w, f.width(c.getString()));
        }
        w += 12;
        int h = lines.size() * lineH + 8;

        int x = mouseX + 10;
        int y = mouseY - 6;
        if (x + w > screenW - 4) {
            x = mouseX - w - 10;
        }
        if (y + h > screenH - 4) {
            y = screenH - h - 4;
        }
        x = Math.max(4, x);
        y = Math.max(4, y);

        Rect box = new Rect(x, y, w, h);
        card(g, box, 0xFA1E1519);
        outline(g, box, Theme.LINE);

        int ty = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            g.text(f, lines.get(i), x + 6, ty, i == 0 ? Theme.CREAM : Theme.CREAM_DIM, false);
            ty += lineH;
        }
    }

    // ================================================================ Alt-Signaturen
    // Die beiden urspruenglich mit x/y/w/h geschriebenen Screens (Item-Auswahl
    // und Verlauf) nutzen diese Varianten. Gleiche Zeichenlogik wie oben, nur
    // ohne Rect - damit die Portierung nicht jede Zeile anfassen muss.

    public static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void panelCompat(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg, int border) {
        Rect r = new Rect(x, y, w, h);
        card(g, r, bg);
        outline(g, r, border);
    }

    public static void outlineCompat(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        outline(g, new Rect(x, y, w, h), color);
    }

    public static void dotCompat(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        g.fill(cx - r, cy - r + 1, cx + r, cy + r - 1, color);
        g.fill(cx - r + 1, cy - r, cx + r - 1, cy + r, color);
    }

    // ================================================================ Scrollbalken

    public static void scrollbar(GuiGraphicsExtractor g, int x, int top, int height,
                                 int visible, int total, int offset, int accent) {
        if (total <= visible) {
            return;
        }
        g.fill(x, top, x + 2, top + height, Theme.LINE_SOFT);
        int thumbH = Math.max(12, height * visible / total);
        int maxOff = total - visible;
        int thumbY = top + (height - thumbH) * Math.max(0, Math.min(offset, maxOff)) / Math.max(1, maxOff);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, accent);
    }
}
