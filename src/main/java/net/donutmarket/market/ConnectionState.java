package net.donutmarket.market;

import net.donutmarket.config.DonutConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

/**
 * Hält fest, wo der Spieler gerade verbunden ist.
 *
 * Warum eine eigene Klasse: Der Poller läuft auf einem Netzwerk-Thread und darf
 * Minecraft-Zustand nicht anfassen. Deshalb aktualisiert der Client-Tick hier
 * zwei volatile Flags, und der Poller liest nur diese. Kein Thread-Problem,
 * keine Race Condition.
 *
 * Falls Minecraft#getCurrentServer() in einer künftigen Version umbenannt wird:
 * Das ist die EINZIGE Stelle im ganzen Projekt, die darauf zugreift.
 */
public final class ConnectionState {

    private static volatile boolean onDonut = false;
    private static volatile boolean inWorld = false;
    private static volatile String currentHost = "";

    private ConnectionState() {
    }

    /** Wird jeden Client-Tick aufgerufen. Muss auf dem Client-Thread laufen. */
    public static void update(Minecraft mc) {
        inWorld = mc.player != null;

        ServerData data = mc.getCurrentServer();
        String host = data == null || data.ip == null ? "" : data.ip.toLowerCase(Locale.ROOT);
        currentHost = host;

        if (host.isEmpty()) {
            onDonut = false;
            return;
        }
        boolean match = false;
        for (String h : DonutConfig.get().donutHosts) {
            if (h != null && !h.isBlank() && host.contains(h.toLowerCase(Locale.ROOT))) {
                match = true;
                break;
            }
        }
        onDonut = match;
    }

    public static boolean isOnDonutSmp() {
        return onDonut;
    }

    public static boolean isInWorld() {
        return inWorld;
    }

    public static String currentHost() {
        return currentHost;
    }

    /** Darf gerade gescannt werden? */
    public static boolean pollingAllowed() {
        return !DonutConfig.get().onlyWhileOnDonutSmp || onDonut;
    }
}
