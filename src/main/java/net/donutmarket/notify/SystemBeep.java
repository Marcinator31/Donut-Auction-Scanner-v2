package net.donutmarket.notify;

import net.donutmarket.config.DonutConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Alarmton, der NICHT durch Minecrafts Audio-Engine läuft.
 *
 * Warum das nötig ist: Minecraft hält die Sound-Engine an, sobald es die
 * Singleplayer-Welt pausiert - und das passiert standardmäßig, wenn das Fenster
 * den Fokus verliert. Wer AFK mit Kopfhörern daneben sitzt und in einem anderen
 * Programm ist, würde ein Pling über getSoundManager() also möglicherweise nie
 * hören.
 *
 * Diese Klasse synthetisiert den Ton selbst über javax.sound.sampled und spielt
 * ihn direkt über die Soundkarte. Das ist unabhängig von:
 *   - Minecrafts Pause-Zustand
 *   - Minecrafts Lautstärke-Reglern
 *   - dem Fensterfokus
 *   - ob überhaupt eine Welt geladen ist
 *
 * Es braucht keine Asset-Datei, der Ton wird berechnet.
 */
public final class SystemBeep {

    private static final float SAMPLE_RATE = 44_100f;

    /** Aufsteigender Dreiklang: A5 - C#6 - E6. Sticht gut durch Kopfhörer. */
    private static final double[] FREQUENCIES = {880.0, 1108.7, 1318.5};
    private static final int TONE_MILLIS = 140;
    private static final int GAP_MILLIS = 70;

    /** Verhindert, dass sich mehrere Alarme überlagern. */
    private static final AtomicBoolean PLAYING = new AtomicBoolean(false);

    /** Merkt sich, ob Audio auf diesem System überhaupt geht. */
    private static volatile boolean unavailable = false;

    private SystemBeep() {
    }

    /**
     * Spielt den Alarm. Darf von jedem Thread aufgerufen werden, blockiert nicht -
     * die Ausgabe läuft auf einem eigenen kurzlebigen Daemon-Thread.
     */
    public static void play() {
        if (unavailable || !PLAYING.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(SystemBeep::render, "DonutMarket-Beep");
        t.setDaemon(true);
        t.start();
    }

    private static void render() {
        SourceDataLine line = null;
        try {
            int repeats = Math.max(1, Math.min(8, DonutConfig.get().soundRepeats));
            double gain = Math.max(0.0, Math.min(1.0, DonutConfig.get().systemSoundVolume));
            if (gain <= 0.0) {
                return;
            }

            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();

            for (int i = 0; i < repeats; i++) {
                double freq = FREQUENCIES[Math.min(i, FREQUENCIES.length - 1)];
                // Bei mehr als drei Wiederholungen den Dreiklang weiter hochziehen
                if (i >= FREQUENCIES.length) {
                    freq = FREQUENCIES[FREQUENCIES.length - 1]
                            * Math.pow(1.06, i - FREQUENCIES.length + 1);
                }
                line.write(tone(freq, TONE_MILLIS, gain), 0, byteCount(TONE_MILLIS));
                if (i < repeats - 1) {
                    line.write(silence(GAP_MILLIS), 0, byteCount(GAP_MILLIS));
                }
            }
            line.drain();
        } catch (Exception e) {
            // Kein Audio-Gerät, exklusiv belegt, oder Headless-JVM.
            // Einmal melden, dann nie wieder versuchen.
            unavailable = true;
            System.out.println("[DonutMarket] System-Ton nicht verfügbar ("
                    + e.getClass().getSimpleName() + "), es bleibt beim Minecraft-Ton.");
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.close();
                } catch (Exception ignored) {
                    // egal
                }
            }
            PLAYING.set(false);
        }
    }

    private static int byteCount(int millis) {
        return (int) (SAMPLE_RATE * millis / 1000.0) * 2;
    }

    /**
     * Sinuston mit kurzem Ein- und Ausblenden. Ohne die Blende gibt es ein
     * hörbares Knacken an den Rändern.
     */
    private static byte[] tone(double freqHz, int millis, double gain) {
        int samples = (int) (SAMPLE_RATE * millis / 1000.0);
        byte[] buf = new byte[samples * 2];
        int fade = Math.max(1, samples / 12);

        for (int i = 0; i < samples; i++) {
            double envelope = 1.0;
            if (i < fade) {
                envelope = (double) i / fade;
            } else if (i > samples - fade) {
                envelope = (double) (samples - i) / fade;
            }
            double angle = 2.0 * Math.PI * i * freqHz / SAMPLE_RATE;
            short v = (short) (Math.sin(angle) * envelope * gain * Short.MAX_VALUE * 0.7);
            buf[i * 2] = (byte) (v & 0xFF);
            buf[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return buf;
    }

    private static byte[] silence(int millis) {
        return new byte[byteCount(millis)];
    }

    /** Für den Test-Button in den Einstellungen. */
    public static boolean isUnavailable() {
        return unavailable;
    }
}
