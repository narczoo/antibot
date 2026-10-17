package pl.antybot.plugin.manager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class IpManager {

    private final Map<String, Set<UUID>> connectionsPerIp = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<Long>> ipRecentConnections = new ConcurrentHashMap<>();

    // Globalny licznik nowych połączeń (Global CPS Limiter)
    private final ConcurrentLinkedDeque<Long> globalRecentConnections = new ConcurrentLinkedDeque<>();

    // Krótkie blokady tymczasowe w pamięci RAM (Temporary IP Rate-Limit Blocks)
    private final Map<String, Long> tempBlockedIps = new ConcurrentHashMap<>();

    // Ochrona przed wielokrotnym równoległym przetwarzaniem połączenia (Single-Flight Handshake)
    private final Set<String> activeHandshakes = ConcurrentHashMap.newKeySet();

    public void registerConnection(String ip, UUID uuid) {
        if (ip == null || uuid == null) return;
        connectionsPerIp.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    public void unregisterConnection(String ip, UUID uuid) {
        if (ip == null || uuid == null) return;
        Set<UUID> set = connectionsPerIp.get(ip);
        if (set != null) {
            set.remove(uuid);
            if (set.isEmpty()) {
                connectionsPerIp.remove(ip);
            }
        }
    }

    public int getConnectionCount(String ip) {
        if (ip == null) return 0;
        Set<UUID> set = connectionsPerIp.get(ip);
        return set != null ? set.size() : 0;
    }

    /**
     * Sprawdza czy na danym IP znajduje się już zweryfikowany gracz zaufany (Wsparcie dla współdzielonych IP)
     */
    public boolean hasTrustedPlayer(String ip, TrustManager trustManager) {
        if (ip == null || trustManager == null) return false;
        Set<UUID> set = connectionsPerIp.get(ip);
        if (set == null || set.isEmpty()) return false;

        for (UUID uuid : set) {
            if (trustManager.isTrusted(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Globalny limit nowych połączeń na sekundę na całym serwerze
     */
    public boolean isGlobalRateExceeded(int maxPerSecond) {
        if (maxPerSecond <= 0) return false;
        long now = System.currentTimeMillis();
        globalRecentConnections.addLast(now);
        long cutoff = now - 1000L;

        Long first;
        while ((first = globalRecentConnections.peekFirst()) != null && first < cutoff) {
            globalRecentConnections.pollFirst();
        }

        return globalRecentConnections.size() > maxPerSecond;
    }

    /**
     * Sprawdza nagłe serie połączeń z jednego IP (Burst Protection)
     */
    public boolean isBursting(String ip, int maxConnections, int windowSeconds) {
        if (ip == null || maxConnections <= 0 || windowSeconds <= 0) return false;
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        long cutoff = now - windowMillis;

        ConcurrentLinkedDeque<Long> queue = ipRecentConnections.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        queue.addLast(now);

        // Usuwanie prób starszych niż okno czasowe
        Long first;
        while ((first = queue.peekFirst()) != null && first < cutoff) {
            queue.pollFirst();
        }

        return queue.size() > maxConnections;
    }

    /**
     * Nadaje krótką blokadę tymczasową w pamięci RAM (np. 15s)
     */
    public void addTempBlock(String ip, int durationSeconds) {
        if (ip == null || durationSeconds <= 0) return;
        long expireAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        tempBlockedIps.put(ip, expireAt);
    }

    /**
     * Sprawdza ile sekund pozostało do końca tymczasowej blokady IP (0 = brak blokady)
     */
    public long getTempBlockRemainingSeconds(String ip) {
        if (ip == null) return 0;
        Long expireAt = tempBlockedIps.get(ip);
        if (expireAt == null) return 0;
        long diff = expireAt - System.currentTimeMillis();
        if (diff <= 0) {
            tempBlockedIps.remove(ip);
            return 0;
        }
        return (diff / 1000L) + 1;
    }

    /**
     * Ochrona Single-Flight: rezerwuje przetwarzanie połączenia dla danego IP/UUID
     */
    public boolean acquireHandshake(String ip, UUID uuid) {
        if (ip == null) return true;
        String key = ip + (uuid != null ? ":" + uuid : "");
        return activeHandshakes.add(key);
    }

    public void releaseHandshake(String ip, UUID uuid) {
        if (ip == null) return;
        String key = ip + (uuid != null ? ":" + uuid : "");
        activeHandshakes.remove(key);
    }

    public void cleanExpired(int windowSeconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - (windowSeconds * 1000L);

        // Czyszczenie kolejki burstów
        ipRecentConnections.entrySet().removeIf(entry -> {
            ConcurrentLinkedDeque<Long> queue = entry.getValue();
            Long first;
            while ((first = queue.peekFirst()) != null && first < cutoff) {
                queue.pollFirst();
            }
            return queue.isEmpty();
        });

        // Czyszczenie przedawnionych blokad tymczasowych
        tempBlockedIps.entrySet().removeIf(entry -> now >= entry.getValue());

        // Czyszczenie kolejki globalnej
        long globalCutoff = now - 2000L;
        Long first;
        while ((first = globalRecentConnections.peekFirst()) != null && first < globalCutoff) {
            globalRecentConnections.pollFirst();
        }
    }

    public void cleanExpiredBurst(int windowSeconds) {
        cleanExpired(windowSeconds);
    }

    public void clear() {
        connectionsPerIp.clear();
        ipRecentConnections.clear();
        globalRecentConnections.clear();
        tempBlockedIps.clear();
        activeHandshakes.clear();
    }
}
