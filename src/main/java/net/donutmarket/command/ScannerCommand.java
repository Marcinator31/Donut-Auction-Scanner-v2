package net.donutmarket.command;

import net.donutmarket.ui.ScannerScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

/**
 * Der einzige Befehl der Mod.
 *
 *   /auctionscanner   oeffnet die Oberflaeche
 *   /scanner          Kurzform, macht dasselbe
 *
 * Absichtlich mehr nicht. Alles - Zugang, Gegenstand, Zielpreis, Richtung,
 * Ton, Intervall - wird in der Oberflaeche eingestellt. Ein Befehl, den man
 * sich merken muss, ist besser als zehn.
 */
public final class ScannerCommand {

    private ScannerCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("auctionscanner")
                    .executes(ctx -> open()));
            dispatcher.register(ClientCommands.literal("scanner")
                    .executes(ctx -> open()));
        });
    }

    private static int open() {
        Minecraft mc = Minecraft.getInstance();
        // Verzoegert oeffnen: der Chat schliesst sich sonst direkt ueber das Fenster.
        mc.execute(() -> mc.gui.setScreen(new ScannerScreen(null)));
        return 1;
    }
}
