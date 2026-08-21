package net.donutmarket.ui;

import net.donutmarket.config.DonutConfig;
import net.donutmarket.market.HistoryStore;
import net.donutmarket.market.ItemIndex;
import net.donutmarket.market.MarketService;
import net.donutmarket.market.PriceUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Preis nachschlagen, ohne einen Alarm anzulegen.
 *
 * Tippen, Gegenstand anklicken, und die Mod holt sofort den guenstigsten Preis
 * pro Stueck aus dem Auktionshaus. Genau derselbe Scan wie bei den Alarmen -
 * es wird also auch hier weitergeblaettert, statt einfach den ersten Treffer
 * zu nehmen.
 *
 * Das Ergebnis wandert nebenbei in die Preishistorie. Wer viel nachschlaegt,
 * fuettert damit ganz von selbst seine Verlaufsgraphen.
 */
public class LookupScreen extends BaseScreen {

    private static final int W = 324;
    private static final int H = 238;
    private static final int RESULT_ROWS = 5;
    private static final int RESULT_H = 17;

    private final Screen parent;

    private EditBox search;
    private List<ItemIndex.Entry> matches = new ArrayList<>();
    private int scroll = 0;

    private ItemIndex.Entry selected;
    private volatile boolean scanning = false;
    private volatile MarketService.ScanResult result;
    private volatile String error;

    private int px;
    private int py;

