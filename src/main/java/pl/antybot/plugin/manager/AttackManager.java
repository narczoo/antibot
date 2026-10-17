package pl.antybot.plugin.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.antybot.plugin.AntiBotPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class AttackManager {

    private final AntiBotPlugin plugin;
    private final ConcurrentLinkedDeque<Long> recentJoins = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> reconnectAllowedAt = new ConcurrentHashMap<>();

    private volatile boolean attackMode = false;
    private volatile long attackEndTime = 0L;
    private BukkitTask tickerTask = null;

    private final AtomicInteger blockedBotsCount = new AtomicInteger(0);
    private final AtomicInteger attacksCount = new AtomicInteger(0);

    // Buforowanie i agregacja powiadomień podczas ataku (zapobieganie lagom głównego wątku)
    private final AtomicInteger bufferedBlockedCount = new AtomicInteger(0);
    private volatile String lastBlockedPlayer = null;
    private volatile String lastBlockedReason = null;
    private volatile long lastDirectAlertTime = 0L;

    public AttackManager(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTicker() {
        stopTicker();
        this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 20L, 20L);
    }

    public void stopTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        flushBufferedBotAlerts();
    }

    private void onTick() {
        long now = System.currentTimeMillis();
        if (attackMode && now >= attackEndTime) {
            stopAttackMode();
        }
        flushBufferedBotAlerts();
    }

    public void registerJoinAttempt() {
        long now = System.currentTimeMillis();
        recentJoins.addLast(now);
        cleanOldJoins(now);

        if (!plugin.getSettings().isAttackModeEnabled()) {
            return;
        }

        int triggerJoins = plugin.getSettings().getTriggerJoins();
        if (recentJoins.size() >= triggerJoins) {
            triggerAttackMode(plugin.getSettings().getAttackDurationSeconds());
        }
    }

    public int getCurrentCPS() {
        cleanOldJoins(System.currentTimeMillis());
        return recentJoins.size();
    }

    private void cleanOldJoins(long now) {
        long windowMillis = plugin.getSettings().getTriggerSeconds() * 1000L;
        long cutoff = now - windowMillis;
        Long first;
        while ((first = recentJoins.peekFirst()) != null && first < cutoff) {
            recentJoins.pollFirst();
        }
    }

    public synchronized void triggerAttackMode(int durationSeconds) {
        long now = System.currentTimeMillis();
        long newEndTime = now + (durationSeconds * 1000L);

        if (!attackMode) {
            attackMode = true;
            attackEndTime = newEndTime;
            attacksCount.incrementAndGet();

            notifyAdmins(plugin.getSettings().getAdminAttackStarted().replace("{DURATION}", String.valueOf(durationSeconds)));
        } else {
            // Przedłużenie trwającego ataku
            if (newEndTime > attackEndTime) {
                attackEndTime = newEndTime;
            }
        }
    }

    public synchronized void stopAttackMode() {
        if (!attackMode) return;
        attackMode = false;
        attackEndTime = 0L;
        reconnectAllowedAt.clear();
        notifyAdmins(plugin.getSettings().getAdminAttackStopped());
    }

    public boolean isAttackMode() {
        return attackMode;
    }

    public long getRemainingAttackSeconds() {
        if (!attackMode) return 0;
        long remaining = (attackEndTime - System.currentTimeMillis()) / 1000L;
        return Math.max(0, remaining);
    }

    public boolean handleReconnectCheck(String ip) {
        long now = System.currentTimeMillis();
        Long allowedAt = reconnectAllowedAt.get(ip);

        if (allowedAt == null) {
            long delayMillis = plugin.getSettings().getReconnectDelaySeconds() * 1000L;
            reconnectAllowedAt.put(ip, now + delayMillis);
            return false;
        }

        if (now < allowedAt) {
            return false;
        }

        // Gracz poczekał odpowiednią ilość czasu (okno ważności 45 sekund)
        if (now <= (allowedAt + 45000L)) {
            reconnectAllowedAt.remove(ip);
            return true;
        }

        // Zbyt długi czas bez ponownego połączenia, odświeżamy
        long delayMillis = plugin.getSettings().getReconnectDelaySeconds() * 1000L;
        reconnectAllowedAt.put(ip, now + delayMillis);
        return false;
    }

    public long getRemainingReconnectSeconds(String ip) {
        Long allowedAt = reconnectAllowedAt.get(ip);
        if (allowedAt == null) return plugin.getSettings().getReconnectDelaySeconds();
        long diff = (allowedAt - System.currentTimeMillis()) / 1000L;
        return Math.max(1, diff);
    }

    public void incrementBlockedBots(String player, String reason, String ip) {
        blockedBotsCount.incrementAndGet();

        if (!plugin.getSettings().isNotifyOnBotBlock()) {
            return;
        }

        long now = System.currentTimeMillis();
        // W stanie spokoju i przy niskim natężeniu (odstęp > 600ms) wysyłamy powiadomienie natychmiast
        if (!attackMode && (now - lastDirectAlertTime) >= 600L && bufferedBlockedCount.get() == 0) {
            lastDirectAlertTime = now;
            String msg = plugin.getSettings().getAdminBotBlocked()
                    .replace("{PLAYER}", player != null ? player : "Bot")
                    .replace("{REASON}", reason != null ? reason : "Filtr")
                    .replace("{IP}", ip != null ? ip : "unknown");
            notifyAdmins(msg);
            return;
        }

        // Podczas ataku lub fali botów buforujemy powiadomienia, chroniąc główny wątek serwera przed lagami
        lastBlockedPlayer = player;
        lastBlockedReason = reason;
        bufferedBlockedCount.incrementAndGet();
    }

    private void flushBufferedBotAlerts() {
        int count = bufferedBlockedCount.getAndSet(0);
        if (count <= 0) return;

        String formatted;
        String consoleLog;
        if (count == 1) {
            formatted = plugin.getSettings().getPrefix() + plugin.getSettings().getAdminBotBlocked()
                    .replace("{PLAYER}", lastBlockedPlayer != null ? lastBlockedPlayer : "Bot")
                    .replace("{REASON}", lastBlockedReason != null ? lastBlockedReason : "Filtr")
                    .replace("{IP}", "...");
            consoleLog = "[AntyBot] Zablokowano bota: " + lastBlockedPlayer + " (" + lastBlockedReason + ")";
        } else {
            formatted = plugin.getSettings().getPrefix() + "§c§l[ATAK] §7Zablokowano §e§l" + count + " §7prób botów w ostatniej sekundzie! §8(Ostatni: §e" + lastBlockedPlayer + "§8 | §c" + lastBlockedReason + "§8)";
            consoleLog = "[AntyBot] Zablokowano " + count + " botów w ciągu ostatniej sekundy. Ostatni: " + lastBlockedPlayer;
        }

        Runnable dispatch = () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("antibot.admin")) {
                    player.sendMessage(formatted);
                    if (plugin.getSettings().isShowActionbar() && count > 1) {
                        player.sendActionBar("§c§l[AntyBot] §cZablokowano §e" + count + " §cbotów/s");
                    }
                }
            }
            plugin.getLogger().info(consoleLog);
        };

        if (Bukkit.isPrimaryThread()) {
            dispatch.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, dispatch);
        }
    }

    public void notifyAdmins(String message) {
        if (message == null || message.isEmpty()) return;
        String formatted = plugin.getSettings().getPrefix() + message;

        Runnable dispatch = () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("antibot.admin")) {
                    player.sendMessage(formatted);
                }
            }
            plugin.getLogger().info(message);
        };

        if (Bukkit.isPrimaryThread()) {
            dispatch.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, dispatch);
        }
    }

    public int getBlockedBotsCount() {
        return blockedBotsCount.get();
    }

    public int getAttacksCount() {
        return attacksCount.get();
    }

    public void cleanExpiredReconnects() {
        long now = System.currentTimeMillis();
        reconnectAllowedAt.entrySet().removeIf(entry -> (now - entry.getValue()) > 60000L);
    }

    public void clear() {
        recentJoins.clear();
        reconnectAllowedAt.clear();
        stopAttackMode();
        blockedBotsCount.set(0);
        attacksCount.set(0);
        bufferedBlockedCount.set(0);
    }
}
