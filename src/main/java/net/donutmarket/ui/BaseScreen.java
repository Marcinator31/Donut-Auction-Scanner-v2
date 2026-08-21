package net.donutmarket.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Gemeinsame Basis aller Fenster dieser Mod.
 *
 * Sie existiert aus einem Grund: In 1.21.9 hat Mojang die Eingabebehandlung
 * umgebaut. Aus
 *
 *     mouseClicked(double mouseX, double mouseY, int button)
 *
 * wurde
 *
 *     mouseClicked(MouseButtonEvent event, boolean doubleClick)
 *
 * Die Koordinaten stecken jetzt im Event. Statt das in jedem Fenster einzeln
 * auszupacken, passiert es hier an genau einer Stelle. Wenn Mojang das noch
 * einmal ändert, ist diese Datei die einzige, die angefasst werden muss.
 *
 * Die Mausposition wird beim Zeichnen mitgeschrieben statt aus dem Event
 * gelesen. Das ist Absicht: Die Feldnamen des Events sind nicht dokumentiert,
 * die Zeichenmethode liefert die Position aber ohnehin schon fertig
 * skaliert. Ein Bild Versatz spielt beim Anklicken keine Rolle - das Fenster
 * wird 60-mal pro Sekunde neu gezeichnet.
 */
public abstract class BaseScreen extends Screen {

    /** Mausposition aus dem letzten Zeichendurchgang. */
    protected double mx;
    protected double my;

    protected BaseScreen(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.mx = mouseX;
        this.my = mouseY;
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Erst die selbst gezeichneten Flächen, dann die echten Widgets
        // (Texteingaben). Die überschneiden sich nirgends, deshalb ist die
        // Reihenfolge unkritisch.
        if (onClick(mx, my)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Klick auf eine selbst gezeichnete Fläche.
     *
     * @return true, wenn der Klick verbraucht wurde
     */
    protected boolean onClick(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
