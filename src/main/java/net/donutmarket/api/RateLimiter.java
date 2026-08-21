package net.donutmarket.api;

/**
 * Simpler Token-Bucket. DonutSMP erlaubt laut Doku 250 Requests pro Minute und
 * API-Key - dieser Limiter hält uns unter einem selbst gesetzten, niedrigeren
 * Wert, damit parallel laufende Mods mit demselben Key nicht ausgesperrt werden.
 */
public final class RateLimiter {

    private final double refillPerMs;
    private final double capacity;

    private double tokens;
    private long lastRefill;

    public RateLimiter(int permitsPerMinute) {
        this.capacity = Math.max(1, permitsPerMinute);
        this.refillPerMs = this.capacity / 60_000.0;
        this.tokens = this.capacity;
        this.lastRefill = System.currentTimeMillis();
    }

    /** Blockiert so lange, bis ein Request erlaubt ist. Niemals auf dem Render-Thread aufrufen. */
    public synchronized void acquire() throws InterruptedException {
        while (true) {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return;
            }
            double missing = 1.0 - tokens;
            long waitMs = (long) Math.ceil(missing / refillPerMs);
            wait(Math.max(25, Math.min(waitMs, 5_000)));
        }
    }

    /** Nicht-blockierende Variante: true, wenn ein Request frei war. */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long delta = now - lastRefill;
        if (delta > 0) {
            tokens = Math.min(capacity, tokens + delta * refillPerMs);
            lastRefill = now;
        }
    }

    public synchronized int available() {
        refill();
        return (int) tokens;
    }
}
