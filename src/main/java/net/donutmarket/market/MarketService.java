package net.donutmarket.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.donutmarket.api.AuctionListing;
import net.donutmarket.api.DonutApi;
import net.donutmarket.api.SaleRecord;
import net.donutmarket.config.DonutConfig;
import net.donutmarket.notify.Notifier;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Herzstück der Mod: hält die Alarme, fragt Preise ab, löst aus.
 *
 * Alles Netzwerk läuft auf einem eigenen Daemon-Thread. Nichts hier darf
 * direkt Minecraft-Zustand anfassen - dafür geht alles über Notifier, der
 * sich per Minecraft#execute auf den Client-Thread zurückhängt.
 */
public final class MarketService {

    /**
     * Ergebnis eines Auktions-Scans.
     *
     * @param bestPricePerUnit günstigster Preis PRO STUECK, der gefunden wurde
     * @param bestTotalPrice   Gesamtpreis genau dieses Angebots
     * @param bestCount        Stackgröße genau dieses Angebots
     */
    public record ScanResult(
            String itemId,
            double bestPricePerUnit,
            long bestTotalPrice,
            int bestCount,
            String seller,
            int pagesScanned,
            int listingsMatched
    ) {
        public boolean found() {
            return bestPricePerUnit > 0 && bestPricePerUnit < Double.MAX_VALUE;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ALERT_LIST = new TypeToken<List<Alert>>() {
    }.getType();

    private final DonutApi api = new DonutApi();
    private final HistoryStore history = new HistoryStore();
    private final List<Alert> alerts = new CopyOnWriteArrayList<>();

    private OrderSource orderSource = OrderSource.UNAVAILABLE;
    private ScheduledExecutorService scheduler;
    private final AtomicInteger cursor = new AtomicInteger(0);
    private volatile boolean running = false;
    private volatile String lastGlobalError;

    private static MarketService instance;

    public static MarketService get() {
        if (instance == null) {
            instance = new MarketService();
        }
        return instance;
    }

    private MarketService() {
    }

    // ------------------------------------------------------------------ Zugriff

    public DonutApi api() {
        return api;
    }

    public HistoryStore history() {
        return history;
    }

    public List<Alert> alerts() {
        return alerts;
    }

    public OrderSource orderSource() {
        return orderSource;
    }

    public void setOrderSource(OrderSource src) {
        this.orderSource = src == null ? OrderSource.UNAVAILABLE : src;
    }

    public String lastGlobalError() {
        return lastGlobalError;
    }

    // ------------------------------------------------------------------ Lebenszyklus

    public void start() {
        history.load();
        loadAlerts();
        if (running) {
            return;
        }
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DonutMarket-Poller");
            t.setDaemon(true);
            return t;
        });
        // Erster Lauf nach 10s, damit der Login nicht ausgebremst wird.
        scheduler.scheduleWithFixedDelay(this::tick, 10, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        history.save();
        saveAlerts();
    }

    /**
     * Läuft alle 5s. Prüft pro Durchgang höchstens EINEN fälligen Alarm.
     * So verteilt sich die Last gleichmäßig, statt alle 60s einen Burst von
     * 30 Requests auf einmal rauszufeuern.
     */
    private void tick() {
        try {
            if (!DonutConfig.get().hasApiKey() || alerts.isEmpty()) {
                return;
            }
            // Optionaler Schalter: nur scannen, während wir auf DonutSMP sind.
            if (!ConnectionState.pollingAllowed()) {
                return;
            }
            long interval = Math.max(15, DonutConfig.get().checkIntervalSeconds) * 1000L;
            long now = System.currentTimeMillis();

            int n = alerts.size();
            for (int i = 0; i < n; i++) {
                int idx = Math.floorMod(cursor.getAndIncrement(), n);
                Alert a = alerts.get(idx);
                if (!a.enabled || a.checking) {
                    continue;
                }
                if (now - a.lastCheckMillis < interval) {
                    continue;
                }
                checkAlert(a);
                break; // nur einer pro Tick
            }
            history.saveIfDirty();
        } catch (Exception e) {
            lastGlobalError = e.getMessage();
        }
    }

    // ------------------------------------------------------------------ Preis-Scan

    /**
     * Sucht das GUENSTIGSTE Angebot pro Stück.
     *
     * Wichtiger Fallstrick: Die API sortiert mit "lowest_price" nach dem
     * GESAMTPREIS eines Angebots, nicht nach Preis pro Stück. Ein Stack mit
     * 64 Totems für 12m steht damit weit hinten, ist pro Totem aber mit 187k
     * deutlich billiger als ein Einzeltotem für 250k auf Seite 1.
     *
     * Deshalb nehmen wir NICHT einfach den ersten Treffer, sondern blättern
     * weiter und merken uns das laufende Minimum pro Stück. Abgebrochen wird
     * erst, wenn mehrere Seiten in Folge nichts Besseres mehr bringen.
     */
    public ScanResult scanCheapest(String itemId, String searchTerm) throws Exception {
        DonutConfig cfg = DonutConfig.get();

        double bestPerUnit = Double.MAX_VALUE;
        long bestTotal = 0;
        int bestCount = 0;
        String bestSeller = null;

        int pagesScanned = 0;
        int matched = 0;
        int pagesWithoutImprovement = 0;

        for (int page = 1; page <= Math.max(1, cfg.maxPagesPerScan); page++) {
            List<AuctionListing> listings = api.fetchAuctionPage(page, searchTerm, DonutApi.Sort.LOWEST_PRICE);
            pagesScanned++;

            if (listings.isEmpty()) {
                break; // keine weiteren Seiten
            }

            boolean improvedHere = false;
            for (AuctionListing l : listings) {
                if (!accept(l, itemId, cfg)) {
                    continue;
                }
                matched++;
                double perUnit = l.pricePerUnit();
                if (perUnit > 0 && perUnit < bestPerUnit) {
                    bestPerUnit = perUnit;
                    bestTotal = l.totalPrice();
                    bestCount = l.count();
                    bestSeller = l.seller();
                    improvedHere = true;
                }
            }

            if (improvedHere) {
                pagesWithoutImprovement = 0;
            } else if (++pagesWithoutImprovement > Math.max(1, cfg.pagesWithoutImprovement)) {
                break;
            }
        }

        return new ScanResult(itemId, bestPerUnit, bestTotal, bestCount, bestSeller, pagesScanned, matched);
    }

    /** Filter: passt das Listing überhaupt zu dem, was wir suchen? */
    private static boolean accept(AuctionListing l, String itemId, DonutConfig cfg) {
        if (!ItemIndex.matches(l.itemId(), itemId)) {
            return false;
        }
        if (cfg.ignoreEnchanted && l.enchanted()) {
            return false;
        }
        if (cfg.ignoreShulkers && ItemIndex.stripNamespace(l.itemId()).contains("shulker_box")
                && !ItemIndex.stripNamespace(itemId).contains("shulker_box")) {
            return false;
        }
        return l.totalPrice() > 0 && l.count() > 0;
    }

    /**
     * Bester Verkaufspreis für die SELL_ABOVE-Richtung: die Order, die am
     * meisten zahlt. Solange es keine Order-Quelle gibt, wird ersatzweise der
     * günstigste Auktionspreis als Marktreferenz benutzt.
     *
     * @return bester Gebotspreis pro Stück oder null
     */
    public Double bestBid(String itemId) {
        try {
            return orderSource.bestBidPerUnit(itemId);
        } catch (Exception e) {
            return null;
        }
    }

    /** Verkaufte Items der letzten Zeit in die Historie schreiben. */
    public int seedHistoryFromTransactions(String itemId, String searchTerm, int pages) {
        int added = 0;
        try {
            for (int page = 1; page <= Math.max(1, pages); page++) {
                List<SaleRecord> sales = api.fetchTransactionPage(page, searchTerm);
                if (sales.isEmpty()) {
                    break;
                }
                for (SaleRecord s : sales) {
                    if (!ItemIndex.matches(s.itemId(), itemId)) {
                        continue;
                    }
                    if (DonutConfig.get().ignoreEnchanted && s.enchanted()) {
                        continue;
                    }
                    if (!s.hasTimestamp()) {
                        continue; // ohne Zeit ist der Punkt für den Graph wertlos
                    }
                    history.record(itemId, s.pricePerUnit(), HistoryStore.Source.SALE, s.timestampMillis());
                    added++;
                }
            }
            history.saveIfDirty();
        } catch (Exception e) {
            lastGlobalError = e.getMessage();
        }
        return added;
    }

    // ------------------------------------------------------------------ Auswertung

    /** Prüft einen Alarm sofort. Blockiert - nur vom Poller-Thread aufrufen. */
    public void checkAlert(Alert a) {
        a.checking = true;
        try {
            ScanResult r = scanCheapest(a.itemId, a.searchTerm());
            a.lastCheckMillis = System.currentTimeMillis();
            a.lastError = null;

            if (!r.found()) {
                a.lastPrice = null;
                a.lastError = "Kein passendes Angebot gefunden";
                return;
            }

            double current;
            if (a.mode == Alert.Mode.SELL_ABOVE) {
                Double bid = bestBid(a.itemId);
                current = bid != null ? bid : r.bestPricePerUnit();
            } else {
                current = r.bestPricePerUnit();
            }

            a.lastPrice = current;
            a.lastCount = r.bestCount();
            a.lastSeller = r.seller();
            history.record(a.itemId, r.bestPricePerUnit(), HistoryStore.Source.ASK);

            evaluate(a, current, r);
        } catch (Exception e) {
            a.lastCheckMillis = System.currentTimeMillis();
            a.lastError = e.getMessage();
            lastGlobalError = e.getMessage();
        } finally {
            a.checking = false;
        }
    }

    private void evaluate(Alert a, double current, ScanResult r) {
        boolean triggered = a.isTriggeredBy(current);
        boolean rearmOnly = DonutConfig.get().rearmOnly;

        if (triggered && (a.armed || !rearmOnly)) {
            a.armed = false;
            Notifier.fireAlert(a, current, r);
            if (!a.repeat) {
                a.enabled = false;
            }
            saveAlerts();
        } else if (!triggered) {
            a.armed = true;
        }
    }

    // ------------------------------------------------------------------ Alarm-CRUD

    public Alert addAlert(String itemId, double target, Alert.Mode mode) {
        Alert a = new Alert(itemId, target, mode);
        a.afterLoad();
        alerts.add(a);
        saveAlerts();
        return a;
    }

    public void removeAlert(Alert a) {
        alerts.remove(a);
        saveAlerts();
    }

    public Alert byId(String id) {
        for (Alert a : alerts) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }

    /** Erzwingt sofortige Prüfung aller Alarme (auf dem Poller-Thread). */
    public void checkAllNow() {
        if (scheduler == null) {
            return;
        }
        scheduler.execute(() -> {
            for (Alert a : alerts) {
                if (a.enabled) {
                    checkAlert(a);
                }
            }
            history.saveIfDirty();
        });
    }

    /** Einen einzelnen Alarm sofort prüfen, ohne den Client zu blockieren. */
    public void checkNowAsync(Alert a) {
        if (scheduler == null) {
            return;
        }
        scheduler.execute(() -> checkAlert(a));
    }

    /** Beliebige Aufgabe auf dem Netzwerk-Thread ausführen (z.B. aus der GUI). */
    public void runAsync(Runnable task) {
        if (scheduler != null) {
            scheduler.execute(task);
        }
    }

    // ------------------------------------------------------------------ Persistenz

    private static Path alertFile() {
        return DonutConfig.configDir().resolve("alerts.json");
    }

    public void loadAlerts() {
        try {
            Path p = alertFile();
            if (!Files.exists(p)) {
                return;
            }
            List<Alert> loaded = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), ALERT_LIST);
            alerts.clear();
            if (loaded != null) {
                for (Alert a : loaded) {
                    if (a != null) {
                        a.afterLoad();
                        alerts.add(a);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DonutMarket] Alarme konnten nicht gelesen werden: " + e.getMessage());
        }
    }

    public void saveAlerts() {
        try {
            Files.createDirectories(DonutConfig.configDir());
            List<Alert> snapshot = Collections.unmodifiableList(new ArrayList<>(alerts));
            Files.writeString(alertFile(), GSON.toJson(snapshot, ALERT_LIST), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[DonutMarket] Alarme konnten nicht geschrieben werden: " + e.getMessage());
        }
    }
}
