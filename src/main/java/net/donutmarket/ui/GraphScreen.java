package net.donutmarket.ui;

import net.donutmarket.market.Alert;
import net.donutmarket.market.HistoryStore;
import net.donutmarket.market.ItemIndex;
import net.donutmarket.market.MarketService;
import net.donutmarket.market.PriceUtil;
import net.donutmarket.notify.Notifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Preisverlauf als Graph.
 *
 * Komplett aus fill()-Rechtecken gebaut: Fläche, Linie, Raster, Crosshair.
 * Keine Linien-API, kein Scissor, kein Blit - läuft damit unabhängig davon,
 * wie Mojang die Render-Pipeline im nächsten Drop wieder umbaut.
 *
 * Datenquellen (siehe HistoryStore):
 *   ASK  - selbst gemessene günstigste Angebote (blau)
 *   SALE - echte Verkäufe aus /auction/transactions (pink)
 */
public class GraphScreen extends BaseScreen {

    private record Range(String label, long millis) {
    }

    private static final Range[] RANGES = {
            new Range("1h", 3_600_000L),
            new Range("6h", 21_600_000L),
            new Range("24h", 86_400_000L),
            new Range("7d", 604_800_000L)
    };

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 216;
    private static final int PAD_LEFT = 14;
    private static final int PAD_RIGHT = 46;
    private static final int CHART_TOP_OFFSET = 74;
    private static final int CHART_BOTTOM_OFFSET = 46;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final String itemId;
    private final Alert linkedAlert;

    private int rangeIndex = 2; // 24h
    private int panelX;
    private int panelY;
    private boolean loading = false;
    private String statusText = "";

    public GraphScreen(Screen parent, String itemId, Alert linkedAlert) {
        super(Component.literal("Preisverlauf"));
        this.parent = parent;
        this.itemId = itemId;
        this.linkedAlert = linkedAlert;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
    }

    // ---------------------------------------------------------------- Layout

    private Ui.Rect rangeChip(int i) {
        int w = 30;
        int x = panelX + PANEL_W - 10 - (RANGES.length * (w + 3)) + 3;
        return new Ui.Rect(x + i * (w + 3), panelY + 46, w, 16);
    }

    private Ui.Rect loadSalesButton() {
        return new Ui.Rect(panelX + 10, panelY + PANEL_H - 25, 104, 18);
    }

    private Ui.Rect backButton() {
        return new Ui.Rect(panelX + PANEL_W - 72, panelY + PANEL_H - 25, 62, 18);
    }

    /**
     * Holt echte, abgeschlossene Verkäufe nach. Das ist die einzige Quelle
     * für Preise aus der Zeit, in der die Mod noch nicht lief.
     */
    private void loadSales() {
        if (loading) {
            return;
        }
        loading = true;
        statusText = "Lade Verkäufe...";
        MarketService svc = MarketService.get();
        String term = linkedAlert != null ? linkedAlert.searchTerm() : ItemIndex.stripNamespace(itemId);
        svc.runAsync(() -> {
            int n = svc.seedHistoryFromTransactions(itemId, term, 3);
            loading = false;
            statusText = n > 0
                    ? n + " Verkäufe übernommen"
                    : "Keine Verkäufe mit Zeitstempel gefunden";
            if (n == 0) {
                Notifier.info("Die Transactions-API lieferte keine nutzbaren Zeitstempel. "
                        + "Prüfe mit /dm raw transactions 1, wie die Felder wirklich heissen.");
            }
        });
    }

    private int chartX() {
        return panelX + PAD_LEFT;
    }

    private int chartY() {
        return panelY + CHART_TOP_OFFSET;
    }

    private int chartW() {
        return PANEL_W - PAD_LEFT - PAD_RIGHT;
    }

    private int chartH() {
        return PANEL_H - CHART_TOP_OFFSET - CHART_BOTTOM_OFFSET;
    }

