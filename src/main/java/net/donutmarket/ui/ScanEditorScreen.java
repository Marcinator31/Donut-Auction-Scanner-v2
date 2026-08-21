package net.donutmarket.ui;

import net.donutmarket.market.Alert;
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
 * Scan anlegen oder aendern. Hier wird alles eingestellt, keine Befehle.
 *
 * Die Richtung ist als zwei grosse Auswahlkarten umgesetzt, nicht als
 * Umschaltknopf - bei einem Umschalter muss man raten, was der andere Zustand
 * waere. Darunter steht in einem Satz im Klartext, was passieren wird. Das ist
 * die wichtigste Zeile des Fensters: Wer sie liest, kann sich nicht mehr
 * vertun.
 */
public class ScanEditorScreen extends BaseScreen {

    private static final int W = 300;
    private static final int H = 240;

    /** Schnellwerte. Bewusst an dem orientiert, was auf DonutSMP real gehandelt wird. */
    private static final String[] PRESETS = {"250k", "1m", "10m", "100m", "400m", "1b"};

    private final Screen parent;
    private final Alert existing;

    private String itemId;
    private boolean sellAbove;
    private boolean repeat;
    private boolean enabled;

    private EditBox priceBox;
    private int px;
    private int py;

    /** Startwert fuer das Preisfeld, z.B. der gerade nachgeschlagene Preis. */
    private final Double presetPrice;

    public ScanEditorScreen(Screen parent, Alert existing) {
        this(parent, existing, null, null);
    }

    /** Neuer Scan mit vorbelegtem Gegenstand - kommt aus "Preis prüfen". */
    public ScanEditorScreen(Screen parent, String presetItemId, Double presetPrice) {
        this(parent, null, presetItemId, presetPrice);
    }

    private ScanEditorScreen(Screen parent, Alert existing, String presetItemId, Double presetPrice) {
        super(Component.literal(existing == null ? "Neuer Scan" : "Scan ändern"));
        this.parent = parent;
        this.existing = existing;
        this.presetPrice = presetPrice;
        this.itemId = existing != null ? existing.itemId
                : presetItemId != null ? presetItemId : "";
        this.sellAbove = existing == null || existing.mode == Alert.Mode.SELL_ABOVE;
        this.repeat = existing == null || existing.repeat;
        this.enabled = existing == null || existing.enabled;
    }

    // ---------------------------------------------------------------- Layout

    private Ui.Rect panel() {
        return new Ui.Rect(px, py, W, H);
    }

    private Ui.Rect itemSlot() {
        return new Ui.Rect(px + 12, py + 44, 26, 26);
    }

    private Ui.Rect itemPick() {
        return new Ui.Rect(px + 44, py + 44, W - 56, 26);
    }

    private Ui.Rect priceField() {
        return new Ui.Rect(px + 12, py + 90, W - 24, 20);
    }

    private Ui.Rect preset(int i) {
        int w = (W - 24 - 5 * 3) / 6;
        return new Ui.Rect(px + 12 + i * (w + 3), py + 114, w, 14);
    }

    private Ui.Rect directionCard(boolean sell) {
        int w = (W - 27) / 2;
        return new Ui.Rect(px + 12 + (sell ? 0 : w + 3), py + 142, w, 30);
    }

    private Ui.Rect repeatRow() {
        return new Ui.Rect(px + 12, py + 194, W - 24, 16);
    }

    private Ui.Rect deleteButton() {
        return new Ui.Rect(px + 12, py + H - 26, 60, 18);
    }

    private Ui.Rect cancelButton() {
        return new Ui.Rect(px + W - 152, py + H - 26, 62, 18);
    }

