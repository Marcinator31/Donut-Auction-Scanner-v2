package net.donutmarket.market;

import java.util.UUID;

/**
 * Ein Preis-Alarm auf ein Item.
 *
 * Beide Richtungen vergleichen gegen denselben Wert: den GÜNSTIGSTEN Preis pro
 * Stück, der aktuell im Auktionshaus steht. Das ist der Preis, den man selbst
 * unterbieten müsste, um zu verkaufen - und der Preis, den man zahlt, wenn man
 * kauft. Also in beide Richtungen die richtige Referenz.
 *
 *   BUY_BELOW  - günstigstes Angebot fällt auf &lt;= targetPrice.
 *                Moment zum Kaufen.
 *   SELL_ABOVE - günstigstes Angebot steigt auf &gt;= targetPrice.
 *                Moment zum Verkaufen. Beispiel: Elytra-Alarm auf 400m meldet
 *                sich, sobald die billigste Elytra im AH 400m kostet - dann
 *                bringt die eigene ungefähr dasselbe.
 *
 * Wenn irgendwann eine echte Order-Quelle dazukommt (siehe OrderSource), nutzt
 * SELL_ABOVE automatisch das höchste Kaufgebot statt des AH-Preises.
 */
public class Alert {

    public enum Mode {
        BUY_BELOW,
        SELL_ABOVE
    }

    /** Stabile ID, damit die GUI Zeilen wiedererkennt. */
    public String id = UUID.randomUUID().toString();

    /** Voller Registry-Name, z.B. "minecraft:totem_of_undying". */
    public String itemId = "";

    /**
     * Suchbegriff, der an die API geht. Standard ist der Registry-Pfad
     * ("totem_of_undying"). Falls die AH-Suche mit etwas anderem besser
     * trifft, kann man hier überschreiben.
     */
    public String searchOverride = "";

    /** Zielpreis PRO STUECK. */
    public double targetPrice = 0.0;

    public Mode mode = Mode.BUY_BELOW;

    public boolean enabled = true;

    /** false = Alarm deaktiviert sich nach dem ersten Auslösen. */
    public boolean repeat = true;

    // ------------------------------------------------------------- Laufzeit-Zustand
    // (transient = wird nicht in die JSON geschrieben)

    /** false, solange der Alarm noch in der Trigger-Zone hängt. */
    public transient boolean armed = true;

    /** Zuletzt gemessener Preis pro Stück, null wenn noch nie geprüft. */
    public transient Double lastPrice;

    /** Wie viele Stück das günstigste Angebot hatte. */
    public transient int lastCount;

    public transient String lastSeller;

    public transient long lastCheckMillis;

    public transient String lastError;

    public transient boolean checking;

    public Alert() {
    }

    public Alert(String itemId, double targetPrice, Mode mode) {
        this.itemId = itemId;
        this.targetPrice = targetPrice;
        this.mode = mode;
    }

    /** Wird nach dem Laden aus JSON aufgerufen, weil Gson keine Feld-Initializer ausführt. */
    public void afterLoad() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (mode == null) {
            mode = Mode.BUY_BELOW;
        }
        if (searchOverride == null) {
            searchOverride = "";
        }
        armed = true;
        checking = false;
    }

    public String searchTerm() {
        if (searchOverride != null && !searchOverride.isBlank()) {
            return searchOverride;
        }
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    /** Ist der aktuelle Preis in der Zone, die den Alarm auslöst? */
    public boolean isTriggeredBy(double pricePerUnit) {
        return mode == Mode.BUY_BELOW
                ? pricePerUnit <= targetPrice
                : pricePerUnit >= targetPrice;
    }

    /**
     * Abstand zum Ziel in Prozent. Negativ = Ziel noch nicht erreicht.
     * Bei BUY_BELOW heisst "+" also: günstiger als gewünscht.
     */
    public double distancePercent(double pricePerUnit) {
        if (targetPrice <= 0) {
            return 0;
        }
        double raw = (targetPrice - pricePerUnit) / targetPrice * 100.0;
        return mode == Mode.BUY_BELOW ? raw : -raw;
    }
}
