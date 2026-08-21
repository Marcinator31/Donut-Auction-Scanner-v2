package net.donutmarket.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.donutmarket.config.DonutConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client für die offizielle DonutSMP-Public-API.
 *
 * Basis-URL: https://api.donutsmp.net/v1
 * Auth:      Header "Authorization: Bearer <key>", Key kommt aus /api ingame.
 * Limit:     250 Requests/Minute pro Key.
 *
 * Eigenheit der API: /auction/list/{page} ist ein GET *mit JSON-Body*
 * ({"search":"...","sort":"lowest_price"}). Das ist unüblich, aber genau so
 * erwartet der Server es. Javas HttpClient kann das über
 * .method("GET", BodyPublishers.ofString(...)).
 *
 * Alle Feldnamen werden tolerant gelesen: Die API hat in der Vergangenheit
 * Felder umbenannt, und für /auction/transactions ist die Struktur nicht
 * offiziell dokumentiert. Deshalb probiert der Parser mehrere Kandidaten durch.
 * Mit /dm raw kannst du dir jederzeit die echte Antwort ansehen.
 */
public final class DonutApi {

    public static final String BASE_URL = "https://api.donutsmp.net/v1";

    public enum Sort {
        LOWEST_PRICE,
        HIGHEST_PRICE,
        RECENTLY_LISTED,
        LAST_LISTED;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Wird geworfen, wenn die API einen Fehlerstatus liefert. */
    public static class ApiException extends Exception {
        public final int statusCode;

        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    private final HttpClient client;
    private final RateLimiter limiter;

    public DonutApi() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.limiter = new RateLimiter(DonutConfig.get().maxRequestsPerMinute);
    }

    public RateLimiter limiter() {
        return limiter;
    }

    // ------------------------------------------------------------------ requests

    /** Roher Request - für /dm raw und zum Debuggen unbekannter Felder. */
    public String requestRaw(String path, String jsonBody) throws Exception {
        DonutConfig cfg = DonutConfig.get();
        if (!cfg.hasApiKey()) {
            throw new ApiException(401, "Kein API-Key gesetzt. Führe ingame /api aus und trage ihn mit /dm key <key> ein.");
        }

        limiter.acquire();

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Authorization", "Bearer " + cfg.apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "DonutMarket-Fabric/1.0")
                .timeout(Duration.ofSeconds(15));

        if (jsonBody == null) {
            b.GET();
        } else {
            b.header("Content-Type", "application/json");
            b.method("GET", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        }

        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (res.statusCode() == 429) {
            throw new ApiException(429, "Rate-Limit erreicht. Erhöhe checkIntervalSeconds oder senke maxRequestsPerMinute.");
        }
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new ApiException(res.statusCode(), "API-Key ungültig oder Discord-Account nicht verknüpft.");
        }
        if (res.statusCode() >= 400) {
            throw new ApiException(res.statusCode(), "HTTP " + res.statusCode() + ": " + truncate(res.body(), 200));
        }
        return res.body();
    }

    /** Eine Seite des Auktionshauses. */
    public List<AuctionListing> fetchAuctionPage(int page, String search, Sort sort) throws Exception {
        String body = "{\"search\":\"" + escape(search) + "\",\"sort\":\"" + sort + "\"}";
        String raw = requestRaw("/auction/list/" + Math.max(1, page), body);
        JsonArray arr = extractResultArray(raw);

        List<AuctionListing> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            AuctionListing l = parseListing(el.getAsJsonObject());
            if (l != null) {
                out.add(l);
            }
        }
        return out;
    }

    /** Eine Seite der abgeschlossenen Verkäufe. */
    public List<SaleRecord> fetchTransactionPage(int page, String search) throws Exception {
        String body = "{\"search\":\"" + escape(search) + "\",\"sort\":\"" + Sort.RECENTLY_LISTED + "\"}";
        String raw = requestRaw("/auction/transactions/" + Math.max(1, page), body);
        JsonArray arr = extractResultArray(raw);

        List<SaleRecord> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            SaleRecord s = parseSale(el.getAsJsonObject());
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ parsing

    private static JsonArray extractResultArray(String raw) {
        JsonElement root = JsonParser.parseString(raw);
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject obj = root.getAsJsonObject();
        for (String key : new String[]{"result", "results", "data", "auctions", "transactions", "items"}) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                return obj.getAsJsonArray(key);
            }
        }
        return null;
    }

    private static AuctionListing parseListing(JsonObject o) {
        JsonObject item = childObject(o, "item");
        if (item == null) {
            return null;
        }
        String id = str(item, "id", "identifier", "material", "type");
        if (id == null) {
            return null;
        }
        long price = num(o, 0L, "price", "amount", "cost", "value");
        int count = (int) num(item, 1L, "count", "amount", "quantity");
        String display = str(item, "display_name", "displayName", "name");
        String seller = sellerName(o);
        return new AuctionListing(id, display, Math.max(1, count), price, seller, hasEnchantments(item));
    }

    private static SaleRecord parseSale(JsonObject o) {
        JsonObject item = childObject(o, "item");
        if (item == null) {
            return null;
        }
        String id = str(item, "id", "identifier", "material", "type");
        if (id == null) {
            return null;
        }
        long price = num(o, 0L, "price", "amount", "cost", "value", "sold_for", "soldFor");
        int count = (int) num(item, 1L, "count", "amount", "quantity");
        long ts = normalizeTimestamp(num(o, 0L,
                "timestamp", "time", "date", "sold_at", "soldAt",
                "created_at", "createdAt", "purchased_at", "purchasedAt", "epoch"));
        return new SaleRecord(id, Math.max(1, count), price, ts, hasEnchantments(item));
    }

    /**
     * Die API liefert Zeitstempel je nach Endpunkt in Sekunden oder
     * Millisekunden. Alles unter ~10^11 behandeln wir als Sekunden.
     */
    private static long normalizeTimestamp(long v) {
        if (v <= 0L) {
            return 0L;
        }
        return v < 100_000_000_000L ? v * 1000L : v;
    }

    private static boolean hasEnchantments(JsonObject item) {
        JsonObject ench = childObject(item, "enchants", "enchantments");
        if (ench == null) {
            return false;
        }
        JsonObject inner = childObject(ench, "enchantments");
        JsonObject levels = childObject(inner != null ? inner : ench, "levels");
        if (levels != null) {
            return !levels.keySet().isEmpty();
        }
        return !ench.keySet().isEmpty();
    }

    private static String sellerName(JsonObject o) {
        String direct = str(o, "seller", "owner", "player", "username");
        if (direct != null) {
            return direct;
        }
        JsonObject sellerObj = childObject(o, "seller", "owner", "player");
        return sellerObj == null ? null : str(sellerObj, "name", "username", "display_name", "displayName");
    }

    private static JsonObject childObject(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && o.get(k).isJsonObject()) {
                return o.getAsJsonObject(k);
            }
        }
        return null;
    }

    private static String str(JsonObject o, String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                String s = e.getAsString();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static long num(JsonObject o, long fallback, String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonPrimitive()) {
                try {
                    if (e.getAsJsonPrimitive().isNumber()) {
                        return e.getAsLong();
                    }
                    if (e.getAsJsonPrimitive().isString()) {
                        return Long.parseLong(e.getAsString().replaceAll("[^0-9-]", ""));
                    }
                } catch (Exception ignored) {
                    // nächster Kandidat
                }
            }
        }
        return fallback;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
