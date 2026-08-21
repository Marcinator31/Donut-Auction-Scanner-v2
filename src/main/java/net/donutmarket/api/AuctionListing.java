package net.donutmarket.api;

/**
 * Ein aktives Angebot im Auktionshaus.
 *
 * @param itemId      z.B. "minecraft:totem_of_undying" (Namespace kann fehlen)
 * @param displayName Anzeigename, falls die API einen liefert, sonst null
 * @param count       Stackgröße des Angebots
 * @param totalPrice  Preis für den kompletten Stack
 * @param seller      Verkäufername, falls vorhanden
 * @param enchanted   true, wenn das Item Verzauberungen trägt
 */
public record AuctionListing(
        String itemId,
        String displayName,
        int count,
        long totalPrice,
        String seller,
        boolean enchanted
) {
    /** Preis pro einzelnem Item - das ist die Zahl, die man vergleichen will. */
    public double pricePerUnit() {
        return count <= 0 ? totalPrice : (double) totalPrice / count;
    }
}
