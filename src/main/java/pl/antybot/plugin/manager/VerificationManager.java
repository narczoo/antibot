package pl.antybot.plugin.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.auth.AuthAccount;
import pl.antybot.plugin.auth.AuthManager;
import pl.antybot.plugin.config.Settings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationManager {

    private final AntiBotPlugin plugin;
    private final Map<UUID, VerificationSession> sessions = new ConcurrentHashMap<>();

    public VerificationManager(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void startVerification(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        cleanup(uuid);

        Settings s = plugin.getSettings();
        int cameraTarget = s.getRequiredCameraMoves();
        int walkTarget = s.getRequiredWalkSteps();
        Location loc = player.getLocation();

        VerificationSession session = new VerificationSession(
                uuid,
                cameraTarget,
                walkTarget,
                loc.getYaw(),
                loc.getPitch(),
                loc
        );
        sessions.put(uuid, session);

        // Powiadomienie na czacie
        String prompt = s.getMovementPrompt()
                .replace("{CAMERA_TARGET}", String.valueOf(cameraTarget))
                .replace("{WALK_TARGET}", String.valueOf(walkTarget));
        player.sendMessage(s.getPrefix() + prompt);

        // Natychmiastowe odswiezenie paska Actionbar
        updateActionbar(player, session);

        // Cykliczne odswiezanie paska Actionbar (co 10 tickow = 0.5s), aby nie gasl
        BukkitTask abTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isVerifying(uuid)) {
                    cancel();
                    return;
                }
                updateActionbar(player, session);
            }
        }.runTaskTimer(plugin, 10L, 10L);
        session.setActionbarTask(abTask);

        // Limit czasu na weryfikacje ruchu
        int timeoutSeconds = s.getMovementTimeoutSeconds();
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isVerifying(uuid)) {
                cleanup(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.kickPlayer(plugin.getSettings().getPrefix() + "§cUpłynął limit czasu na weryfikację ruchu!");
                }
            }
        }, timeoutSeconds * 20L);
        session.setTimeoutTask(timeoutTask);
    }

    public boolean isVerifying(UUID uuid) {
        return uuid != null && sessions.containsKey(uuid);
    }

    public boolean isUnverified(UUID uuid) {
        return isVerifying(uuid);
    }

    public void checkMovement(Player player, Location from, Location to) {
        if (player == null || to == null || from == null) return;
        UUID uuid = player.getUniqueId();
        VerificationSession session = sessions.get(uuid);
        if (session == null) return;

        // ETAP 1: Ruch kamerą (np. 5 razy)
        if (!session.isCameraPhaseComplete()) {
            float yawDiff = Math.abs(to.getYaw() - session.getLastYaw());
            float pitchDiff = Math.abs(to.getPitch() - session.getLastPitch());

            // Zmiana o co najmniej 15 stopni yaw lub 10 stopni pitch
            if (yawDiff >= 15.0f || pitchDiff >= 10.0f) {
                session.setLastYaw(to.getYaw());
                session.setLastPitch(to.getPitch());
                session.incrementCameraMoves();

                if (session.getCameraMoves() >= session.getTargetCameraMoves()) {
                    session.setCameraPhaseComplete(true);
                    session.setLastStepLocation(to.clone());
                    try {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    } catch (Throwable ignored) {
                    }
                } else {
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
                    } catch (Throwable ignored) {
                    }
                }
                updateActionbar(player, session);
            }
            return;
        }

        // ETAP 2: Kroki / ruch przemieszczeniowy (np. 5 kroków)
        Location lastStep = session.getLastStepLocation();
        if (lastStep == null || lastStep.getWorld() != to.getWorld()) {
            session.setLastStepLocation(to.clone());
            return;
        }

        double dx = to.getX() - lastStep.getX();
        double dz = to.getZ() - lastStep.getZ();
        double distSq = dx * dx + dz * dz;

        // 1 krok = pokonanie dystansu co najmniej 0.8 bloku w poziomie
        if (distSq >= 0.64) { // 0.8^2 = 0.64
            session.setLastStepLocation(to.clone());
            session.incrementWalkSteps();

            if (session.getWalkSteps() >= session.getTargetWalkSteps()) {
                passVerification(player);
            } else {
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.8f);
                } catch (Throwable ignored) {
                }
                updateActionbar(player, session);
            }
        }
    }

    public void updateActionbar(Player player, VerificationSession session) {
        if (player == null || !player.isOnline() || session == null) return;
        Settings s = plugin.getSettings();
        if (!session.isCameraPhaseComplete()) {
            String msg = s.getMovementCameraActionbar()
                    .replace("{CURRENT}", String.valueOf(session.getCameraMoves()))
                    .replace("{TARGET}", String.valueOf(session.getTargetCameraMoves()));
            player.sendActionBar(msg);
        } else {
            String msg = s.getMovementWalkActionbar()
                    .replace("{CURRENT}", String.valueOf(session.getWalkSteps()))
                    .replace("{TARGET}", String.valueOf(session.getTargetWalkSteps()));
            player.sendActionBar(msg);
        }
    }

    public void passVerification(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        cleanup(uuid);

        plugin.getTrustManager().addTrusted(uuid);
        plugin.getTrustManager().saveAsync();

        Settings s = plugin.getSettings();
        player.sendMessage(s.getPrefix() + s.getMovementVerified());

        String verifiedAb = s.getMovementVerifiedActionbar();
        if (verifiedAb != null && !verifiedAb.isEmpty()) {
            player.sendActionBar(verifiedAb);
        }

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } catch (Throwable ignored) {
        }

        // Przejście do autoryzacji gracza (Auto-login Premium lub sesja /login):
        if (plugin.getSettings().isAuthEnabled() && plugin.getAuthManager() != null && !plugin.getAuthManager().isLoggedIn(uuid)) {
            AuthManager am = plugin.getAuthManager();
            AuthAccount account = am.getOrCreateAccount(player.getName());

            if (account.isPremium()) {
                am.autoLoginPremium(player, account);
                return;
            }

            // Sprawdzamy sesje auto-logowania IP dla graczy zarejestrowanych
            String currentIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
            if (s.isAuthSessionEnabled() && account.isRegistered() && currentIp.equals(account.getLastIp())) {
                long maxSessionMillis = s.getAuthSessionTimeoutMinutes() * 60L * 1000L;
                if (System.currentTimeMillis() - account.getLastLogin() <= maxSessionMillis) {
                    am.finishAuth(player);
                    account.setLastLogin(System.currentTimeMillis());
                    account.setLastIp(currentIp);
                    am.saveAsync();
                    player.sendMessage(s.getPrefix() + s.getAuthSessionRestored());
                    return;
                }
            }

            // Jesli gracz nie ma aktywnej sesji IP, uruchamiamy wlasciwa sesje logowania/rejestracji
            am.startAuthSession(player);
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public boolean canStartNewVerification(int maxConcurrent) {
        if (maxConcurrent <= 0) return true;
        return sessions.size() < maxConcurrent;
    }

    public void cleanup(UUID uuid) {
        if (uuid == null) return;
        VerificationSession session = sessions.remove(uuid);
        if (session != null) {
            session.cancelTasks();
        }
    }

    public void clearAll() {
        for (VerificationSession session : sessions.values()) {
            if (session != null) {
                session.cancelTasks();
            }
        }
        sessions.clear();
    }
}
