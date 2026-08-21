package net.donutmarket.ui;

import net.donutmarket.config.DonutConfig;
import net.donutmarket.market.Alert;
import net.donutmarket.market.ConnectionState;
import net.donutmarket.market.ItemIndex;
import net.donutmarket.market.MarketService;
import net.donutmarket.market.PriceUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hauptfenster des Auction Scanners. Wird mit /auctionscanner geoeffnet.
 *
 * Alles wird hier eingestellt - es gibt keine Befehle zum Konfigurieren.
 *
 * Aufbau: Kopfzeile mit Status, darunter die Beobachtungsliste als Karten.
 * Jede Karte zeigt Item, Ist-Preis, Ziel und die Zielskala. Unten eine Leiste
 * mit "Neuer Scan" und "Einstellungen".
 */
public class ScannerScreen extends BaseScreen {

    private static final int W = 348;
    private static final int H = 240;
    private static final int ROW_H = 34;

    private final Screen parent;

    private int px;
    private int py;
    private int scroll = 0;
    private List<Alert> rows = new ArrayList<>();

    public ScannerScreen(Screen parent) {
        super(Component.literal("Auction Scanner"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- Layout

    private Ui.Rect panel() {
        return new Ui.Rect(px, py, W, H);
    }

    private Ui.Rect listArea() {
        return new Ui.Rect(px + 8, py + 62, W - 16, H - 62 - 34);
    }

    /** Sieht aus wie ein Suchfeld, oeffnet die Preis-Abfrage. */
    private Ui.Rect lookupField() {
        return new Ui.Rect(px + 10, py + 38, W - 130, 18);
    }

    private int visibleRows() {
        return Math.max(1, listArea().h() / ROW_H);
    }

    private Ui.Rect rowRect(int i) {
        Ui.Rect a = listArea();
        return new Ui.Rect(a.x(), a.y() + i * ROW_H, a.w() - 4, ROW_H - 3);
    }

    private Ui.Rect newScanButton() {
        return new Ui.Rect(px + 8, py + H - 26, 96, 18);
    }

    private Ui.Rect settingsButton() {
        return new Ui.Rect(px + 108, py + H - 26, 82, 18);
    }

    private Ui.Rect refreshButton() {
        return new Ui.Rect(px + W - 148, py + H - 26, 62, 18);
    }

    private Ui.Rect closeButton() {
        return new Ui.Rect(px + W - 82, py + H - 26, 74, 18);
    }

    /** Graph-Knopf innerhalb einer Zeile. */
    private Ui.Rect rowGraph(Ui.Rect row) {
        return new Ui.Rect(row.right() - 40, row.y() + 4, 36, 12);
    }

    /** Ein/Aus-Schalter innerhalb einer Zeile. */
    private Ui.Rect rowToggle(Ui.Rect row) {
        return new Ui.Rect(row.right() - 26, row.bottom() - 16, 22, 12);
    }

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;
        rebuild();
    }

    private void rebuild() {
        rows = new ArrayList<>(MarketService.get().alerts());
    }

    // ---------------------------------------------------------------- Rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, Theme.SCRIM);

        Ui.Rect p = panel();
        Ui.card(g, p, Theme.GROUND);
        Ui.outline(g, p, Theme.LINE);

        // Kopfbereich mit leichtem Verlauf, damit der Titel Gewicht bekommt
        Ui.gradient(g, new Ui.Rect(p.x() + 1, p.y() + 1, p.w() - 2, 52), Theme.HEADER, Theme.GROUND);
        Ui.rule(g, p.x() + 1, p.y() + 53, p.w() - 2, Theme.LINE);

        // Fussleiste
        Ui.rule(g, p.x() + 1, p.y() + H - 32, p.w() - 2, Theme.LINE_SOFT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        rebuild();
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        drawHeader(g, mouseX, mouseY);
        drawFooter(g, mouseX, mouseY);

        if (!DonutConfig.get().hasApiKey()) {
            drawNeedsKey(g, mouseX, mouseY);
            return;
        }
        if (rows.isEmpty()) {
            drawEmpty(g);
            return;
        }
        drawList(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Ui.label(g, this.font, "auction scanner", px + 10, py + 9, Theme.RASPBERRY);

        MarketService svc = MarketService.get();
        String state;
        int color;
        if (!DonutConfig.get().hasApiKey()) {
            state = "kein Zugang";
            color = Theme.AMBER;
        } else if (!ConnectionState.pollingAllowed()) {
            state = "pausiert";
            color = Theme.CREAM_FAINT;
        } else {
            state = "beobachtet";
            color = Theme.PISTACHIO;
        }
        Ui.dotRight(g, this.font, state, px + W - 10, py + 9, color);

        // Suchfeld-Optik: sieht aus wie eine Eingabe, ist aber ein Knopf. Ein
        // echtes Feld hier haette zwei Sucheingaben im selben Fenster bedeutet.
        Ui.Rect lf = lookupField();
        boolean lh = lf.has(mouseX, mouseY);
        Ui.card(g, lf, lh ? Theme.SURFACE_HI : Theme.SURFACE_SUNK);
        Ui.outline(g, lf, lh ? Theme.PISTACHIO : Theme.LINE_SOFT);
        // Kleine Lupe aus Rechtecken
        int gx = lf.x() + 7;
        int gy = lf.centerY() - 3;
        Ui.outlineCompat(g, gx, gy, 6, 6, lh ? Theme.PISTACHIO : Theme.CREAM_FAINT);
        g.fill(gx + 6, gy + 6, gx + 8, gy + 8, lh ? Theme.PISTACHIO : Theme.CREAM_FAINT);
        Ui.text(g, this.font, "Preis eines Gegenstands prüfen…", lf.x() + 20, lf.centerY() - 4,
                lh ? Theme.CREAM : Theme.CREAM_FAINT);

        int active = 0;
        for (Alert a : rows) {
            if (a.enabled) {
                active++;
            }
        }
        Ui.right(g, this.font, active + " von " + rows.size() + " aktiv",
                px + W - 10, py + 26, Theme.CREAM_DIM);
        Ui.right(g, this.font, svc.history().totalPoints() + " Preise gesammelt",
                px + W - 10, py + 42, Theme.CREAM_FAINT);
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int vis = visibleRows();
        int maxScroll = Math.max(0, rows.size() - vis);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        Ui.Rect area = listArea();
        List<Component> tip = null;

        for (int i = 0; i < vis; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) {
                break;
            }
            Ui.Rect row = rowRect(i);
            if (row.bottom() > area.bottom()) {
                break; // manuelles Clipping
            }
            List<Component> t = drawRow(g, rows.get(idx), row, mouseX, mouseY);
            if (t != null) {
                tip = t;
            }
        }

        Ui.scrollbar(g, area.right(), area.y(), area.h(), vis, rows.size(), scroll, Theme.RASPBERRY_DEEP);

        if (tip != null) {
            Ui.tooltip(g, this.font, tip, mouseX, mouseY, this.width, this.height);
        }
    }

    private List<Component> drawRow(GuiGraphicsExtractor g, Alert a, Ui.Rect row, int mouseX, int mouseY) {
        boolean hovered = row.has(mouseX, mouseY);
        boolean sell = a.mode == Alert.Mode.SELL_ABOVE;
        int accent = Theme.forDirection(sell);

        Ui.card(g, row, hovered ? Theme.SURFACE_HI : Theme.SURFACE);
        // Farbkante links kodiert die Richtung
        g.fill(row.x(), row.y() + 1, row.x() + 2, row.bottom() - 1,
                a.enabled ? accent : Theme.LINE);

        // Item
        ItemIndex.Entry entry = ItemIndex.find(a.itemId);
        ItemStack stack = entry != null ? entry.stack() : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            g.item(stack, row.x() + 7, row.y() + 4);
        }

        String name = entry != null ? entry.display() : a.itemId;
        Ui.text(g, this.font, Ui.clip(this.font, name, 116), row.x() + 28, row.y() + 4,
                a.enabled ? Theme.CREAM : Theme.CREAM_FAINT);

        // Regel in Klartext, nicht als Symbol allein
        String rule = (sell ? "ab " : "bis ") + PriceUtil.compact(a.targetPrice) + " $";
        Ui.text(g, this.font, rule, row.x() + 28, row.y() + 16, accent);

        // Ist-Preis, rechtsbuendig fuer eine saubere Zahlenspalte
        int numRight = row.right() - 46;
        if (a.lastPrice != null) {
            Ui.strong(g, this.font, PriceUtil.compact(a.lastPrice) + " $", 
                    numRight - this.font.width(PriceUtil.compact(a.lastPrice) + " $"), row.y() + 4,
                    Theme.forPrice(sell, a.lastPrice, a.targetPrice));
            String dist = PriceUtil.percent(a.targetPrice, a.lastPrice);
            Ui.right(g, this.font, dist, numRight, row.y() + 16, Theme.CREAM_FAINT);
        } else if (a.checking) {
            Ui.right(g, this.font, "prüft…", numRight, row.y() + 10, Theme.CREAM_DIM);
        } else if (a.lastError != null) {
            Ui.right(g, this.font, "Fehler", numRight, row.y() + 10, Theme.CHILI);
        } else {
            Ui.right(g, this.font, "noch kein Preis", numRight, row.y() + 10, Theme.CREAM_FAINT);
        }

        // Zielskala
        if (a.lastPrice != null) {
            Ui.targetGauge(g, new Ui.Rect(row.x() + 28, row.bottom() - 8, row.w() - 78, 4),
                    a.lastPrice, a.targetPrice, sell);
        }

        // Zeilenaktionen
        Ui.Rect gr = rowGraph(row);
        Ui.chip(g, this.font, gr, "Verlauf", false, gr.has(mouseX, mouseY), accent);

        Ui.Rect tg = rowToggle(row);
        Ui.toggle(g, tg, a.enabled, tg.has(mouseX, mouseY), accent);

        if (hovered && !gr.has(mouseX, mouseY) && !tg.has(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(name));
            lines.add(Component.literal(sell
                    ? "Meldung ab " + PriceUtil.full(a.targetPrice) + " $ pro Stück"
                    : "Meldung bis " + PriceUtil.full(a.targetPrice) + " $ pro Stück"));
            if (a.lastPrice != null) {
                lines.add(Component.literal("Günstigstes Angebot: " + PriceUtil.full(a.lastPrice) + " $"
                        + (a.lastCount > 1 ? "  (" + a.lastCount + "x-Stapel)" : "")));
            }
            if (a.lastError != null) {
                lines.add(Component.literal(a.lastError));
            }
            lines.add(Component.literal("Klicken zum Bearbeiten"));
            return lines;
        }
        return null;
    }

    private void drawEmpty(GuiGraphicsExtractor g) {
        Ui.Rect a = listArea();
        int cy = a.y() + a.h() / 2 - 14;
        Ui.center(g, this.font, "Noch nichts im Blick.", a.centerX(), cy, Theme.CREAM_DIM);
        Ui.center(g, this.font, "Lege einen Scan an und die Mod sagt dir,",
                a.centerX(), cy + 13, Theme.CREAM_FAINT);
        Ui.center(g, this.font, "sobald dein Preis erreicht ist.",
                a.centerX(), cy + 23, Theme.CREAM_FAINT);
    }

    private void drawNeedsKey(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Ui.Rect a = listArea();
        int cy = a.y() + a.h() / 2 - 20;
        Ui.center(g, this.font, "Der Scanner braucht deinen API-Zugang.", a.centerX(), cy, Theme.AMBER);
        Ui.center(g, this.font, "Führe auf DonutSMP /api aus und trage den",
                a.centerX(), cy + 14, Theme.CREAM_FAINT);
        Ui.center(g, this.font, "Schlüssel in den Einstellungen ein.",
                a.centerX(), cy + 24, Theme.CREAM_FAINT);
    }

    private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Ui.Rect nb = newScanButton();
        Ui.primaryButton(g, this.font, nb, "+ Neuer Scan", nb.has(mouseX, mouseY), Theme.RASPBERRY);

        Ui.Rect sb = settingsButton();
        Ui.ghostButton(g, this.font, sb, "Einstellungen", sb.has(mouseX, mouseY));

        Ui.Rect rb = refreshButton();
        Ui.ghostButton(g, this.font, rb, "Jetzt", rb.has(mouseX, mouseY));

        Ui.Rect cb = closeButton();
        Ui.ghostButton(g, this.font, cb, "Schließen", cb.has(mouseX, mouseY));
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    protected boolean onClick(double mouseX, double mouseY) {
        if (lookupField().has(mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new LookupScreen(this));
            return true;
        }
        if (newScanButton().has(mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new ScanEditorScreen(this, null));
            return true;
        }
        if (settingsButton().has(mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new SettingsScreen(this));
            return true;
        }
        if (refreshButton().has(mouseX, mouseY)) {
            MarketService.get().checkAllNow();
            return true;
        }
        if (closeButton().has(mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        int vis = visibleRows();
        for (int i = 0; i < vis; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) {
                break;
            }
            Ui.Rect row = rowRect(i);
            if (row.bottom() > listArea().bottom()) {
                break;
            }
            if (!row.has(mouseX, mouseY)) {
                continue;
            }
            Alert a = rows.get(idx);

            if (rowToggle(row).has(mouseX, mouseY)) {
                a.enabled = !a.enabled;
                a.armed = true;
                MarketService.get().saveAlerts();
                return true;
            }
            if (rowGraph(row).has(mouseX, mouseY)) {
                this.minecraft.gui.setScreen(new GraphScreen(this, a.itemId, a));
                return true;
            }
            this.minecraft.gui.setScreen(new ScanEditorScreen(this, a));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listArea().has(mouseX, mouseY)) {
            scroll -= (int) Math.signum(scrollY);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        MarketService.get().saveAlerts();
        this.minecraft.gui.setScreen(parent);
    }
}
