package net.donutmarket.ui;

import net.donutmarket.market.ItemIndex;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Item-Auswahl: suchen und anklicken.
 *
 * Jedes Item im Spiel ist hier auffindbar - gesucht wird gleichzeitig über
 * Anzeigename, Registry-Pfad und volle ID, wobei Treffer am Wortanfang
 * vorsortiert werden. "tot" liefert also wirklich das Totem zuerst.
 */
public class ItemPickerScreen extends BaseScreen {

    private static final int COLS = 11;
    private static final int CELL = 20;
    private static final int PANEL_W = COLS * CELL + 22;
    private static final int PANEL_H = 214;
    private static final int MAX_RESULTS = 600;

    private final Screen parent;
    private final Consumer<ItemIndex.Entry> onPick;

    private EditBox search;
    private List<ItemIndex.Entry> results = new ArrayList<>();
    private int scroll = 0;
    private int panelX;
    private int panelY;

    public ItemPickerScreen(Screen parent, Consumer<ItemIndex.Entry> onPick) {
        super(Component.literal("Item wählen"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        search = new EditBox(this.font, panelX + 10, panelY + 30, PANEL_W - 20, 18,
                Component.literal("Suche"));
        search.setHint(Component.literal("Item suchen, z.B. totem, elytra, spawner..."));
        search.setMaxLength(64);
        search.setResponder(s -> {
            scroll = 0;
            refresh(s);
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        refresh("");
    }

    private Ui.Rect cancelButton() {
        return new Ui.Rect(panelX + PANEL_W - 82, panelY + PANEL_H - 25, 70, 18);
    }

    private void refresh(String q) {
        results = new ArrayList<>(ItemIndex.search(q, MAX_RESULTS));
    }

    private int gridTop() {
        return panelY + 54;
    }

    private int gridBottom() {
        return panelY + PANEL_H - 30;
    }

    private int rowsFitting() {
        return Math.max(1, (gridBottom() - gridTop()) / CELL);
    }

    private int totalRows() {
        return (results.size() + COLS - 1) / COLS;
    }

    // ------------------------------------------------------------------ Rendering

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, Theme.SCRIM);
        Ui.panelCompat(g, panelX, panelY, PANEL_W, PANEL_H, Theme.GROUND, Theme.LINE);
        g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + 24, Theme.HEADER);
        g.fill(panelX + 1, panelY + 24, panelX + PANEL_W - 1, panelY + 25, Theme.RASPBERRY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        Ui.strong(g, this.font, "Item wählen", panelX + 10, panelY + 8, Theme.RASPBERRY);
        Ui.right(g, this.font, results.size() + " Treffer", panelX + PANEL_W - 10, panelY + 8, Theme.CREAM_FAINT);

        int top = gridTop();
        int rows = rowsFitting();
        int maxScroll = Math.max(0, totalRows() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ItemIndex.Entry hovered = null;
        int hx = 0;
        int hy = 0;

        for (int r = 0; r < rows; r++) {
            int rowIdx = scroll + r;
            for (int c = 0; c < COLS; c++) {
                int idx = rowIdx * COLS + c;
                if (idx >= results.size()) {
                    break;
                }
                int x = panelX + 11 + c * CELL;
                int y = top + r * CELL;
                if (y + CELL > gridBottom()) {
                    break;
                }
                ItemIndex.Entry e = results.get(idx);
                boolean hov = Ui.inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2);

                g.fill(x, y, x + CELL - 2, y + CELL - 2, hov ? Theme.SURFACE_HI : Theme.SURFACE);
                if (hov) {
                    Ui.outlineCompat(g, x, y, CELL - 2, CELL - 2, Theme.RASPBERRY);
                    hovered = e;
                    hx = mouseX;
                    hy = mouseY;
                }
                g.item(e.stack(), x + 1, y + 1);
            }
        }

        if (results.isEmpty()) {
            Ui.center(g, this.font, "Nichts gefunden.", panelX + PANEL_W / 2, top + 30, Theme.CREAM_DIM);
        }

        if (maxScroll > 0) {
            int trackX = panelX + PANEL_W - 8;
            int trackTop = top;
            int trackH = gridBottom() - top;
            g.fill(trackX, trackTop, trackX + 3, trackTop + trackH, Theme.LINE_SOFT);
            int thumbH = Math.max(14, trackH * rows / Math.max(1, totalRows()));
            int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, Theme.RASPBERRY_DEEP);
        }

        Ui.Rect cb = cancelButton();
        Ui.ghostButton(g, this.font, cb, "Abbrechen", cb.has(mouseX, mouseY));

        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.display()));
            lines.add(Component.literal(hovered.id()));
            Ui.tooltip(g, this.font, lines, hx, hy, this.width, this.height);
        }
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    protected boolean onClick(double mouseX, double mouseY) {
        if (cancelButton().has(mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        int top = gridTop();
        int rows = rowsFitting();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                if (idx >= results.size()) {
                    break;
                }
                int x = panelX + 11 + c * CELL;
                int y = top + r * CELL;
                if (y + CELL > gridBottom()) {
                    break;
                }
                if (Ui.inside((int) mouseX, (int) mouseY, x, y, CELL - 2, CELL - 2)) {
                    ItemIndex.Entry picked = results.get(idx);
                    this.minecraft.gui.setScreen(parent);
                    if (onPick != null) {
                        onPick.accept(picked);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows() - rowsFitting())));
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
