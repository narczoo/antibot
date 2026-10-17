package pl.antybot.plugin.manager;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PingManager {

    private static final int MAX_PING_CACHE_SIZE = 25000;
    private final Map<String, Long> pingCache = new ConcurrentHashMap<>();

    public void recordPing(String ip) {
        if (ip == null) return;

        if (pingCache.size() >= MAX_PING_CACHE_SIZE) {
            cleanExpired(600); // Awaryjne oczyszczenie wpisów starszych niż 10 min
            if (pingCache.size() >= MAX_PING_CACHE_SIZE) {
                // Jeśli nadal pełne (np. DDoS na ping z dziesiątek tysięcy IP), usuwamy 10% najstarszych
                int toRemove = MAX_PING_CACHE_SIZE / 10;
                Iterator<String> it = pingCache.keySet().iterator();
                while (it.hasNext() && toRemove > 0) {
                    it.next();
                    it.remove();
                    toRemove--;
                }
            }
        }

        pingCache.put(ip, System.currentTimeMillis());
    }

    public boolean hasPinged(String ip, int maxAgeSeconds) {
        if (ip == null) return false;
        Long lastPing = pingCache.get(ip);
        if (lastPing == null) return false;

        long maxAgeMillis = maxAgeSeconds * 1000L;
        return (System.currentTimeMillis() - lastPing) <= maxAgeMillis;
    }

    public void cleanExpired(int maxAgeSeconds) {
        long now = System.currentTimeMillis();
        long maxAgeMillis = maxAgeSeconds * 1000L;
        pingCache.entrySet().removeIf(entry -> (now - entry.getValue()) > maxAgeMillis);
    }

    public int getCachedPingCount() {
        return pingCache.size();
    }

    public void clear() {
        pingCache.clear();
    }
}
