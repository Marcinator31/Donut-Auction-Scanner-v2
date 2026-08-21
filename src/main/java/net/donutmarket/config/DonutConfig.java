package net.donutmarket.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Einstellungen der Mod. Liegt unter config/donutmarket/config.json.
 *
 * Der API-Key wird im Klartext gespeichert - genau wie bei allen anderen
 * DonutSMP-Mods auch. Teile die Datei also nicht und streame sie nicht.
 */
public final class DonutConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** API-Key aus dem Ingame-Befehl /api auf DonutSMP. */
    public String apiKey = "";

    /** Wie oft (Sekunden) ein einzelner Alarm geprüft wird. */
    public int checkIntervalSeconds = 60;

    /**
     * Selbst gesetztes Limit. DonutSMP erlaubt 250 Requests/Minute pro Key.
     * Wir bleiben bewusst deutlich darunter, damit noch Luft für andere Mods
     * bleibt, die denselben Key benutzen.
     */
    public int maxRequestsPerMinute = 100;

    /** Wie viele Auktionsseiten pro Preis-Check maximal durchgeblättert werden. */
    public int maxPagesPerScan = 4;

    /**
     * Wenn auf einer Seite kein günstigerer Treffer mehr auftaucht, brechen wir
     * nach so vielen Seiten ab. Spart massiv Requests.
     */
    public int pagesWithoutImprovement = 2;

    /** Wie lange Preis-Historie lokal aufgehoben wird (Stunden). */
    public int historyRetentionHours = 168;

    /** Ton bei Alarm abspielen. */
    public boolean playSound = true;

    /** Anzahl der "Ding"s. */
    public int soundRepeats = 3;

    /**
     * Zusätzlicher Ton, der an Minecrafts Audio-Engine vorbei direkt über die
     * Soundkarte läuft.
     *
     * Wichtig fürs AFK-Warten: Minecraft hält die Sound-Engine an, sobald es
     * die Singleplayer-Welt pausiert - und das passiert standardmäßig beim
     * Verlust des Fensterfokus. Der normale Minecraft-Ton kann dann ausbleiben.
     * Dieser hier kommt immer, auch alt-getabbt und auch im Hauptmenü.
     */
    public boolean useSystemSound = true;

    /** Lautstärke des System-Tons, 0.0 bis 1.0. */
    public double systemSoundVolume = 0.5;

    /** Alarm zusätzlich als Overlay-Text über der Hotbar anzeigen. */
    public boolean showOverlayMessage = true;

    /**
     * Ein ausgelöster Alarm wird pausiert, bis der Preis diese Zone wieder
     * verlassen hat. Verhindert Dauergeklingel.
     */
    public boolean rearmOnly = true;

    /** Listings mit Verzauberungen ignorieren, wenn das Basis-Item gesucht wird. */
    public boolean ignoreEnchanted = true;

    /** Shulker-Kisten aus der Preisbildung raushalten (verzerren die Stats stark). */
    public boolean ignoreShulkers = true;

    /**
     * Nur scannen, während du tatsächlich auf DonutSMP verbunden bist.
     *
     * false (Standard) = die Mod beobachtet den Markt auch im Hauptmenü, in
     * Singleplayer oder auf anderen Servern weiter. Praktisch, wenn du auf
     * einen Zielpreis wartest, aber gerade woanders spielst - die Meldung wird
     * dann gemerkt und nachgereicht.
     *
     * true = kein einziger Request, solange du nicht auf DonutSMP bist. Spart
     * Traffic und hält deine API-Nutzung genau auf die Zeit begrenzt, in der du
     * auch spielst.
     */
    public boolean onlyWhileOnDonutSmp = false;

    /** Hostnamen, die als DonutSMP gelten. */
    public String[] donutHosts = {"donutsmp.net", "donut-smp.net", "donutsmp.de"};

    // ---------------------------------------------------------------- laden/speichern

    private static DonutConfig instance;

    public static DonutConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutmarket");
    }

    private static Path file() {
        return configDir().resolve("config.json");
    }

    private static DonutConfig load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                String json = Files.readString(p, StandardCharsets.UTF_8);
                DonutConfig cfg = GSON.fromJson(json, DonutConfig.class);
                if (cfg != null) {
                    return cfg;
                }
            }
        } catch (Exception e) {
            System.err.println("[DonutMarket] Config konnte nicht gelesen werden: " + e.getMessage());
        }
        return new DonutConfig();
    }

    public void save() {
        try {
            Files.createDirectories(configDir());
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[DonutMarket] Config konnte nicht geschrieben werden: " + e.getMessage());
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
