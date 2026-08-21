package net.donutmarket.notify;

import net.donutmarket.config.DonutConfig;
import net.donutmarket.market.Alert;
import net.donutmarket.market.ItemIndex;
import net.donutmarket.market.MarketService;
import net.donutmarket.market.PriceUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Alles, was beim Auslösen eines Alarms sicht- und hörbar wird.
 *
 * Wird vom Netzwerk-Thread aufgerufen und hängt sich selbst per
 * Minecraft#execute auf den Client-Thread zurück.
 */
public final class Notifier {

    /** Ein geplantes "Ding": nach wie vielen Ticks, mit welcher Tonhöhe. */
    private record PendingSound(int ticksLeft, float pitch) {
    }

    private static final Deque<PendingSound> SOUND_QUEUE = new ArrayDeque<>();

    /**
     * Meldungen, die aufgelaufen sind, während kein Spieler existierte
     * (Hauptmenü, Ladebildschirm, Serverwechsel). Werden nachgereicht, sobald
     * du wieder in einer Welt bist - sonst wäre die Benachrichtigung einfach
     * verschluckt worden und du hättest nur den Ton gehört.
     */
    private static final Deque<Component> PENDING_CHAT = new ArrayDeque<>();
    private static final int PENDING_LIMIT = 40;

    private Notifier() {
    }

    public static void fireAlert(Alert alert, double currentPrice, MarketService.ScanResult scan) {
        DonutConfig cfg = DonutConfig.get();

        // ZUERST der System-Ton, direkt hier auf dem Poller-Thread. Bewusst
        // ausserhalb von mc.execute(): wenn Minecraft pausiert ist (z.B. weil
        // das Fenster den Fokus verloren hat), koennte die Client-Queue
        // verzoegert abgearbeitet werden - der Ton soll aber sofort kommen.
        if (cfg.playSound && cfg.useSystemSound) {
            SystemBeep.play();
        }

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            String name = ItemIndex.displayNameOf(alert.itemId);

            boolean buy = alert.mode == Alert.Mode.BUY_BELOW;
            ChatFormatting accent = buy ? ChatFormatting.GREEN : ChatFormatting.GOLD;
            String verb = buy ? "KAUFEN" : "VERKAUFEN";

            // ---- Chat -------------------------------------------------------
            Component header = Component.literal("")
                    .append(Component.literal("[DonutMarket] ").withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal(verb + " ").withStyle(accent, ChatFormatting.BOLD))
                    .append(Component.literal(name).withStyle(ChatFormatting.WHITE));

            Component detail = Component.literal("  ")
                    .append(Component.literal(PriceUtil.full(currentPrice) + " $").withStyle(accent))
                    .append(Component.literal(" / Stück").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("   (Ziel: " + PriceUtil.full(alert.targetPrice) + ")")
                            .withStyle(ChatFormatting.DARK_GRAY));

            sendChat(mc, header);
            sendChat(mc, detail);

            if (scan != null && scan.found() && scan.bestCount() > 1) {
                sendChat(mc, Component.literal("  Günstigstes Angebot: " + scan.bestCount() + "x für "
                                + PriceUtil.full(scan.bestTotalPrice()) + " $"
                                + (scan.seller() != null ? " von " + scan.seller() : ""))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }

            // ---- Overlay über der Hotbar -----------------------------------
            // Nur sinnvoll, wenn wirklich ein HUD gerendert wird.
            if (cfg.showOverlayMessage && mc.player != null) {
                Component overlay = Component.literal(name + " " + PriceUtil.compact(currentPrice) + " $")
                        .withStyle(accent);
                // 26.2: die HUD-Methoden hängen an Minecraft#gui#hud
                mc.gui.hud.setOverlayMessage(overlay, false);
            }

            // ---- Ding Ding Ding ---------------------------------------------
            // Der System-Ton lief schon oben. Nur wenn der aus ist (oder auf
            // diesem Rechner nicht geht), springt der Minecraft-Ton ein -
            // sonst hoerst du alles doppelt.
            if (cfg.playSound && (!cfg.useSystemSound || SystemBeep.isUnavailable())) {
                queueDings(Math.max(1, Math.min(8, cfg.soundRepeats)));
            }
        });
    }

    public static void info(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> sendChat(mc, Component.literal("[DonutMarket] ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY))));
    }

    public static void error(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> sendChat(mc, Component.literal("[DonutMarket] ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(text).withStyle(ChatFormatting.RED))));
    }

    private static void sendChat(Minecraft mc, Component c) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(c);
            return;
        }
        // Kein Spieler da: merken und ins Log schreiben, statt zu verwerfen.
        synchronized (PENDING_CHAT) {
            if (PENDING_CHAT.size() < PENDING_LIMIT) {
                PENDING_CHAT.add(c);
            }
        }
        System.out.println("[DonutMarket] " + c.getString());
    }

    /** Aufgelaufene Meldungen nachreichen, sobald wieder ein Spieler da ist. */
    private static void flushPending(Minecraft mc) {
        if (mc.player == null || PENDING_CHAT.isEmpty()) {
            return;
        }
        synchronized (PENDING_CHAT) {
            if (!PENDING_CHAT.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal("[DonutMarket] ")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal("Während du weg warst:")
                                .withStyle(ChatFormatting.DARK_GRAY)));
            }
            while (!PENDING_CHAT.isEmpty()) {
                mc.player.sendSystemMessage(PENDING_CHAT.poll());
            }
        }
    }

    // ------------------------------------------------------------------ Sound

    /**
     * Drei Plings mit steigender Tonhöhe, jeweils 4 Ticks (0,2s) auseinander.
     * Klingt wie das klassische "ding-ding-ding" und geht im Kampfgetümmel
     * trotzdem nicht unter.
     */
    private static void queueDings(int count) {
        synchronized (SOUND_QUEUE) {
            for (int i = 0; i < count; i++) {
                // 1.0 -> 1.26 -> 1.5 : klingt nach aufsteigendem Dreiklang
                float pitch = 1.0f + (i * 0.25f);
                SOUND_QUEUE.add(new PendingSound(i * 4, Math.min(2.0f, pitch)));
            }
        }
    }

    /** Wird jeden Client-Tick aus DonutMarketClient aufgerufen. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        flushPending(mc);

        if (SOUND_QUEUE.isEmpty()) {
            return;
        }
        synchronized (SOUND_QUEUE) {
            int size = SOUND_QUEUE.size();
            for (int i = 0; i < size; i++) {
                PendingSound s = SOUND_QUEUE.poll();
                if (s == null) {
                    break;
                }
                if (s.ticksLeft() <= 0) {
                    mc.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, s.pitch()));
                } else {
                    SOUND_QUEUE.add(new PendingSound(s.ticksLeft() - 1, s.pitch()));
                }
            }
        }
    }

    /** Vorschau-Ton für den Test-Button. Spielt genau das, was im Ernstfall kommt. */
    public static void playTestSound() {
        DonutConfig cfg = DonutConfig.get();
        if (cfg.useSystemSound) {
            SystemBeep.play();
            if (!SystemBeep.isUnavailable()) {
                return;
            }
        }
        queueDings(Math.max(1, Math.min(8, cfg.soundRepeats)));
    }
}
