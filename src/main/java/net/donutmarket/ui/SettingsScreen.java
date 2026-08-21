package net.donutmarket.ui;

import net.donutmarket.config.DonutConfig;
import net.donutmarket.market.ConnectionState;
import net.donutmarket.market.MarketService;
import net.donutmarket.notify.Notifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Einstellungen. Alles hier, keine Befehle, keine Datei von Hand bearbeiten.
 *
 * Der API-Zugang steht bewusst ganz oben: Ohne ihn tut der Scanner nichts, also
 * ist das die erste Frage, die die Oberflaeche stellt.
 */
public class SettingsScreen extends BaseScreen {

    private static final int W = 300;
    private static final int H = 244;

    private final Screen parent;

    private EditBox keyBox;
    private EditBox intervalBox;
    private int px;
    private int py;
    private String flash = "";

    public SettingsScreen(Screen parent) {
        super(Component.literal("Einstellungen"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- Layout

    private Ui.Rect panel() {
        return new Ui.Rect(px, py, W, H);
    }

    private Ui.Rect keyField() {
        return new Ui.Rect(px + 12, py + 46, W - 24, 20);
    }

    private Ui.Rect intervalField() {
        return new Ui.Rect(px + 12, py + 86, 46, 18);
    }

    private Ui.Rect row(int i) {
        return new Ui.Rect(px + 12, py + 118 + i * 19, W - 24, 17);
    }

    private Ui.Rect testSoundButton() {
        return new Ui.Rect(px + W - 82, py + 86, 70, 18);
    }

    private Ui.Rect debugButton() {
        return new Ui.Rect(px + 12, py + H - 26, 96, 18);
    }

    private Ui.Rect saveButton() {
        return new Ui.Rect(px + W - 86, py + H - 26, 74, 18);
    }

    @Override
    protected void init() {
        DonutConfig cfg = DonutConfig.get();
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        Ui.Rect kf = keyField();
        keyBox = new EditBox(this.font, kf.x() + 5, kf.y() + 6, kf.w() - 10, 12,
                Component.literal("API-Schlüssel"));
        keyBox.setBordered(false);
        keyBox.setMaxLength(200);
        keyBox.setTextColor(Theme.CREAM);
        keyBox.setHint(Component.literal("Schlüssel aus /api hier einfügen"));
        keyBox.setValue(cfg.apiKey == null ? "" : cfg.apiKey);
        addRenderableWidget(keyBox);

        Ui.Rect inf = intervalField();
        intervalBox = new EditBox(this.font, inf.x() + 5, inf.y() + 5, inf.w() - 10, 12,
                Component.literal("Intervall"));
        intervalBox.setBordered(false);
        intervalBox.setMaxLength(5);
        intervalBox.setTextColor(Theme.CREAM);
        intervalBox.setValue(String.valueOf(cfg.checkIntervalSeconds));
        addRenderableWidget(intervalBox);
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
        DonutConfig cfg = DonutConfig.get();

        Ui.label(g, this.font, "einstellungen", px + 12, py + 10, Theme.RASPBERRY);

        // Status rechts oben: sagt sofort, ob gerade etwas passiert
        String state;
        int stateColor;
        if (!cfg.hasApiKey()) {
            state = "kein Zugang";
            stateColor = Theme.AMBER;
        } else if (!ConnectionState.pollingAllowed()) {
            state = "pausiert";
            stateColor = Theme.CREAM_FAINT;
        } else if (ConnectionState.isOnDonutSmp()) {
            state = "aktiv";
            stateColor = Theme.PISTACHIO;
        } else {
            state = "aktiv im Hintergrund";
            stateColor = Theme.PISTACHIO_DEEP;
        }
        Ui.dotRight(g, this.font, state, px + W - 12, py + 10, stateColor);

        // ---- Zugang --------------------------------------------------------
        Ui.label(g, this.font, "api-schlüssel", px + 12, py + 38, Theme.CREAM_FAINT);
        Ui.Rect kf = keyField();
        Ui.card(g, kf, Theme.SURFACE_SUNK);
        Ui.outline(g, kf, keyBox != null && keyBox.isFocused() ? Theme.RASPBERRY : Theme.LINE_SOFT);

        // ---- Intervall -----------------------------------------------------
        Ui.label(g, this.font, "prüfen alle", px + 12, py + 76, Theme.CREAM_FAINT);
        Ui.Rect inf = intervalField();
        Ui.card(g, inf, Theme.SURFACE_SUNK);
        Ui.outline(g, inf, intervalBox != null && intervalBox.isFocused()
                ? Theme.RASPBERRY : Theme.LINE_SOFT);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        Ui.text(g, this.font, "Sekunden", inf.right() + 6, inf.centerY() - 4, Theme.CREAM_DIM);
        int free = MarketService.get().api().limiter().available();
        Ui.text(g, this.font, free + " Abfragen frei", inf.right() + 62, inf.centerY() - 4,
                free > 20 ? Theme.CREAM_FAINT : Theme.AMBER);

        Ui.Rect tb = testSoundButton();
        Ui.ghostButton(g, this.font, tb, "Ton testen", tb.has(mouseX, mouseY));

        // ---- Schalter ------------------------------------------------------
        Ui.toggleRow(g, this.font, row(0), "Ton bei Treffer", null,
                cfg.playSound, mouseX, mouseY, Theme.RASPBERRY);
        Ui.toggleRow(g, this.font, row(1), "Ton am Spiel vorbei",
                "hörbar auch alt-getabbt und im Hauptmenü",
                cfg.useSystemSound, mouseX, mouseY, Theme.RASPBERRY);
        Ui.toggleRow(g, this.font, row(2), "Nur auf DonutSMP prüfen",
                "sonst läuft der Scanner überall weiter",
                cfg.onlyWhileOnDonutSmp, mouseX, mouseY, Theme.RASPBERRY);
        Ui.toggleRow(g, this.font, row(3), "Verzauberte Angebote ignorieren", null,
                cfg.ignoreEnchanted, mouseX, mouseY, Theme.RASPBERRY);
        Ui.toggleRow(g, this.font, row(4), "Erst wieder melden, wenn der Preis rausläuft", null,
                cfg.rearmOnly, mouseX, mouseY, Theme.RASPBERRY);

        // ---- Fussleiste ----------------------------------------------------
        Ui.Rect db = debugButton();
        Ui.ghostButton(g, this.font, db, "API prüfen", db.has(mouseX, mouseY));

        Ui.Rect sb = saveButton();
        Ui.primaryButton(g, this.font, sb, "Speichern", sb.has(mouseX, mouseY), Theme.RASPBERRY);

        String msg = !flash.isEmpty() ? flash : MarketService.get().lastGlobalError();
        if (msg != null && !msg.isEmpty()) {
            Ui.text(g, this.font, Ui.clip(this.font, msg, W - 200), px + 114, py + H - 22,
                    flash.isEmpty() ? Theme.CHILI : Theme.CREAM_DIM);
        }
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    protected boolean onClick(double mouseX, double mouseY) {
        DonutConfig cfg = DonutConfig.get();

        if (testSoundButton().has(mouseX, mouseY)) {
            Notifier.playTestSound();
            return true;
        }
        if (row(0).has(mouseX, mouseY)) {
            cfg.playSound = !cfg.playSound;
            return true;
        }
        if (row(1).has(mouseX, mouseY)) {
            cfg.useSystemSound = !cfg.useSystemSound;
            return true;
        }
        if (row(2).has(mouseX, mouseY)) {
            cfg.onlyWhileOnDonutSmp = !cfg.onlyWhileOnDonutSmp;
            return true;
        }
        if (row(3).has(mouseX, mouseY)) {
            cfg.ignoreEnchanted = !cfg.ignoreEnchanted;
            return true;
        }
        if (row(4).has(mouseX, mouseY)) {
            cfg.rearmOnly = !cfg.rearmOnly;
            return true;
        }
        if (debugButton().has(mouseX, mouseY)) {
            runApiCheck();
            return true;
        }
        if (saveButton().has(mouseX, mouseY)) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Ersetzt den frueheren /dm raw Befehl. Holt eine Auktions- und eine
     * Transaktionsseite, schreibt sie in latest.log und meldet kurz, ob es
     * geklappt hat. Damit laesst sich pruefen, ob der Schluessel geht und ob
     * die Transactions-API Zeitstempel liefert.
     */
    private void runApiCheck() {
        flash = "prüfe…";
        MarketService svc = MarketService.get();
        svc.runAsync(() -> {
            try {
                String a = svc.api().requestRaw("/auction/list/1",
                        "{\"search\":\"elytra\",\"sort\":\"lowest_price\"}");
                System.out.println("[DonutMarket] RAW auction/list/1 -> " + a);
                String t = svc.api().requestRaw("/auction/transactions/1",
                        "{\"search\":\"elytra\",\"sort\":\"recently_listed\"}");
                System.out.println("[DonutMarket] RAW auction/transactions/1 -> " + t);
                flash = "Zugang funktioniert. Antworten stehen in latest.log";
                Notifier.info("API-Prüfung erfolgreich, Rohdaten stehen in latest.log");
            } catch (Exception e) {
                flash = "";
                Notifier.error("API-Prüfung fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    private void save() {
        DonutConfig cfg = DonutConfig.get();
        cfg.apiKey = keyBox.getValue().trim();
        try {
            cfg.checkIntervalSeconds = Math.max(15, Integer.parseInt(intervalBox.getValue().trim()));
        } catch (NumberFormatException ignored) {
            // ungueltige Eingabe: alten Wert behalten
        }
        cfg.save();
        MarketService.get().checkAllNow();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
