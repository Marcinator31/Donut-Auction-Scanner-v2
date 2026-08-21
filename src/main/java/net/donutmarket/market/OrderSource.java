package net.donutmarket.market;

/**
 * Platzhalter für die Order-Seite (Kaufaufträge).
 *
 * Stand 21.08.2026 hat die offizielle DonutSMP-Public-API dafür KEINEN
 * Endpunkt. Es gibt nur:
 *   /v1/auction/list/{page}          - aktive Verkaufsangebote
 *   /v1/auction/transactions/{page}  - abgeschlossene Verkäufe
 *
 * Sobald DonutSMP Orders freigibt, implementiert man einfach dieses Interface
 * und hängt es in MarketService.setOrderSource() ein - der Rest der Mod
 * (Alarme, GUI, Graph) funktioniert dann ohne weitere Änderung.
 *
 * Die Semantik ist bewusst festgelegt: gesucht ist immer die Order, die am
 * MEISTEN zahlt. Das Gegenstück zum günstigsten Auktionsangebot.
 */
public interface OrderSource {

    /**
     * Bester Kaufauftrag für ein Item.
     *
     * @return höchster gebotener Preis pro Stück, oder null wenn es keine
     *         Order gibt bzw. die Quelle nichts liefern kann
     */
    Double bestBidPerUnit(String itemId) throws Exception;

    /** Kurzer Quellenname für die GUI, z.B. "DonutSMP API" oder "nicht verfügbar". */
    String sourceName();

    /** Wird benutzt, solange es keine echte Order-Quelle gibt. */
    OrderSource UNAVAILABLE = new OrderSource() {
        @Override
        public Double bestBidPerUnit(String itemId) {
            return null;
        }

        @Override
        public String sourceName() {
            return "nicht verfügbar";
        }
    };
}
