package pl.antybot.plugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.config.Settings;
import pl.antybot.plugin.manager.AntiVpnManager;

import java.net.InetAddress;
import java.util.UUID;
import java.util.regex.Pattern;

public class PreLoginListener implements Listener {

    private final AntiBotPlugin plugin;

    public PreLoginListener(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        UUID uuid = event.getUniqueId();
        InetAddress address = event.getAddress();
        if (address == null || name == null) return;

        String ip = address.getHostAddress();
        Settings settings = plugin.getSettings();

        // 1. Ochrona przed wielokrotnym równoległym przetwarzaniem połączenia (Single-Flight Handshake)
        if (settings.isSingleFlightHandshake()) {
            if (!plugin.getIpManager().acquireHandshake(ip, uuid)) {
                denyLogin(event, settings.getDuplicateHandshakeKick(), "Równoległe połączenie w toku (Single-Flight)", name, ip, uuid);
                return;
            }
        }

        // 2. Krótkie blokady tymczasowe w pamięci RAM (Rate-Limit Temp-Block)
        if (settings.isTempBlockEnabled()) {
            long remaining = plugin.getIpManager().getTempBlockRemainingSeconds(ip);
            if (remaining > 0) {
                String kickMsg = settings.getTempBlockKick().replace("{SECONDS}", String.valueOf(remaining));
                denyLogin(event, kickMsg, "Tymczasowa blokada rate-limit (" + remaining + "s)", name, ip, uuid);
                return;
            }
        }

        // 3. Globalny limit nowych połączeń na sekundę (Global CPS Limiter)
        if (settings.isGlobalThrottleEnabled()) {
            if (plugin.getIpManager().isGlobalRateExceeded(settings.getGlobalMaxJoinsPerSecond())) {
                if (!plugin.getTrustManager().isTrusted(uuid)) {
                    denyLogin(event, settings.getGlobalThrottleKick(), "Przekroczono globalny limit połączeń (" + settings.getGlobalMaxJoinsPerSecond() + "/s)", name, ip, uuid);
                    return;
                }
            }
        }

        // 4. Sprawdzenie poprawności i filtru nicku
        if (settings.isNicknameFilterEnabled()) {
            if (name.length() < settings.getMinNickLength() || name.length() > settings.getMaxNickLength()) {
                denyLogin(event, settings.getInvalidNicknameKick(), "Nieprawidłowa długość nicku", name, ip, uuid);
                return;
            }

            for (Pattern pattern : settings.getBlockedNickPatterns()) {
                if (pattern.matcher(name).find()) {
                    denyLogin(event, settings.getInvalidNicknameKick(), "Wzorzec nicku botów (" + pattern.pattern() + ")", name, ip, uuid);
                    return;
                }
            }
        }

        boolean isTrusted = plugin.getTrustManager().isTrusted(uuid);
        boolean hasTrustedOnIp = plugin.getIpManager().hasTrustedPlayer(ip, plugin.getTrustManager());

        // 5. Sprawdzenie limitu jednoczesnych połączeń z danego IP z uwzględnieniem współdzielonych adresów (Shared IP Grace)
        if (settings.isIpLimitEnabled()) {
            int maxAllowed = settings.getMaxConnectionsPerIp();
            if (settings.isSharedIpEnabled() && (isTrusted || hasTrustedOnIp)) {
                maxAllowed += settings.getSharedIpExtraConnections();
            }

            int currentConnections = plugin.getIpManager().getConnectionCount(ip);
            if (currentConnections >= maxAllowed) {
                String kickMsg = settings.getIpLimitKick().replace("{LIMIT}", String.valueOf(maxAllowed));
                denyLogin(event, kickMsg, "Przekroczono limit kont na IP (" + currentConnections + "/" + maxAllowed + ")", name, ip, uuid);
                return;
            }
        }

        // 6. Limit nagłych serii połączeń z tego samego IP (Burst Protection) z automatyczną blokadą tymczasową
        if (settings.isIpBurstProtectionEnabled()) {
            int maxBurst = settings.getIpBurstMaxConnections();
            if (settings.isSharedIpEnabled() && (isTrusted || hasTrustedOnIp)) {
                maxBurst += settings.getSharedIpExtraConnections();
            }

            if (plugin.getIpManager().isBursting(ip, maxBurst, settings.getIpBurstWindowSeconds())) {
                if (settings.isTempBlockEnabled()) {
                    plugin.getIpManager().addTempBlock(ip, settings.getTempBlockDurationSeconds());
                }
                denyLogin(event, settings.getIpBurstKick(), "Zbyt częste próby połączeń (Burst: " + maxBurst + " w " + settings.getIpBurstWindowSeconds() + "s)", name, ip, uuid);
                return;
            }
        }

        // 7. Sprawdzenie pingu (ServerListPing) - pomijane dla zaufanych graczy
        if (settings.isPingCheckEnabled() && !isTrusted) {
            boolean hasPinged = plugin.getPingManager().hasPinged(ip, settings.getMaxPingAgeSeconds());
            if (!hasPinged) {
                denyLogin(event, settings.getPingCheckKick(), "Brak odświeżenia listy serwerów (brak pingu)", name, ip, uuid);
                return;
            }
        }

        // 8. Asynchroniczne sprawdzenie Anti-VPN / Proxy / Hosting z limitami i polityką fail-open/fail-closed
        if (settings.isAntiVpnEnabled()) {
            AntiVpnManager vpnManager = plugin.getAntiVpnManager();
            if (vpnManager != null) {
                AntiVpnManager.VpnResult res = vpnManager.checkSyncWithTimeout(ip, name, uuid, isTrusted);
                if (res.isVpn()) {
                    denyLogin(event, settings.getAntiVpnKick(), "Wykryto VPN / Proxy / Hosting (" + res.getReason() + ")", name, ip, uuid);
                    return;
                } else if (res.getStatus() == AntiVpnManager.VpnCheckStatus.OVERLOAD && "FAIL_CLOSED".equalsIgnoreCase(settings.getAntiVpnOverloadPolicy())) {
                    denyLogin(event, settings.getAntiVpnOverloadKick(), "Przeciążenie weryfikacji Anti-VPN (FAIL_CLOSED)", name, ip, uuid);
                    return;
                } else if (res.getStatus() == AntiVpnManager.VpnCheckStatus.ERROR && "FAIL_CLOSED".equalsIgnoreCase(settings.getAntiVpnFailurePolicy())) {
                    denyLogin(event, settings.getAntiVpnOverloadKick(), "Błąd weryfikacji Anti-VPN (FAIL_CLOSED)", name, ip, uuid);
                    return;
                }
            }
        }

        // 9. Rejestracja próby wejścia i kontrola trybu ataku
        plugin.getAttackManager().registerJoinAttempt();

        if (plugin.getAttackManager().isAttackMode()) {
            // Zaufany gracz omija blokadę trybu ataku
            if (isTrusted) {
                return;
            }

            // Opcja ponownego połączenia (Reconnect Verification)
            if (settings.isRequireReconnect()) {
                boolean passed = plugin.getAttackManager().handleReconnectCheck(ip);
                if (!passed) {
                    long remaining = plugin.getAttackManager().getRemainingReconnectSeconds(ip);
                    String kickMsg = settings.getReconnectWaitKick().replace("{SECONDS}", String.valueOf(remaining));
                    denyLogin(event, kickMsg, "Weryfikacja ponownego połączenia (odczekaj " + remaining + "s)", name, ip, uuid);
                    return;
                }
            } else if (settings.isBlockNewPlayers()) {
                long remaining = plugin.getAttackManager().getRemainingAttackSeconds();
                String kickMsg = settings.getAttackModeKick().replace("{TIME}", String.valueOf(remaining));
                denyLogin(event, kickMsg, "Aktywny tryb ataku", name, ip, uuid);
                return;
            }
        }
    }

    private void denyLogin(AsyncPlayerPreLoginEvent event, String kickMessage, String reason, String player, String ip, UUID uuid) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
        plugin.getIpManager().releaseHandshake(ip, uuid);
        plugin.getAttackManager().incrementBlockedBots(player, reason, ip);
    }
}