    // ------------------------------------------------------------------ Rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, Theme.SCRIM);
        Ui.panelCompat(g, panelX, panelY, PANEL_W, PANEL_H, Theme.GROUND, Theme.LINE);
        g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + 40, Theme.HEADER);
        g.fill(panelX + 1, panelY + 40, panelX + PANEL_W - 1, panelY + 41, Theme.RASPBERRY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        long window = RANGES[rangeIndex].millis();
        HistoryStore hist = MarketService.get().history();
        List<HistoryStore.PricePoint> points = hist.window(itemId, window);
        HistoryStore.Stats stats = hist.stats(itemId, window);

        drawHeader(g, stats);

        int cx = chartX();
        int cy = chartY();
        int cw = chartW();
        int ch = chartH();

        g.fill(cx, cy, cx + cw, cy + ch, 0xFF0E131D);
        Ui.outlineCompat(g, cx, cy, cw, ch, Theme.LINE_SOFT);

        if (points.size() < 2) {
            drawEmptyState(g, cx, cy, cw, ch);
            drawFooter(g, mouseX, mouseY);
            return;
        }

        double min = stats.min();
        double max = stats.max();
        if (linkedAlert != null && linkedAlert.targetPrice > 0) {
            min = Math.min(min, linkedAlert.targetPrice);
            max = Math.max(max, linkedAlert.targetPrice);
        }
        double span = max - min;
        if (span <= 0) {
            span = Math.max(1.0, max * 0.1);
            min -= span / 2;
            max += span / 2;
            span = max - min;
        } else {
            double pad = span * 0.12;
            min -= pad;
            max += pad;
            span = max - min;
        }

        long now = System.currentTimeMillis();
        long from = now - window;

        drawGrid(g, cx, cy, cw, ch, min, max, from, now);

        if (linkedAlert != null && linkedAlert.targetPrice > 0) {
            drawTargetLine(g, cx, cy, cw, ch, min, span, linkedAlert.targetPrice);
        }

        drawSeries(g, points, cx, cy, cw, ch, min, span, from, window);
        drawSalePoints(g, points, cx, cy, cw, ch, min, span, from, window);
        drawCrosshair(g, points, cx, cy, cw, ch, min, span, from, window, mouseX, mouseY);

        drawFooter(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor g, HistoryStore.Stats stats) {
        ItemIndex.Entry e = ItemIndex.find(itemId);
        if (e != null) {
            ItemStack st = e.stack();
            if (!st.isEmpty()) {
                g.item(st, panelX + 10, panelY + 6);
            }
        }
        String name = e != null ? e.display() : itemId;
        Ui.strong(g, this.font, Ui.clip(this.font, name, 150),
                panelX + 32, panelY + 6, Theme.CREAM);

        if (stats.isEmpty()) {
            Ui.text(g, this.font, "keine Daten", panelX + 32, panelY + 18, Theme.CREAM_FAINT);
        } else {
            Ui.text(g, this.font, PriceUtil.full(stats.last()) + " $",
                    panelX + 32, panelY + 18, Theme.CREAM);
            double chg = stats.changePercent();
            String chgStr = (chg >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", chg) + "%";
            Ui.text(g, this.font, chgStr,
                    panelX + 32 + this.font.width(PriceUtil.full(stats.last()) + " $") + 6,
                    panelY + 18, chg >= 0 ? Theme.PISTACHIO : Theme.CHILI);
        }

        Ui.text(g, this.font, "min", panelX + 200, panelY + 6, Theme.CREAM_FAINT);
        Ui.text(g, this.font, "avg", panelX + 250, panelY + 6, Theme.CREAM_FAINT);
        Ui.text(g, this.font, "max", panelX + 296, panelY + 6, Theme.CREAM_FAINT);
        if (!stats.isEmpty()) {
            Ui.text(g, this.font, PriceUtil.compact(stats.min()), panelX + 200, panelY + 18, Theme.PISTACHIO);
            Ui.text(g, this.font, PriceUtil.compact(stats.avg()), panelX + 250, panelY + 18, Theme.CREAM_DIM);
            Ui.text(g, this.font, PriceUtil.compact(stats.max()), panelX + 296, panelY + 18, Theme.CHILI);
        }

        Ui.text(g, this.font, "Zeitraum", panelX + 10, panelY + 51, Theme.CREAM_DIM);
    }

    private void drawGrid(GuiGraphicsExtractor g, int cx, int cy, int cw, int ch,
                          double min, double max, long from, long now) {
        int hLines = 4;
        for (int i = 0; i <= hLines; i++) {
            int y = cy + (ch - 1) * i / hLines;
            g.fill(cx + 1, y, cx + cw - 1, y + 1, Theme.LINE_SOFT);
            double value = max - (max - min) * i / hLines;
            Ui.text(g, this.font, PriceUtil.compact(value), cx + cw + 4, y - 3, Theme.CREAM_FAINT);
        }
        int vLines = 4;
        for (int i = 0; i <= vLines; i++) {
            int x = cx + (cw - 1) * i / vLines;
            g.fill(x, cy + 1, x + 1, cy + ch - 1, Theme.LINE_SOFT);
            long t = from + (now - from) * i / vLines;
            String label = RANGES[rangeIndex].millis() > 86_400_000L
                    ? DATE_FMT.format(Instant.ofEpochMilli(t)).substring(0, 6)
                    : TIME_FMT.format(Instant.ofEpochMilli(t));
            int lw = this.font.width(label);
            int lx = Math.max(cx, Math.min(x - lw / 2, cx + cw - lw));
            Ui.text(g, this.font, label, lx, cy + ch + 4, Theme.CREAM_FAINT);
        }
    }

    private void drawTargetLine(GuiGraphicsExtractor g, int cx, int cy, int cw, int ch,
                                double min, double span, double target) {
        int y = valueToY(target, cy, ch, min, span);
        // gestrichelt
        for (int x = cx + 1; x < cx + cw - 1; x += 6) {
            g.fill(x, y, Math.min(x + 3, cx + cw - 1), y + 1, Theme.AMBER);
        }
        String lbl = "Ziel";
        Ui.text(g, this.font, lbl, cx + 3, y - 9, Theme.AMBER);
    }

    /**
     * Zeichnet Fläche + Linie. Für jede Bildschirmspalte wird der
     * interpolierte Y-Wert bestimmt; die Linie entsteht dann als Kette kleiner
     * Rechtecke, die auch steile Sprunge sauber verbindet.
     */
    private void drawSeries(GuiGraphicsExtractor g, List<HistoryStore.PricePoint> points,
                            int cx, int cy, int cw, int ch,
                            double min, double span, long from, long window) {
        int prevX = -1;
        int prevY = -1;

        for (int i = 0; i < points.size(); i++) {
            HistoryStore.PricePoint p = points.get(i);
            int x = timeToX(p.time(), cx, cw, from, window);
            int y = valueToY(p.price(), cy, ch, min, span);

            // Fläche unter dem Punkt
            int alphaTop = 0x55;
            for (int yy = y; yy < cy + ch - 1; yy++) {
                float t = (float) (yy - y) / Math.max(1, (cy + ch - 1 - y));
                int col = Theme.alpha(Theme.PISTACHIO, Math.round(alphaTop * (1 - t)));
                g.fill(x, yy, x + 1, yy + 1, col);
            }

            if (prevX >= 0) {
                connect(g, prevX, prevY, x, y, Theme.PISTACHIO);
            }
            prevX = x;
            prevY = y;
        }
    }

    /** Verbindet zwei Punkte mit 2px starker Linie aus Rechtecken. */
    private void connect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        if (dx <= 0) {
            g.fill(x1, Math.min(y0, y1), x1 + 2, Math.max(y0, y1) + 2, color);
            return;
        }
        for (int i = 0; i <= dx; i++) {
            int x = x0 + i;
            int ya = y0 + (y1 - y0) * i / dx;
            int yb = y0 + (y1 - y0) * Math.min(i + 1, dx) / dx;
            int top = Math.min(ya, yb);
            int bot = Math.max(ya, yb);
            g.fill(x, top, x + 1, bot + 2, color);
        }
    }

    /** Echte Verkäufe zusätzlich als kleine Punkte markieren. */
    private void drawSalePoints(GuiGraphicsExtractor g, List<HistoryStore.PricePoint> points,
                                int cx, int cy, int cw, int ch,
                                double min, double span, long from, long window) {
        for (HistoryStore.PricePoint p : points) {
            if (p.source() != HistoryStore.Source.SALE) {
                continue;
            }
            int x = timeToX(p.time(), cx, cw, from, window);
            int y = valueToY(p.price(), cy, ch, min, span);
            Ui.dotCompat(g, x, y, 2, Theme.RASPBERRY);
        }
    }

    private void drawCrosshair(GuiGraphicsExtractor g, List<HistoryStore.PricePoint> points,
                               int cx, int cy, int cw, int ch,
                               double min, double span, long from, long window,
                               int mouseX, int mouseY) {
        if (!Ui.inside(mouseX, mouseY, cx, cy, cw, ch)) {
            return;
        }
        HistoryStore.PricePoint nearest = null;
        int nearestX = 0;
        int bestDist = Integer.MAX_VALUE;

        for (HistoryStore.PricePoint p : points) {
            int x = timeToX(p.time(), cx, cw, from, window);
            int d = Math.abs(x - mouseX);
            if (d < bestDist) {
                bestDist = d;
                nearest = p;
                nearestX = x;
            }
        }
        if (nearest == null || bestDist > 20) {
            return;
        }
        int y = valueToY(nearest.price(), cy, ch, min, span);

        g.fill(nearestX, cy + 1, nearestX + 1, cy + ch - 1, Theme.alpha(Theme.CREAM_DIM, 0x66));
        g.fill(cx + 1, y, cx + cw - 1, y + 1, Theme.alpha(Theme.CREAM_DIM, 0x44));
        Ui.dotCompat(g, nearestX, y, 3, nearest.source() == HistoryStore.Source.SALE
                ? Theme.RASPBERRY : Theme.PISTACHIO);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(PriceUtil.full(nearest.price()) + " $ pro Stück"));
        lines.add(Component.literal(DATE_FMT.format(Instant.ofEpochMilli(nearest.time()))));
        lines.add(Component.literal(nearest.source() == HistoryStore.Source.SALE
                ? "Verkauft (Transaktion)"
                : "Günstigstes Angebot"));
        Ui.tooltip(g, this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    private void drawEmptyState(GuiGraphicsExtractor g, int cx, int cy, int cw, int ch) {
        int midY = cy + ch / 2 - 14;
        Ui.center(g, this.font, "Noch zu wenig Daten für diesen Zeitraum",
                cx + cw / 2, midY, Theme.CREAM_DIM);
        Ui.center(g, this.font, "Die API liefert keine Historie - die Mod sammelt selbst.",
                cx + cw / 2, midY + 12, Theme.CREAM_FAINT);
        Ui.center(g, this.font, "\"Verkäufe laden\" holt echte Abschlüsse nach.",
                cx + cw / 2, midY + 22, Theme.CREAM_FAINT);
    }

    private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (int i = 0; i < RANGES.length; i++) {
            Ui.Rect r = rangeChip(i);
            Ui.chip(g, this.font, r, RANGES[i].label(), i == rangeIndex,
                    r.has(mouseX, mouseY), Theme.RASPBERRY);
        }
        Ui.Rect lb = loadSalesButton();
        Ui.ghostButton(g, this.font, lb, loading ? "lädt…" : "Verkäufe laden", lb.has(mouseX, mouseY));
        Ui.Rect bb = backButton();
        Ui.ghostButton(g, this.font, bb, "Zurück", bb.has(mouseX, mouseY));

        int y = panelY + PANEL_H - 36;
        Ui.dotCompat(g, panelX + 118, y + 3, 2, Theme.PISTACHIO);
        Ui.text(g, this.font, "Angebot", panelX + 124, y, Theme.CREAM_FAINT);
        Ui.dotCompat(g, panelX + 172, y + 3, 2, Theme.RASPBERRY);
        Ui.text(g, this.font, "Verkauft", panelX + 178, y, Theme.CREAM_FAINT);

        if (!statusText.isEmpty()) {
            Ui.right(g, this.font, statusText, panelX + PANEL_W - 10, y,
                    loading ? Theme.AMBER : Theme.CREAM_FAINT);
        }
    }

    // ------------------------------------------------------------------ Mathe

    private static int timeToX(long time, int cx, int cw, long from, long window) {
        double t = (double) (time - from) / Math.max(1, window);
        t = Math.max(0, Math.min(1, t));
        return cx + 1 + (int) Math.round(t * (cw - 3));
    }

    private static int valueToY(double value, int cy, int ch, double min, double span) {
        double t = (value - min) / Math.max(1e-9, span);
        t = Math.max(0, Math.min(1, t));
        return cy + 1 + (int) Math.round((1 - t) * (ch - 3));
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY) {
        for (int i = 0; i < RANGES.length; i++) {
            if (rangeChip(i).has(mouseX, mouseY)) {
                rangeIndex = i;
                return true;
            }
        }
        if (loadSalesButton().has(mouseX, mouseY)) {
            loadSales();
            return true;
        }
        if (backButton().has(mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
