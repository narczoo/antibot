package pl.antybot.plugin.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.antybot.plugin.AntiBotPlugin;

import java.net.InetSocketAddress;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final AntiBotPlugin plugin;

    public PlayerListener(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        InetSocketAddress address = player.getAddress();
        if (address != null && address.getAddress() != null) {
            String ip = address.getAddress().getHostAddress();
            plugin.getIpManager().registerConnection(ip, uuid);
            plugin.getIpManager().releaseHandshake(ip, uuid);
        }

        // Gracz z uprawnieniem bypass lub stały gracz
        if (player.hasPermission("antibot.bypass")) {
            plugin.getTrustManager().addTrusted(uuid);
            return;
        }

        // Informowanie admina przy dołączeniu o trwającym ataku
        if (player.hasPermission("antibot.admin") && plugin.getAttackManager().isAttackMode()) {
            long remaining = plugin.getAttackManager().getRemainingAttackSeconds();
            player.sendMessage(plugin.getSettings().getPrefix() + "§c§lUWAGA! §eNa serwerze trwa aktywny tryb obrony przed botami (§c" + remaining + "s§e).");
        }

        // Informowanie admina o dostępnej aktualizacji pluginu
        if (player.hasPermission("antibot.admin") && plugin.getSettings().isUpdateCheckerNotifyAdmins()) {
            if (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
                player.sendMessage(plugin.getSettings().getPrefix() + "§eDostępna jest nowa wersja pluginu: §a§lv" + plugin.getUpdateChecker().getLatestVersion() + " §7(zainstalowana: §cv" + plugin.getUpdateChecker().getCurrentVersion() + "§7)!");
                player.sendMessage(plugin.getSettings().getPrefix() + "§7Pobierz aktualizację z GitHub: §b" + plugin.getUpdateChecker().getDownloadUrl());
            }
        }

        // Ograniczona kolejka weryfikacji i liczba aktywnych zadań weryfikacyjnych (Captcha / Ruch)
        boolean needsCaptcha = plugin.getCaptchaManager() != null && plugin.getCaptchaManager().shouldRequireCaptcha(player);
        boolean needsMovement = !needsCaptcha && !plugin.getTrustManager().isTrusted(uuid) && !player.hasPlayedBefore() && plugin.getSettings().isMovementCheckEnabled();

        if (needsCaptcha || needsMovement) {
            int maxQueue = plugin.getSettings().getMaxConcurrentVerifications();
            int currentActive = (plugin.getCaptchaManager() != null ? plugin.getCaptchaManager().getActiveSessionCount() : 0)
                    + (plugin.getVerificationManager() != null ? plugin.getVerificationManager().getActiveSessionCount() : 0);

            if (maxQueue > 0 && currentActive >= maxQueue) {
                String kickMsg = plugin.getSettings().getVerificationQueueFullKick()
                        .replace("{LIMIT}", String.valueOf(maxQueue));
                player.kickPlayer(kickMsg);
                return;
            }
        }

        // Sprawdzanie czy gracz musi rozwiazac captche (zarowno Premium jak i Non-Premium, jesli jest niezaufany)
        if (needsCaptcha) {
            plugin.getCaptchaManager().startCaptcha(player);
            return;
        }

        // Jesli captcha nie jest wymagana (np. gracz juz zaufany lub inny tryb)
        if (!plugin.getTrustManager().isTrusted(uuid)) {
            if (player.hasPlayedBefore()) {
                plugin.getTrustManager().addTrusted(uuid);
            } else if (plugin.getSettings().isMovementCheckEnabled()) {
                plugin.getVerificationManager().startVerification(player);
            } else {
                plugin.getTrustManager().addTrusted(uuid);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        InetSocketAddress address = player.getAddress();
        if (address != null && address.getAddress() != null) {
            String ip = address.getAddress().getHostAddress();
            plugin.getIpManager().unregisterConnection(ip, uuid);
            plugin.getIpManager().releaseHandshake(ip, uuid);
        }

        plugin.getVerificationManager().cleanup(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getSettings().isMovementCheckEnabled()) return;

        Player player = event.getPlayer();
        if (plugin.getVerificationManager().isUnverified(player.getUniqueId())) {
            plugin.getVerificationManager().checkMovement(player, event.getFrom(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSettings().isMovementCheckEnabled() &&
                plugin.getSettings().isBlockChatBeforeVerification() &&
                plugin.getVerificationManager().isUnverified(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getChatBlocked());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSettings().isMovementCheckEnabled() &&
                plugin.getSettings().isBlockCommandsBeforeVerification() &&
                plugin.getVerificationManager().isUnverified(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getCommandBlocked());
        }
    }
}
