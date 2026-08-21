package net.donutmarket.market;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Einmalig aufgebauter Index über alle Items im Spiel.
 *
 * Bewusst OHNE Registry#get(Identifier) - dessen Rückgabetyp hat sich zwischen
 * den Versionen mehrfach geändert (T vs. Optional&lt;Holder.Reference&lt;T&gt;&gt;).
 * Wir iterieren stattdessen einmal über die Registry und bauen unsere eigene
 * Map. Das ist versionsstabil und außerdem schneller für die Suche.
 */
public final class ItemIndex {

    /**
     * @param item      das Item selbst
     * @param id        "minecraft:totem_of_undying"
     * @param path      "totem_of_undying"
     * @param display   "Totem of Undying" (übersetzt)
     * @param searchKey vorberechnet, lowercase, für schnelles contains()
     */
    public record Entry(Item item, String id, String path, String display, String searchKey) {
        public ItemStack stack() {
            return new ItemStack(item);
        }
    }

    private static List<Entry> all;
    private static Map<String, Entry> byId;
    private static Map<String, Entry> byPath;

    private ItemIndex() {
    }

    private static synchronized void build() {
        if (all != null) {
            return;
        }
        List<Entry> list = new ArrayList<>(1500);
        Map<String, Entry> ids = new HashMap<>();
        Map<String, Entry> paths = new HashMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) {
                continue;
            }
            String id = key.toString();
            String path = key.getPath();
            if ("air".equals(path)) {
                continue;
            }
            String display;
            try {
                display = new ItemStack(item).getHoverName().getString();
            } catch (Exception e) {
                display = path;
            }
            String searchKey = (display + " " + path + " " + id)
                    .toLowerCase(Locale.ROOT)
                    .replace('_', ' ');

            Entry e = new Entry(item, id, path, display, searchKey);
            list.add(e);
            ids.put(id, e);
            paths.putIfAbsent(path, e);
        }

        list.sort((a, b) -> a.display().compareToIgnoreCase(b.display()));
        all = List.copyOf(list);
        byId = Map.copyOf(ids);
        byPath = Map.copyOf(paths);
    }

    public static List<Entry> all() {
        build();
        return all;
    }

    /** Akzeptiert "minecraft:totem_of_undying" genauso wie "totem_of_undying". */
    public static Entry find(String idOrPath) {
        build();
        if (idOrPath == null || idOrPath.isBlank()) {
            return null;
        }
        String s = idOrPath.trim().toLowerCase(Locale.ROOT);
        Entry e = byId.get(s);
        if (e != null) {
            return e;
        }
        e = byId.get("minecraft:" + s);
        if (e != null) {
            return e;
        }
        int colon = s.indexOf(':');
        return byPath.get(colon >= 0 ? s.substring(colon + 1) : s);
    }

    public static String displayNameOf(String itemId) {
        Entry e = find(itemId);
        return e != null ? e.display() : itemId;
    }

    /**
     * Suche für die Item-Auswahl. Treffer am Wortanfang werden nach vorne
     * sortiert, damit "tot" wirklich das Totem zuerst zeigt und nicht irgendein
     * Item, das "tot" mittendrin hat.
     */
    public static List<Entry> search(String query, int limit) {
        build();
        if (query == null || query.isBlank()) {
            return all.size() > limit ? all.subList(0, limit) : all;
        }
        String q = query.trim().toLowerCase(Locale.ROOT).replace('_', ' ');

        List<Entry> starts = new ArrayList<>();
        List<Entry> contains = new ArrayList<>();

        for (Entry e : all) {
            String k = e.searchKey();
            if (k.startsWith(q) || k.contains(" " + q)) {
                starts.add(e);
            } else if (k.contains(q)) {
                contains.add(e);
            }
            if (starts.size() >= limit) {
                break;
            }
        }
        starts.addAll(contains);
        return starts.size() > limit ? starts.subList(0, limit) : starts;
    }

    /**
     * Vergleicht eine Item-ID aus der API mit einer erwarteten ID.
     * Die API lässt den Namespace mal weg, mal nicht.
     */
    public static boolean matches(String apiId, String expectedId) {
        if (apiId == null || expectedId == null) {
            return false;
        }
        String a = stripNamespace(apiId);
        String b = stripNamespace(expectedId);
        return a.equalsIgnoreCase(b);
    }

    public static String stripNamespace(String s) {
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }
}
