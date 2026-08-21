package net.donutmarket.api;

/**
 * Ein abgeschlossener Verkauf aus /v1/auction/transactions.
 *
 * Das ist die einzige Quelle in der offiziellen API, die echte *vergangene*
 * Preise samt Zeitstempel liefert. Genau daraus baut die Mod den 24h-Graphen,
 * solange die API einen Zeitstempel mitschickt.
 *
 * @param timestampMillis Unix-Zeit in Millisekunden, oder 0 wenn unbekannt
 */
public record SaleRecord(
        String itemId,
        int count,
        long totalPrice,
        long timestampMillis,
        boolean enchanted
) {
    public double pricePerUnit() {
        return count <= 0 ? totalPrice : (double) totalPrice / count;
    }

    public boolean hasTimestamp() {
        return timestampMillis > 0L;
    }
}
