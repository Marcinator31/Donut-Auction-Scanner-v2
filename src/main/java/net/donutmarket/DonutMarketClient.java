package net.donutmarket;

import com.mojang.blaze3d.platform.InputConstants;
import net.donutmarket.command.ScannerCommand;
import net.donutmarket.market.ConnectionState;
import net.donutmarket.market.MarketService;
import net.donutmarket.notify.Notifier;
import net.donutmarket.ui.ScannerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt.
 *
 * Die Mod ist rein clientseitig und ausschließlich lesend: Sie fragt die
 * offizielle DonutSMP-Public-API ab und zeigt an, was sie findet. Sie kauft
 * nichts, verkauft nichts, klickt nichts an und schickt keine Befehle an den
 * Server. Damit ist sie funktional dasselbe, was der Browser auch könnte -
 * nur direkt im Spiel.
 */
public class DonutMarketClient implements ClientModInitializer {

    public static final String MOD_ID = "donutmarket";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Seit 1.21.9 nimmt KeyMapping eine Category statt eines Strings.
     * Das Fabric-Modul heisst seit 26.x keybinding-v1 nicht mehr, sondern
     * keymapping-v1, und die Methode registerKeyBinding heisst
     * registerKeyMapping.
     */
    public static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutmarket.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KEY_CATEGORY
        ));

        ScannerCommand.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            MarketService.get().start();
            LOGGER.info("DonutMarket bereit. {} Alarme geladen.",
                    MarketService.get().alerts().size());
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MarketService.get().shutdown());

        ClientTickEvents.END_CLIENT_TICK.register(DonutMarketClient::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        // Muss vor dem Poller-Check aktuell sein - laeuft auf dem Client-Thread.
        ConnectionState.update(client);
        Notifier.tick();

        while (openKey != null && openKey.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new ScannerScreen(null));
            }
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
