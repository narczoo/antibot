package pl.antybot.plugin.auth;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.config.Settings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class AuthManager {

    private final AntiBotPlugin plugin;
    private final MojangResolver mojangResolver = new MojangResolver();

    private final Map<String, AuthAccount> accounts = new ConcurrentHashMap<>();
    private final Set<UUID> loggedInPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> reminderTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();

    private final File accountsFile;
    private final ReentrantLock fileLock = new ReentrantLock();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public AuthManager(AntiBotPlugin plugin) {
        this.plugin = plugin;
        this.accountsFile = new File(plugin.getDataFolder(), "accounts.yml");
    }

    public void load() {
        fileLock.lock();
        try {
            accounts.clear();
            dirty.set(false);
            if (!accountsFile.exists()) {
                return;
            }

            FileConfiguration yaml = YamlConfiguration.loadConfiguration(accountsFile);
            ConfigurationSection sec = yaml.getConfigurationSection("accounts");
            if (sec == null) return;

            for (String userKey : sec.getKeys(false)) {
                ConfigurationSection accSec = sec.getConfigurationSection(userKey);
                if (accSec == null) continue;

                AuthAccount account = new AuthAccount(userKey);
                account.setPremium(accSec.getBoolean("premium", false));
                account.setMojangUuid(accSec.getString("mojang-uuid", null));
                account.setPasswordHash(accSec.getString("hash", null));
                account.setSalt(accSec.getString("salt", null));
                account.setRegisteredIp(accSec.getString("registered-ip", null));
                account.setLastIp(accSec.getString("last-ip", null));
                account.setLastLogin(accSec.getLong("last-login", 0L));
                account.setRegisteredAt(accSec.getLong("registered-at", 0L));

                accounts.put(userKey.toLowerCase(), account);
            }
        } finally {
            fileLock.unlock();
        }
    }

    public void saveAsync() {
        dirty.set(true);
        if (!plugin.isEnabled()) {
            save();
            return;
        }

        if (saveScheduled.compareAndSet(false, true)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    saveSyncInternal(false);
                } finally {
                    saveScheduled.set(false);
                }
            });
        }
    }

    public void save() {
        saveSyncInternal(true);
    }

    private void saveSyncInternal(boolean force) {
        if (!force && !dirty.getAndSet(false)) {
            return;
        }

        fileLock.lock();
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            FileConfiguration yaml = new YamlConfiguration();
            // Tworzymy migawkę wpisów, aby iteracja była w pełni bezpieczna
            for (Map.Entry<String, AuthAccount> entry : accounts.entrySet()) {
                String key = entry.getKey();
                AuthAccount acc = entry.getValue();

                String path = "accounts." + key + ".";
                yaml.set(path + "premium", acc.isPremium());
                if (acc.getMojangUuid() != null) yaml.set(path + "mojang-uuid", acc.getMojangUuid());
                if (acc.getPasswordHash() != null) yaml.set(path + "hash", acc.getPasswordHash());
                if (acc.getSalt() != null) yaml.set(path + "salt", acc.getSalt());
                if (acc.getRegisteredIp() != null) yaml.set(path + "registered-ip", acc.getRegisteredIp());
                if (acc.getLastIp() != null) yaml.set(path + "last-ip", acc.getLastIp());
                yaml.set(path + "last-login", acc.getLastLogin());
                yaml.set(path + "registered-at", acc.getRegisteredAt());
            }

            try {
                yaml.save(accountsFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Nie mozna zapisac bazy kont (accounts.yml): " + e.getMessage());
            }
        } finally {
            fileLock.unlock();
        }
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public AuthAccount getAccount(String username) {
        if (username == null) return null;
        return accounts.get(username.toLowerCase());
    }

    public AuthAccount getOrCreateAccount(String username) {
        if (username == null) return null;
        return accounts.computeIfAbsent(username.toLowerCase(), AuthAccount::new);
    }

    public boolean isRegistered(String username) {
        AuthAccount acc = getAccount(username);
        return acc != null && acc.isRegistered();
    }

    public boolean isLoggedIn(UUID uuid) {
        return uuid != null && loggedInPlayers.contains(uuid);
    }

    public void setLoggedOut(UUID uuid) {
        if (uuid != null) {
            loggedInPlayers.remove(uuid);
        }
    }

    public void startAuthSession(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        loggedInPlayers.remove(uuid);

        cleanupTasks(uuid);

        // Opcjonalny efekt oslepienia / spowolnienia na czas logowania
        if (plugin.getSettings().isAuthBlindness()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 120, 1, false, false));
        }

        boolean registered = isRegistered(player.getName());

        // Zadanie cyklicznego przypominania na Actionbarze i czacie (co 3 sekundy = 60 tickow)
        BukkitTask reminderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || isLoggedIn(uuid)) {
                    cancel();
                    return;
                }

                Settings s = plugin.getSettings();
                if (registered) {
                    player.sendActionBar(s.getAuthLoginPromptActionbar());
                    player.sendMessage(s.getPrefix() + s.getAuthLoginPromptChat());
                } else {
                    player.sendActionBar(s.getAuthRegisterPromptActionbar());
                    player.sendMessage(s.getPrefix() + s.getAuthRegisterPromptChat());
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);
        reminderTasks.put(uuid, reminderTask);

        // Zadanie limitu czasu na zalogowanie (Timeout)
        int timeoutSeconds = plugin.getSettings().getAuthTimeoutSeconds();
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !isLoggedIn(uuid)) {
                cleanupTasks(uuid);
                player.kickPlayer(plugin.getSettings().getPrefix() + plugin.getSettings().getAuthTimeoutKick());
                String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
                plugin.getAttackManager().incrementBlockedBots(player.getName(), "Przekroczono czas autoryzacji", ip);
            }
        }, timeoutSeconds * 20L);
        timeoutTasks.put(uuid, timeoutTask);
    }

    public void autoLoginPremium(Player player, AuthAccount account) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        account.setLastIp(ip);
        account.setLastLogin(System.currentTimeMillis());
        account.setPremium(true);
        saveAsync();

        finishAuth(player);

        plugin.getLogger().info("Gracz " + player.getName() + " zostal automatycznie zalogowany z konta PREMIUM (" + ip + ").");

        // Natychmiastowy Actionbar na ticku 0:
        String instantActionbar = plugin.getSettings().getAuthPremiumActionbar();
        if (instantActionbar != null && !instantActionbar.isEmpty()) {
            player.sendActionBar(instantActionbar);
        }

        // Wysylamy pelne powiadomienie ekranowe i na czacie po krotkim opoznieniu (7 tickow),
        // aby upewnic sie, ze klient gracza w pelni zaladowal swiat i HUD po polaczeniu
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Settings s = plugin.getSettings();

                // Powiadomienie na czacie:
                player.sendMessage(s.getPrefix() + s.getAuthPremiumWelcome());

                String title = s.getAuthPremiumTitle();
                String subtitle = s.getAuthPremiumSubtitle();
                if (title != null && !title.isEmpty()) {
                    player.sendTitle(title, subtitle, 10, 60, 20);
                }

                String actionbar = s.getAuthPremiumActionbar();
                if (actionbar != null && !actionbar.isEmpty()) {
                    player.sendActionBar(actionbar);
                }

                try {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                } catch (Throwable ignored) {
                }
            }
        }, 7L);
    }

    public boolean register(Player player, String password) {
        if (player == null || password == null) return false;
        String name = player.getName();
        AuthAccount account = getOrCreateAccount(name);

        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";

        account.setSalt(salt);
        account.setPasswordHash(hash);
        account.setRegisteredIp(ip);
        account.setLastIp(ip);
        account.setRegisteredAt(System.currentTimeMillis());
        account.setLastLogin(System.currentTimeMillis());

        saveAsync();
        finishAuth(player);

        player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getAuthRegisterSuccess());
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } catch (Throwable ignored) {
        }
        return true;
    }

    public boolean login(Player player, String password) {
        if (player == null || password == null) return false;
        AuthAccount account = getAccount(player.getName());
        if (account == null || !account.isRegistered()) return false;

        String calculatedHash = hashPassword(password, account.getSalt());
        if (!calculatedHash.equals(account.getPasswordHash())) {
            // Bledne haslo
            int currentFails = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
            int remaining = Math.max(0, plugin.getSettings().getAuthMaxFailedAttempts() - currentFails);

            if (remaining <= 0) {
                cleanupTasks(player.getUniqueId());
                player.kickPlayer(plugin.getSettings().getPrefix() + plugin.getSettings().getAuthMaxAttemptsKick());
                String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
                plugin.getAttackManager().incrementBlockedBots(player.getName(), "Zbyt wiele błędnych haseł", ip);
            } else {
                String msg = plugin.getSettings().getAuthWrongPassword().replace("{ATTEMPTS}", String.valueOf(remaining));
                player.sendMessage(plugin.getSettings().getPrefix() + msg);
            }
            return false;
        }

        // Poprawne logowanie
        failedAttempts.remove(player.getUniqueId());
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        account.setLastIp(ip);
        account.setLastLogin(System.currentTimeMillis());
        saveAsync();

        finishAuth(player);
        player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getAuthLoginSuccess());
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } catch (Throwable ignored) {
        }
        return true;
    }

    public boolean changePassword(Player player, String oldPassword, String newPassword) {
        if (player == null || oldPassword == null || newPassword == null) return false;
        AuthAccount account = getAccount(player.getName());
        if (account == null || account.getPasswordHash() == null) return false;

        String oldHash = hashPassword(oldPassword, account.getSalt());
        if (!oldHash.equals(account.getPasswordHash())) {
            return false;
        }

        String newSalt = generateSalt();
        account.setSalt(newSalt);
        account.setPasswordHash(hashPassword(newPassword, newSalt));
        saveAsync();
        return true;
    }

    public void setPassword(String username, String newPassword) {
        AuthAccount account = getOrCreateAccount(username);
        String newSalt = generateSalt();
        account.setSalt(newSalt);
        account.setPasswordHash(hashPassword(newPassword, newSalt));
        account.setPremium(false);
        saveAsync();
    }

    public void unregister(String username) {
        if (username == null) return;
        accounts.remove(username.toLowerCase());
        saveAsync();
    }

    public void finishAuth(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        loggedInPlayers.add(uuid);
        cleanupTasks(uuid);

        if (plugin.getCaptchaManager() != null) {
            plugin.getCaptchaManager().cancelSession(uuid, true);
        }
        if (plugin.getVerificationManager() != null) {
            plugin.getVerificationManager().cleanup(uuid);
        }

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.resetTitle();
        player.sendActionBar(" ");

        plugin.getTrustManager().addTrusted(uuid);
        plugin.getTrustManager().saveAsync();
    }

    public void cleanupTasks(UUID uuid) {
        if (uuid == null) return;
        BukkitTask t1 = timeoutTasks.remove(uuid);
        if (t1 != null) t1.cancel();

        BukkitTask t2 = reminderTasks.remove(uuid);
        if (t2 != null) t2.cancel();
    }

    public void cleanupAll() {
        for (BukkitTask t : timeoutTasks.values()) if (t != null) t.cancel();
        for (BukkitTask t : reminderTasks.values()) if (t != null) t.cancel();
        timeoutTasks.clear();
        reminderTasks.clear();
        loggedInPlayers.clear();
        failedAttempts.clear();
        save();
    }

    public static String generateSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Blad haszowania hasla: " + e.getMessage(), e);
        }
    }

    public static boolean isPasswordComplex(String password) {
        if (password == null) return false;
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true;
            }
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    public MojangResolver getMojangResolver() {
        return mojangResolver;
    }
}