    public LookupScreen(Screen parent) {
        super(Component.literal("Preis prüfen"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- Layout

    private Ui.Rect panel() {
        return new Ui.Rect(px, py, W, H);
    }

    private Ui.Rect searchField() {
        return new Ui.Rect(px + 12, py + 40, W - 24, 20);
    }

    private Ui.Rect listArea() {
        return new Ui.Rect(px + 12, py + 66, W - 24, RESULT_ROWS * RESULT_H);
    }

    private Ui.Rect matchRow(int i) {
        Ui.Rect a = listArea();
        return new Ui.Rect(a.x(), a.y() + i * RESULT_H, a.w() - 4, RESULT_H - 2);
    }

    private Ui.Rect resultCard() {
        return new Ui.Rect(px + 12, py + 160, W - 24, 46);
    }

    private Ui.Rect createScanButton() {
        return new Ui.Rect(px + 12, py + H - 26, 92, 18);
    }

    private Ui.Rect graphButton() {
        return new Ui.Rect(px + 108, py + H - 26, 62, 18);
    }

    private Ui.Rect backButton() {
        return new Ui.Rect(px + W - 74, py + H - 26, 62, 18);
    }

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        Ui.Rect sf = searchField();
        search = new EditBox(this.font, sf.x() + 5, sf.y() + 6, sf.w() - 10, 12,
                Component.literal("Suche"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(Theme.CREAM);
        search.setHint(Component.literal("Gegenstand suchen, z.B. elytra"));
        search.setResponder(s -> {
            scroll = 0;
            refresh(s);
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        refresh("");
    }

    private void refresh(String q) {
        // Bei leerer Suche keine 1300 Eintraege zeigen - das hilft niemandem.
        matches = q == null || q.isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(ItemIndex.search(q, 60));
    }

    /** Startet den Scan fuer den angeklickten Gegenstand. */
    private void lookup(ItemIndex.Entry entry) {
        selected = entry;
        result = null;
        error = null;

        if (!DonutConfig.get().hasApiKey()) {
            error = "Kein API-Schlüssel hinterlegt.";
            return;
        }

        scanning = true;
        MarketService svc = MarketService.get();
        svc.runAsync(() -> {
            try {
                MarketService.ScanResult r = svc.scanCheapest(entry.id(), entry.path());
                if (r.found()) {
                    // Nebenbei die Historie fuettern - kostet nichts extra.
                    svc.history().record(entry.id(), r.bestPricePerUnit(), HistoryStore.Source.ASK);
                }
                result = r;
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                scanning = false;
            }
        });
    }

    // ---------------------------------------------------------------- Rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, Theme.SCRIM);
        Ui.Rect p = panel();
        Ui.card(g, p, Theme.GROUND);
        Ui.outline(g, p, Theme.LINE);
        Ui.gradient(g, new Ui.Rect(p.x() + 1, p.y() + 1, p.w() - 2, 30), Theme.HEADER, Theme.GROUND);
        Ui.rule(g, p.x() + 1, p.y() + 31, p.w() - 2, Theme.LINE);
        Ui.rule(g, p.x() + 1, p.y() + H - 32, p.w() - 2, Theme.LINE_SOFT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Ui.label(g, this.font, "preis prüfen", px + 12, py + 10, Theme.PISTACHIO);
        Ui.right(g, this.font, "günstigstes Angebot im Auktionshaus",
                px + W - 12, py + 10, Theme.CREAM_FAINT);

        // ---- Suchfeld ------------------------------------------------------
        Ui.Rect sf = searchField();
        Ui.card(g, sf, Theme.SURFACE_SUNK);
        Ui.outline(g, sf, search != null && search.isFocused() ? Theme.PISTACHIO : Theme.LINE_SOFT);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // ---- Trefferliste --------------------------------------------------
        drawMatches(g, mouseX, mouseY);

        // ---- Ergebnis ------------------------------------------------------
        drawResult(g);

        // ---- Fussleiste ----------------------------------------------------
        boolean hasItem = selected != null;
        Ui.Rect cs = createScanButton();
        if (hasItem) {
            Ui.primaryButton(g, this.font, cs, "Scan anlegen", cs.has(mouseX, mouseY), Theme.RASPBERRY);
        } else {
            Ui.card(g, cs, Theme.SURFACE);
            Ui.outline(g, cs, Theme.LINE_SOFT);
            Ui.center(g, this.font, "Scan anlegen", cs.centerX(), cs.centerY() - 4, Theme.CREAM_FAINT);
        }

        Ui.Rect gb = graphButton();
        Ui.ghostButton(g, this.font, gb, "Verlauf", gb.has(mouseX, mouseY));

        Ui.Rect bb = backButton();
        Ui.ghostButton(g, this.font, bb, "Zurück", bb.has(mouseX, mouseY));
    }

    private void drawMatches(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Ui.Rect area = listArea();
        Ui.card(g, area, Theme.SURFACE_SUNK);

        if (matches.isEmpty()) {
            String hint = search != null && search.getValue().isBlank()
                    ? "Tippe einen Namen ein."
                    : "Nichts gefunden.";
            Ui.center(g, this.font, hint, area.centerX(), area.centerY() - 4, Theme.CREAM_FAINT);
            return;
        }

        int maxScroll = Math.max(0, matches.size() - RESULT_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        HistoryStore hist = MarketService.get().history();

        for (int i = 0; i < RESULT_ROWS; i++) {
            int idx = scroll + i;
            if (idx >= matches.size()) {
                break;
            }
            Ui.Rect row = matchRow(i);
            if (row.bottom() > area.bottom()) {
                break;
            }
            ItemIndex.Entry e = matches.get(idx);
            boolean hovered = row.has(mouseX, mouseY);
            boolean isSelected = selected != null && selected.id().equals(e.id());

            if (isSelected) {
                Ui.card(g, row, Theme.mix(Theme.PISTACHIO, Theme.GROUND, 0.2f));
            } else if (hovered) {
                Ui.card(g, row, Theme.SURFACE_HI);
            }

            ItemStack st = e.stack();
            if (!st.isEmpty()) {
                g.item(st, row.x() + 2, row.y() - 1);
            }
            Ui.text(g, this.font, Ui.clip(this.font, e.display(), row.w() - 90),
                    row.x() + 22, row.y() + 4, isSelected || hovered ? Theme.CREAM : Theme.CREAM_DIM);

            // Kleiner Hinweis, wenn wir zu dem Gegenstand schon Daten haben
            if (hist.hasData(e.id())) {
                Ui.right(g, this.font, "Verlauf vorhanden", row.right() - 6, row.y() + 4, Theme.CREAM_FAINT);
            }
        }

        Ui.scrollbar(g, area.right() - 2, area.y(), area.h(), RESULT_ROWS, matches.size(),
                scroll, Theme.PISTACHIO_DEEP);
    }

    private void drawResult(GuiGraphicsExtractor g) {
        Ui.Rect card = resultCard();
        Ui.card(g, card, Theme.SURFACE);
        Ui.outline(g, card, Theme.LINE_SOFT);

        if (selected == null) {
            Ui.center(g, this.font, "Gegenstand anklicken, um den Preis zu holen.",
                    card.centerX(), card.centerY() - 4, Theme.CREAM_FAINT);
            return;
        }

        ItemStack st = selected.stack();
        if (!st.isEmpty()) {
            g.item(st, card.x() + 7, card.y() + 7);
        }
        Ui.text(g, this.font, Ui.clip(this.font, selected.display(), 130),
                card.x() + 28, card.y() + 6, Theme.CREAM);

        if (scanning) {
            Ui.text(g, this.font, "Auktionshaus wird durchsucht…",
                    card.x() + 28, card.y() + 19, Theme.CREAM_DIM);
            return;
        }
        if (error != null) {
            Ui.text(g, this.font, Ui.clip(this.font, error, card.w() - 34),
                    card.x() + 28, card.y() + 19, Theme.CHILI);
            return;
        }
        MarketService.ScanResult r = result;
        if (r == null) {
            Ui.text(g, this.font, "Bereit.", card.x() + 28, card.y() + 19, Theme.CREAM_FAINT);
            return;
        }
        if (!r.found()) {
            Ui.text(g, this.font, "Aktuell kein passendes Angebot im Auktionshaus.",
                    card.x() + 28, card.y() + 19, Theme.AMBER);
            Ui.text(g, this.font, r.pagesScanned() + " Seiten geprüft",
                    card.x() + 28, card.y() + 31, Theme.CREAM_FAINT);
            return;
        }

        // Der Preis ist die Antwort - also bekommt er das meiste Gewicht.
        String price = PriceUtil.full(r.bestPricePerUnit()) + " $";
        Ui.strong(g, this.font, price, card.x() + 28, card.y() + 18, Theme.PISTACHIO);
        Ui.text(g, this.font, "pro Stück", card.x() + 30 + this.font.width(price),
                card.y() + 18, Theme.CREAM_FAINT);

        String detail = r.bestCount() > 1
                ? "Günstigstes Angebot: " + r.bestCount() + "x für " + PriceUtil.compact(r.bestTotalPrice()) + " $"
                : "Einzelstück";
        if (r.seller() != null) {
            detail += " · " + r.seller();
        }
        Ui.text(g, this.font, Ui.clip(this.font, detail, card.w() - 34),
                card.x() + 28, card.y() + 31, Theme.CREAM_DIM);

        // Wie sicher ist die Zahl? Ehrliche Angabe statt blindem Vertrauen.
        Ui.right(g, this.font, r.pagesScanned() + " Seiten · " + r.listingsMatched() + " Treffer",
                card.right() - 6, card.y() + 6, Theme.CREAM_FAINT);

        // 24h-Vergleich, falls vorhanden
        HistoryStore.Stats s = MarketService.get().history()
                .stats(selected.id(), 86_400_000L);
        if (s.count() > 2) {
            String cmp = "24h: " + PriceUtil.compact(s.min()) + " – " + PriceUtil.compact(s.max());
            Ui.right(g, this.font, cmp, card.right() - 6, card.y() + 31, Theme.CREAM_FAINT);
        }
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    protected boolean onClick(double mouseX, double mouseY) {
        for (int i = 0; i < RESULT_ROWS; i++) {
            int idx = scroll + i;
            if (idx >= matches.size()) {
                break;
            }
            Ui.Rect row = matchRow(i);
            if (row.bottom() > listArea().bottom()) {
                break;
            }
            if (row.has(mouseX, mouseY)) {
                lookup(matches.get(idx));
                return true;
            }
        }
        if (selected != null && createScanButton().has(mouseX, mouseY)) {
            // Gefundenen Preis als Startwert mitgeben - spart das Abtippen.
            MarketService.ScanResult r = result;
            Double preset = r != null && r.found() ? r.bestPricePerUnit() : null;
            this.minecraft.gui.setScreen(new ScanEditorScreen(this, selected.id(), preset));
            return true;
        }
        if (selected != null && graphButton().has(mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new GraphScreen(this, selected.id(), null));
            return true;
        }
        if (backButton().has(mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listArea().has(mouseX, mouseY)) {
            scroll -= (int) Math.signum(scrollY);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, matches.size() - RESULT_ROWS)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