    private Ui.Rect saveButton() {
        return new Ui.Rect(px + W - 86, py + H - 26, 74, 18);
    }

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        Ui.Rect pf = priceField();
        priceBox = new EditBox(this.font, pf.x() + 5, pf.y() + 6, pf.w() - 10, 12,
                Component.literal("Preis"));
        priceBox.setBordered(false);
        priceBox.setMaxLength(24);
        priceBox.setTextColor(Theme.CREAM);
        priceBox.setHint(Component.literal("400m"));
        if (existing != null) {
            priceBox.setValue(PriceUtil.compact(existing.targetPrice));
        } else if (presetPrice != null && presetPrice > 0) {
            priceBox.setValue(PriceUtil.compact(presetPrice));
        }
        addRenderableWidget(priceBox);
        setInitialFocus(priceBox);
    }

    private Double price() {
        return PriceUtil.parse(priceBox == null ? "" : priceBox.getValue());
    }

    private boolean canSave() {
        Double p = price();
        return itemId != null && !itemId.isBlank() && p != null && p > 0;
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
        int accent = Theme.forDirection(sellAbove);

        Ui.label(g, this.font, existing == null ? "neuer scan" : "scan ändern",
                px + 12, py + 10, accent);

        // ---- Item ----------------------------------------------------------
        Ui.label(g, this.font, "gegenstand", px + 12, py + 36, Theme.CREAM_FAINT);

        ItemIndex.Entry entry = ItemIndex.find(itemId);
        ItemStack stack = entry != null ? entry.stack() : ItemStack.EMPTY;
        Ui.Rect slot = itemSlot();
        Ui.slot(g, slot, stack, slot.has(mouseX, mouseY));

        Ui.Rect pick = itemPick();
        boolean pickHover = pick.has(mouseX, mouseY);
        Ui.card(g, pick, pickHover ? Theme.SURFACE_HI : Theme.SURFACE);
        Ui.outline(g, pick, pickHover ? Theme.LINE : Theme.LINE_SOFT);
        if (entry == null) {
            Ui.text(g, this.font, "Gegenstand wählen…", pick.x() + 7, pick.centerY() - 4, Theme.CREAM_FAINT);
        } else {
            Ui.text(g, this.font, Ui.clip(this.font, entry.display(), pick.w() - 14),
                    pick.x() + 7, pick.centerY() - 8, Theme.CREAM);
            Ui.text(g, this.font, entry.id(), pick.x() + 7, pick.centerY() + 1, Theme.CREAM_FAINT);
        }

        // ---- Preis ---------------------------------------------------------
        Ui.label(g, this.font, "zielpreis pro stück", px + 12, py + 80, Theme.CREAM_FAINT);

        Ui.Rect pf = priceField();
        Ui.card(g, pf, Theme.SURFACE_SUNK);
        Ui.outline(g, pf, priceBox != null && priceBox.isFocused() ? accent : Theme.LINE_SOFT);
        Ui.right(g, this.font, "$", pf.right() - 6, pf.y() + 6, Theme.CREAM_FAINT);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        for (int i = 0; i < PRESETS.length; i++) {
            Ui.Rect r = preset(i);
            boolean sel = priceBox != null && PRESETS[i].equalsIgnoreCase(priceBox.getValue().trim());
            Ui.chip(g, this.font, r, PRESETS[i], sel, r.has(mouseX, mouseY), accent);
        }

        // ---- Richtung ------------------------------------------------------
        Ui.label(g, this.font, "wann melden", px + 12, py + 134, Theme.CREAM_FAINT);
        drawDirectionCard(g, directionCard(true), true, mouseX, mouseY);
        drawDirectionCard(g, directionCard(false), false, mouseX, mouseY);

        // ---- Klartext-Vorschau --------------------------------------------
        Double p = price();
        String preview;
        int previewColor;
        if (entry == null) {
            preview = "Wähle zuerst einen Gegenstand.";
            previewColor = Theme.CREAM_FAINT;
        } else if (p == null || p <= 0) {
            preview = "Trage einen Preis ein, z.B. 400m.";
            previewColor = Theme.CREAM_FAINT;
        } else {
            preview = "Meldung, sobald " + entry.display() + " "
                    + PriceUtil.full(p) + " $ oder " + (sellAbove ? "mehr" : "weniger") + " kostet.";
            previewColor = accent;
        }
        Ui.text(g, this.font, Ui.clip(this.font, preview, W - 24), px + 12, py + 178, previewColor);

        // ---- Optionen ------------------------------------------------------
        Ui.toggleRow(g, this.font, repeatRow(),
                repeat ? "Immer wieder melden" : "Nur einmal melden",
                repeat ? "Bleibt aktiv, auch nach dem ersten Treffer" : "Schaltet sich danach selbst ab",
                repeat, mouseX, mouseY, accent);

        // ---- Fussleiste ----------------------------------------------------
        if (existing != null) {
            Ui.Rect db = deleteButton();
            Ui.ghostButton(g, this.font, db, "Löschen", db.has(mouseX, mouseY));
        }
        Ui.Rect cb = cancelButton();
        Ui.ghostButton(g, this.font, cb, "Abbrechen", cb.has(mouseX, mouseY));

        Ui.Rect sb = saveButton();
        if (canSave()) {
            Ui.primaryButton(g, this.font, sb, "Speichern", sb.has(mouseX, mouseY), accent);
        } else {
            Ui.card(g, sb, Theme.SURFACE);
            Ui.outline(g, sb, Theme.LINE_SOFT);
            Ui.center(g, this.font, "Speichern", sb.centerX(), sb.centerY() - 4, Theme.CREAM_FAINT);
        }

        if (slot.has(mouseX, mouseY) && entry != null) {
            List<Component> t = new ArrayList<>();
            t.add(Component.literal(entry.display()));
            t.add(Component.literal(entry.id()));
            Ui.tooltip(g, this.font, t, mouseX, mouseY, this.width, this.height);
        }
    }

    private void drawDirectionCard(GuiGraphicsExtractor g, Ui.Rect r, boolean sell, int mouseX, int mouseY) {
        boolean selected = sellAbove == sell;
        boolean hovered = r.has(mouseX, mouseY);
        int accent = Theme.forDirection(sell);

        Ui.card(g, r, selected ? Theme.mix(accent, Theme.GROUND, 0.22f)
                : hovered ? Theme.SURFACE_HI : Theme.SURFACE);
        Ui.outline(g, r, selected ? accent : hovered ? Theme.LINE : Theme.LINE_SOFT);

        String head = sell ? "Preis erreicht oder übersteigt" : "Preis erreicht oder fällt darunter";
        String sub = sell ? "zum Verkaufen" : "zum Kaufen";

        Ui.text(g, this.font, Ui.clip(this.font, head, r.w() - 10), r.x() + 5, r.y() + 5,
                selected ? Theme.CREAM : Theme.CREAM_DIM);
        Ui.text(g, this.font, sub, r.x() + 5, r.y() + 17, selected ? accent : Theme.CREAM_FAINT);
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    protected boolean onClick(double mouseX, double mouseY) {
        if (itemSlot().has(mouseX, mouseY) || itemPick().has(mouseX, mouseY)) {
            this.minecraft.gui.setScreen(new ItemPickerScreen(this, picked -> this.itemId = picked.id()));
            return true;
        }
        for (int i = 0; i < PRESETS.length; i++) {
            if (preset(i).has(mouseX, mouseY)) {
                priceBox.setValue(PRESETS[i]);
                return true;
            }
        }
        if (directionCard(true).has(mouseX, mouseY)) {
            sellAbove = true;
            return true;
        }
        if (directionCard(false).has(mouseX, mouseY)) {
            sellAbove = false;
            return true;
        }
        if (repeatRow().has(mouseX, mouseY)) {
            repeat = !repeat;
            return true;
        }
        if (existing != null && deleteButton().has(mouseX, mouseY)) {
            MarketService.get().removeAlert(existing);
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        if (cancelButton().has(mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        if (saveButton().has(mouseX, mouseY)) {
            save();
            return true;
        }
        return false;
    }

    private void save() {
        if (!canSave()) {
            return;
        }
        Alert.Mode mode = sellAbove ? Alert.Mode.SELL_ABOVE : Alert.Mode.BUY_BELOW;
        Alert target = existing;
        if (target == null) {
            target = MarketService.get().addAlert(itemId, price(), mode);
        } else {
            target.itemId = itemId;
            target.targetPrice = price();
            target.mode = mode;
        }
        target.repeat = repeat;
        target.enabled = enabled;
        target.armed = true;
        target.lastPrice = null;
        MarketService.get().saveAlerts();
        MarketService.get().checkNowAsync(target);
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
