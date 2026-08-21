package net.donutmarket.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.donutmarket.config.DonutConfig;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lokale Preis-Historie.
 *
 * WICHTIG, damit keine falschen Erwartungen entstehen: Die DonutSMP-API
 * liefert KEINE Historie. Dieser Speicher hat zwei Quellen:
 *
 *   ASK  - Snapshots des günstigsten Angebots, die diese Mod selbst misst.
 *          Die entstehen nur, während du spielst und die Mod läuft.
 *   SALE - echte, abgeschlossene Verkäufe aus /auction/transactions.
 *          Nur brauchbar, wenn die API dort einen Zeitstempel mitliefert.
 *
 * Der Graph ist also am ersten Tag dünn und wird mit der Zeit gut.
 */
public final class HistoryStore {

    public enum Source {
        ASK,
        SALE
    }

    /**
     * @param time  Unix-Millis
     * @param price Preis pro Stück
     */
    public record PricePoint(long time, double price, Source source) {
    }

    /** Fasst eine Zeitspanne zusammen - für die Kopfzeile im Graph. */
    public record Stats(int count, double min, double max, double avg, double first, double last) {
        public boolean isEmpty() {
            return count == 0;
        }

        public double changePercent() {
            return first <= 0 ? 0 : (last - first) / first * 100.0;
        }
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type TYPE = new TypeToken<Map<String, List<PricePoint>>>() {
    }.getType();

    /** Weniger als das speichern wir nicht - sonst läuft die Datei voll. */
    private static final long MIN_GAP_MILLIS = 30_000L;

    private final Map<String, List<PricePoint>> data = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    // ------------------------------------------------------------------ schreiben

    public void record(String itemId, double pricePerUnit, Source source) {
        record(itemId, pricePerUnit, source, System.currentTimeMillis());
    }

    public void record(String itemId, double pricePerUnit, Source source, long timeMillis) {
        if (itemId == null || pricePerUnit <= 0 || timeMillis <= 0) {
            return;
        }
        String key = ItemIndex.stripNamespace(itemId);
        List<PricePoint> list = data.computeIfAbsent(key, k -> new ArrayList<>());

        synchronized (list) {
            // Duplikate aus mehrfach abgefragten Transaktionsseiten rausfiltern
            for (int i = list.size() - 1; i >= 0 && i >= list.size() - 40; i--) {
                PricePoint p = list.get(i);
                if (p.source() == source
                        && Math.abs(p.time() - timeMillis) < 1_000L
                        && Math.abs(p.price() - pricePerUnit) < 0.001) {
                    return;
                }
            }
            // ASK-Snapshots ausdünnen: nicht öfter als alle 30s
            if (source == Source.ASK && !list.isEmpty()) {
                PricePoint last = list.get(list.size() - 1);
                if (last.source() == Source.ASK
                        && timeMillis - last.time() < MIN_GAP_MILLIS
                        && Math.abs(last.price() - pricePerUnit) < 0.001) {
                    return;
                }
            }
            list.add(new PricePoint(timeMillis, pricePerUnit, source));
            list.sort(Comparator.comparingLong(PricePoint::time));
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ lesen

    /** Alle Punkte der letzten {@code windowMillis}, chronologisch. */
    public List<PricePoint> window(String itemId, long windowMillis) {
        List<PricePoint> list = data.get(ItemIndex.stripNamespace(itemId));
        if (list == null) {
            return List.of();
        }
        long cutoff = System.currentTimeMillis() - windowMillis;
        List<PricePoint> out = new ArrayList<>();
        synchronized (list) {
            for (PricePoint p : list) {
                if (p.time() >= cutoff) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    public Stats stats(String itemId, long windowMillis) {
        List<PricePoint> pts = window(itemId, windowMillis);
        if (pts.isEmpty()) {
            return new Stats(0, 0, 0, 0, 0, 0);
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        for (PricePoint p : pts) {
            min = Math.min(min, p.price());
            max = Math.max(max, p.price());
            sum += p.price();
        }
        return new Stats(pts.size(), min, max, sum / pts.size(),
                pts.get(0).price(), pts.get(pts.size() - 1).price());
    }

    public boolean hasData(String itemId) {
        List<PricePoint> l = data.get(ItemIndex.stripNamespace(itemId));
        return l != null && !l.isEmpty();
    }

    public int totalPoints() {
        int n = 0;
        for (List<PricePoint> l : data.values()) {
            n += l.size();
        }
        return n;
    }

    // ------------------------------------------------------------------ aufräumen

    public void prune() {
        long cutoff = System.currentTimeMillis()
                - DonutConfig.get().historyRetentionHours * 3_600_000L;
        for (List<PricePoint> list : data.values()) {
            synchronized (list) {
                boolean removed = list.removeIf(p -> p.time() < cutoff);
                if (removed) {
                    dirty = true;
                }
            }
        }
        data.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // ------------------------------------------------------------------ persistenz

    private static Path file() {
        return DonutConfig.configDir().resolve("history.json");
    }

    public void load() {
        try {
            Path p = file();
            if (!Files.exists(p)) {
                return;
            }
            String json = Files.readString(p, StandardCharsets.UTF_8);
            Map<String, List<PricePoint>> loaded = GSON.fromJson(json, TYPE);
            if (loaded != null) {
                data.clear();
                for (Map.Entry<String, List<PricePoint>> e : loaded.entrySet()) {
                    if (e.getValue() != null) {
                        data.put(e.getKey(), new ArrayList<>(e.getValue()));
                    }
                }
            }
            prune();
        } catch (Exception e) {
            System.err.println("[DonutMarket] Historie konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        save();
    }

    public void save() {
        try {
            prune();
            Files.createDirectories(DonutConfig.configDir());
            Map<String, List<PricePoint>> snapshot = new HashMap<>();
            for (Map.Entry<String, List<PricePoint>> e : data.entrySet()) {
                synchronized (e.getValue()) {
                    snapshot.put(e.getKey(), new ArrayList<>(e.getValue()));
                }
            }
            Files.writeString(file(), GSON.toJson(snapshot, TYPE), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            System.err.println("[DonutMarket] Historie konnte nicht geschrieben werden: " + e.getMessage());
        }
    }
}
