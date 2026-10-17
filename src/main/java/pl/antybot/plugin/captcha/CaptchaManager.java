package pl.antybot.plugin.captcha;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.auth.AuthAccount;
import pl.antybot.plugin.auth.AuthManager;
import pl.antybot.plugin.config.Settings;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {

    private final AntiBotPlugin plugin;
    private final Map<UUID, CaptchaSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<Location, UUID> signBlockLocations = new ConcurrentHashMap<>();
    private final Random random = new SecureRandom();

    public CaptchaManager(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(UUID uuid) {
        return uuid != null && activeSessions.containsKey(uuid);
    }

    public boolean shouldRequireCaptcha(Player player) {
        if (player == null || !plugin.getSettings().isCaptchaEnabled()) return false;
        if (player.hasPermission("antibot.bypass")) return false;
        if (plugin.getTrustManager().isTrusted(player.getUniqueId())) return false;

        String mode = plugin.getSettings().getCaptchaTriggerMode();
        if ("ALWAYS".equalsIgnoreCase(mode)) {
            return true;
        } else if ("ATTACK_ONLY".equalsIgnoreCase(mode) && plugin.getAttackManager().isAttackMode()) {
            return true;
        }
        return false;
    }

    public CaptchaSession getSession(UUID uuid) {
        return uuid != null ? activeSessions.get(uuid) : null;
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public boolean canStartNewCaptcha(int maxConcurrent) {
        if (maxConcurrent <= 0) return true;
        return activeSessions.size() < maxConcurrent;
    }

    public CaptchaSession getSessionByLocation(Location location) {
        if (location == null) return null;
        UUID uuid = signBlockLocations.get(location.getBlock().getLocation());
        return uuid != null ? activeSessions.get(uuid) : null;
    }

    public String generateCode() {
        Settings s = plugin.getSettings();
        String chars = s.getCaptchaCharacters();
        if (chars == null || chars.isEmpty()) {
            chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
        }
        int length = Math.max(1, s.getCaptchaCodeLength());

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public int determineTargetLine() {
        String conf = plugin.getSettings().getCaptchaRequiredLine();
        if ("RANDOM".equalsIgnoreCase(conf)) {
            return random.nextInt(4); // 0, 1, 2, 3
        }
        try {
            int parsed = Integer.parseInt(conf);
            return Math.max(0, Math.min(3, parsed - 1));
        } catch (NumberFormatException e) {
            return random.nextInt(4);
        }
    }

    public void startCaptcha(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        // Usun poprzednia sesje jesli istniala
        cancelSession(uuid, true);

        String code = generateCode();
        int targetLine = determineTargetLine();

        CaptchaSession session = new CaptchaSession(uuid, code, targetLine, plugin.getSettings().getCaptchaMaxAttempts(), player.getLocation().clone());
        activeSessions.put(uuid, session);

        // Efekt oslepienia (Blindness) na czas rozwiazywania captchy
        if (plugin.getSettings().isCaptchaBlindness()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, plugin.getSettings().getCaptchaTimeoutSeconds() * 20 + 60, 1, false, false));
        }

        // Wyswietl powiadomienia graczowi
        displayPrompts(player, session);

        // Uruchomienie cyklicznego odswiezania KODU wylacznie na Actionbarze
        BukkitTask reminderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isPending(uuid)) {
                    cancel();
                    return;
                }
                String currentCode = session.getCode();
                String lineStr = String.valueOf(session.getDisplayLine());
                Settings s = plugin.getSettings();
                long remaining = session.getRemainingSeconds(s.getCaptchaTimeoutSeconds());

                String actionbar = s.getCaptchaActionbar()
                        .replace("{CODE}", currentCode)
                        .replace("{TIME}", String.valueOf(remaining))
                        .replace("{SECONDS}", String.valueOf(remaining))
                        .replace("{LINE}", lineStr);
                player.sendActionBar(actionbar);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        session.setReminderTask(reminderTask);

        // Zadanie limitu czasu (Timeout)
        int timeoutSeconds = plugin.getSettings().getCaptchaTimeoutSeconds();
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isPending(uuid)) {
                restoreBlock(session);
                cancelSession(uuid, false);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    String kickMsg = plugin.getSettings().getCaptchaKickTimeout().replace("{SECONDS}", String.valueOf(timeoutSeconds));
                    p.kickPlayer(kickMsg);
                    String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
                    plugin.getAttackManager().incrementBlockedBots(p.getName(), "Przekroczono czas captchy", ip);
                }
            }
        }, timeoutSeconds * 20L);
        session.setTimeoutTask(timeoutTask);

        // Otworz GUI tabliczki po krotkim opoznieniu (aby klient zaladowal swiat i chunki)
        int delay = Math.max(10, plugin.getSettings().getCaptchaOpenDelayTicks());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isPending(uuid)) {
                openSignGui(player, session);
            }
        }, delay);
    }

    public void openSignGui(Player player, CaptchaSession session) {
        if (player == null || !player.isOnline() || session == null) return;
        session.setLastGuiOpenAttempt(System.currentTimeMillis());

        // Przywroc poprzedni blok jesli byl juz postawiony
        restoreBlock(session);

        Location loc = findSafeSignLocation(player.getLocation().clone());
        Block block = loc.getBlock();
        Location blockLoc = block.getLocation();

        session.setSignLocation(blockLoc);
        session.setOriginalBlockData(block.getBlockData());
        signBlockLocations.put(blockLoc, session.getPlayerUuid());

        // Ustawienie tabliczki
        block.setType(Material.OAK_SIGN, false);
        if (block.getState() instanceof Sign sign) {
            sign.setWaxed(false);
            sign.setEditable(true);

            try {
                sign.setAllowedEditorUniqueId(player.getUniqueId());
            } catch (Throwable ignored) {
            }

            SignSide front = sign.getSide(Side.FRONT);
            int target = session.getTargetLine();

            String[] setupLines = new String[4];
            setupLines[target] = ""; // linijka, w ktorej gracz musi wpisac kod

            if (target == 0) {
                setupLines[1] = "^^ TUTAJ KOD ^^";
                setupLines[2] = "------------";
                setupLines[3] = "------------";
            } else if (target == 1) {
                setupLines[0] = "vv TUTAJ KOD vv";
                setupLines[2] = "------------";
                setupLines[3] = "------------";
            } else if (target == 2) {
                setupLines[0] = "------------";
                setupLines[1] = "vv TUTAJ KOD vv";
                setupLines[3] = "------------";
            } else {
                setupLines[0] = "------------";
                setupLines[1] = "------------";
                setupLines[2] = "vv TUTAJ KOD vv";
            }

            for (int i = 0; i < 4; i++) {
                front.setLine(i, setupLines[i]);
            }
            sign.update(true, false);

            // Bezposrednie wyslanie pakietu aktualizacji bloku do klienta gracza
            player.sendBlockChange(blockLoc, block.getBlockData());

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isPending(player.getUniqueId())) {
                    sendOpenSignPacket(player, sign);
                }
            }, 3L);
        }
    }

    public void sendOpenSignPacket(Player player, Sign sign) {
        if (player == null || !player.isOnline() || sign == null) return;
        try {
            player.openSign(sign, Side.FRONT);
        } catch (Throwable t) {
            try {
                player.openSign(sign);
            } catch (Throwable ignored) {
            }
        }
    }

    public void reopenSignGui(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        CaptchaSession session = getSession(uuid);
        if (session == null) return;

        long now = System.currentTimeMillis();
        // Ochrona przed spamem ponownych otwarć (cooldown 400ms)
        if (now - session.getLastGuiOpenAttempt() < 400) {
            return;
        }
        session.setLastGuiOpenAttempt(now);

        Location signLoc = session.getSignLocation();
        if (signLoc != null && signLoc.getBlock().getState() instanceof Sign sign) {
            player.sendBlockChange(signLoc, signLoc.getBlock().getBlockData());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isPending(uuid)) {
                    sendOpenSignPacket(player, sign);
                }
            }, 1L);
        } else {
            openSignGui(player, session);
        }
    }

    private Location findSafeSignLocation(Location base) {
        Location candidate = base.getBlock().getLocation();
        if (candidate.getWorld() != null && !candidate.getChunk().isLoaded()) {
            candidate.getChunk().load();
        }

        if (candidate.getBlockY() < 315) {
            candidate.add(0, 2, 0);
        } else {
            candidate.subtract(0, 2, 0);
        }

        // Upewnij sie, ze lokalizacja nie koliduje z inna aktywna sesja
        while (signBlockLocations.containsKey(candidate)) {
            candidate.add(1, 0, 0);
        }
        return candidate;
    }

    public void displayPrompts(Player player, CaptchaSession session) {
        Settings s = plugin.getSettings();
        String code = session.getCode();
        String line = String.valueOf(session.getDisplayLine());

        // Kod wyswietlamy wylacznie na Actionbarze
        player.resetTitle();

        String chat = s.getCaptchaChatPrompt();
        if (chat != null && !chat.trim().isEmpty()) {
            player.sendMessage(plugin.getSettings().getPrefix() + chat.replace("{LINE}", line));
        }

        long remaining = session.getRemainingSeconds(s.getCaptchaTimeoutSeconds());
        String actionbar = s.getCaptchaActionbar()
                .replace("{CODE}", code)
                .replace("{TIME}", String.valueOf(remaining))
                .replace("{SECONDS}", String.valueOf(remaining))
                .replace("{LINE}", line);
        player.sendActionBar(actionbar);
    }

    public boolean verifySubmission(Player player, CaptchaSession session, String[] lines) {
        if (session == null || lines == null) return false;

        String expected = session.getCode();
        int target = session.getTargetLine();

        String enteredInTargetLine = (lines.length > target && lines[target] != null) ? lines[target].trim() : "";

        boolean matched = plugin.getSettings().isCaptchaCaseSensitive()
                ? enteredInTargetLine.equals(expected)
                : enteredInTargetLine.equalsIgnoreCase(expected);

        if (matched) {
            passCaptcha(player, session);
            return true;
        }

        // Jesli gracz zamknal tabliczke (ESC) lub klient wyslal pusty stan bez wpisania kodu:
        if (isSignInputEmpty(lines, target)) {
            player.sendMessage(plugin.getSettings().getPrefix() + "§eZamknięto tabliczkę bez wpisania kodu. §7(Kliknij LPM lub naciśnij Shift, aby otworzyć)");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isPending(session.getPlayerUuid())) {
                    reopenSignGui(player);
                }
            }, 10L);
            return false;
        }

        // Gracz wpisal bledny tekst: sprawdzamy czy przypadkowo wpisal kod w innej linijce
        boolean foundInOtherLine = false;
        for (int i = 0; i < lines.length; i++) {
            if (i == target || lines[i] == null) continue;
            String other = lines[i].trim();
            boolean otherMatched = plugin.getSettings().isCaptchaCaseSensitive()
                    ? other.equals(expected)
                    : other.equalsIgnoreCase(expected);
            if (otherMatched) {
                foundInOtherLine = true;
                break;
            }
        }

        failAttempt(player, session, foundInOtherLine);
        return false;
    }

    private boolean isSignInputEmpty(String[] lines, int targetLine) {
        if (lines == null) return true;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null) continue;
            String text = lines[i].trim();
            if (text.isEmpty()) continue;
            if (text.contains("TUTAJ KOD") || text.equals("------------")) {
                continue;
            }
            return false;
        }
        return true;
    }

    public void passCaptcha(Player player, CaptchaSession session) {
        UUID uuid = session.getPlayerUuid();
        restoreBlock(session);
        cancelSession(uuid, false);

        plugin.getTrustManager().addTrusted(uuid);
        plugin.getTrustManager().saveAsync();

        if (plugin.getVerificationManager() != null) {
            plugin.getVerificationManager().cleanup(uuid);
        }

        player.resetTitle();
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getCaptchaSuccess());

        // Jesli wlaczona jest weryfikacja ruchu (kamera + kroki po captchy):
        if (plugin.getSettings().isMovementCheckEnabled() && plugin.getVerificationManager() != null) {
            plugin.getVerificationManager().startVerification(player);
            return;
        }

        // Uruchomienie procesu logowania / rejestracji DOPIERO PO ZWERYFIKOWANIU CAPTCHY
        if (plugin.getSettings().isAuthEnabled() && plugin.getAuthManager() != null && !plugin.getAuthManager().isLoggedIn(uuid)) {
            AuthManager am = plugin.getAuthManager();
            AuthAccount account = am.getOrCreateAccount(player.getName());
            Settings s = plugin.getSettings();

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

    public void failAttempt(Player player, CaptchaSession session, boolean wrongLine) {
        restoreBlock(session);
        int remainingAttempts = session.decrementAttempts();

        if (remainingAttempts > 0) {
            // Nowy kod oraz ewentualnie nowa linijka na kolejna probe
            String newCode = generateCode();
            session.setCode(newCode);
            if ("RANDOM".equalsIgnoreCase(plugin.getSettings().getCaptchaRequiredLine())) {
                session.setTargetLine(determineTargetLine());
            }

            int line = session.getDisplayLine();
            String msg;
            if (wrongLine) {
                msg = plugin.getSettings().getCaptchaWrongLine()
                        .replace("{LINE}", String.valueOf(line))
                        .replace("{ATTEMPTS}", String.valueOf(remainingAttempts));
            } else {
                msg = plugin.getSettings().getCaptchaFailedAttempt()
                        .replace("{LINE}", String.valueOf(line))
                        .replace("{ATTEMPTS}", String.valueOf(remainingAttempts));
            }

            player.sendMessage(plugin.getSettings().getPrefix() + msg);
            displayPrompts(player, session);

            // Ponowne otwarcie tabliczki za 15 tickow
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isPending(session.getPlayerUuid())) {
                    openSignGui(player, session);
                }
            }, 15L);
        } else {
            UUID uuid = session.getPlayerUuid();
            cancelSession(uuid, false);
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
            plugin.getAttackManager().incrementBlockedBots(player.getName(), "Zly kod captchy / zla linijka (limit prob)", ip);
            player.kickPlayer(plugin.getSettings().getCaptchaKickFailed());
        }
    }

    public void restoreBlock(CaptchaSession session) {
        if (session == null || session.getSignLocation() == null) return;
        Location loc = session.getSignLocation();
        BlockData orig = session.getOriginalBlockData();

        signBlockLocations.remove(loc.getBlock().getLocation());
        signBlockLocations.remove(loc);
        if (orig != null && loc.getWorld() != null) {
            loc.getBlock().setBlockData(orig, false);
        } else if (loc.getWorld() != null) {
            loc.getBlock().setType(Material.AIR, false);
        }
        session.setSignLocation(null);
        session.setOriginalBlockData(null);
    }

    public void cancelSession(UUID uuid, boolean restoreBlock) {
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }

        CaptchaSession session = activeSessions.remove(uuid);
        if (session != null) {
            session.cancelTasks();
            if (restoreBlock) {
                restoreBlock(session);
            }
        }
    }

    public void cleanupAll() {
        for (UUID uuid : activeSessions.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
        for (CaptchaSession session : activeSessions.values()) {
            session.cancelTasks();
            restoreBlock(session);
        }
        activeSessions.clear();
        signBlockLocations.clear();
    }
}
