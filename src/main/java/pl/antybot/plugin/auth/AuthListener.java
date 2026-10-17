package pl.antybot.plugin.auth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.config.Settings;

import java.util.UUID;

public class AuthListener implements Listener {

    private final AntiBotPlugin plugin;

    public AuthListener(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        String name = event.getName();
        if (name == null) return;

        AuthManager am = plugin.getAuthManager();
        AuthAccount account = am.getOrCreateAccount(name);

        // 1. Serwer w trybie online (Bukkit.getOnlineMode()) lub autoryzowane konto z UUID v4
        if (Bukkit.getOnlineMode() || (event.getUniqueId() != null && event.getUniqueId().version() == 4)) {
            account.setPremium(true);
            am.saveAsync();
            plugin.getLogger().info("[Auth] Gracz " + name + " zweryfikowany jako PREMIUM (Online-Mode / UUID v4).");
            return;
        }

        // 2. Jeśli konto jest już znane (jest już PREMIUM lub zarejestrowane jako NON-PREMIUM) - pomijamy zapytanie HTTP
        if (account.isPremium() || account.isRegistered()) {
            return;
        }

        // 3. Automatyczne sprawdzanie bazy Mojang API (tylko dla nowych/nieznanych kont)
        if (plugin.getSettings().isAuthPremiumAutoDetect()) {
            MojangResolver.MojangProfile profile = am.getMojangResolver().resolve(name);
            if (profile.isPremium()) {
                account.setPremium(true);
                account.setMojangUuid(profile.getMojangUuid());
                am.saveAsync();
                plugin.getLogger().info("[Auth] Gracz " + name + " zweryfikowany jako PREMIUM przez Mojang API (UUID: " + profile.getMojangUuid() + ").");
            } else {
                plugin.getLogger().info("[Auth] Gracz " + name + " zweryfikowany jako NON-PREMIUM (brak konta w Mojang API).");
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        AuthManager am = plugin.getAuthManager();
        Settings s = plugin.getSettings();

        AuthAccount account = am.getOrCreateAccount(name);
        if (Bukkit.getOnlineMode() || (uuid != null && uuid.version() == 4)) {
            account.setPremium(true);
        }

        // 1. JEŚLI CAPTCHA JEST WYMAGANA LUB AKTYWNA DLA TEGO GRACZA (ZARÓWNO DLA PREMIUM JAK I NON-PREMIUM)
        // Logowanie i auto-login Premium następuje DOPIERO PO ZWERYFIKOWANIU CAPTCHY!
        boolean needsCaptcha = plugin.getCaptchaManager() != null &&
                (plugin.getCaptchaManager().isPending(uuid) || plugin.getCaptchaManager().shouldRequireCaptcha(player));
        if (needsCaptcha) {
            return;
        }

        // 2. JEŚLI TRWA WERYFIKACJA RUCHU (KAMERA + KROKI)
        if (plugin.getVerificationManager() != null && plugin.getVerificationManager().isVerifying(uuid)) {
            return;
        }

        // 3. KONTO PREMIUM - DLA GRACZY ZAUFANYCH / PO ROZWIĄZANIU CAPTCHY I WERYFIKACJI - OD RAZU LOGUJEMY Z KONTA PREMIUM!
        if (account.isPremium()) {
            am.autoLoginPremium(player, account);
            return;
        }

        // 4. SPRAWDZENIE AKTYWNEJ SESJI IP DLA GRACZY NON-PREMIUM
        String currentIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        boolean hasValidSession = false;
        if (s.isAuthSessionEnabled() && account.isRegistered() && currentIp.equals(account.getLastIp())) {
            long maxSessionMillis = s.getAuthSessionTimeoutMinutes() * 60L * 1000L;
            if (System.currentTimeMillis() - account.getLastLogin() <= maxSessionMillis) {
                hasValidSession = true;
            }
        }

        // 5. PRZYWRACANIE ZAPISANEJ SESJI IP (NON-PREMIUM)
        if (hasValidSession) {
            if (plugin.getCaptchaManager() != null) {
                plugin.getCaptchaManager().cancelSession(uuid, true);
            }
            am.finishAuth(player);
            account.setLastLogin(System.currentTimeMillis());
            account.setLastIp(currentIp);
            am.saveAsync();
            player.sendMessage(s.getPrefix() + s.getAuthSessionRestored());
            return;
        }

        // 6. GDY TRZEBA SIĘ ZALOGOWAĆ (BRAK AKTYWNEJ SESJI / PIERWSZE WEJŚCIE NON-PREMIUM)
        am.startAuthSession(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.getAuthManager().cleanupTasks(uuid);
        plugin.getAuthManager().setLoggedOut(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Podczas weryfikacji ruchu zezwalamy na przemieszczanie się (kroki liczy VerificationManager)
        if (plugin.getVerificationManager() != null && plugin.getVerificationManager().isVerifying(uuid)) {
            return;
        }

        if (!plugin.getAuthManager().isLoggedIn(uuid)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            // Zezwalamy na obrót kamery, blokujemy ruch pozycyjny
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                event.setTo(back);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getCaptchaManager() != null && plugin.getCaptchaManager().isPending(uuid)) {
            // W trakcie weryfikacji captchy czat obsluguje CaptchaListener
            return;
        }

        if (plugin.getVerificationManager() != null && plugin.getVerificationManager().isVerifying(uuid)) {
            if (plugin.getSettings().isBlockChatBeforeVerification()) {
                event.setCancelled(true);
                player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getChatBlocked());
            }
            return;
        }

        if (!plugin.getAuthManager().isLoggedIn(uuid)) {
            event.setCancelled(true);
            boolean reg = plugin.getAuthManager().isRegistered(player.getName());
            String prompt = reg ? plugin.getSettings().getAuthLoginPromptChat() : plugin.getSettings().getAuthRegisterPromptChat();
            player.sendMessage(plugin.getSettings().getPrefix() + prompt);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getCaptchaManager() != null && plugin.getCaptchaManager().isPending(uuid)) {
            // W trakcie weryfikacji captchy komendy sa calkowicie zablokowane
            return;
        }

        if (plugin.getVerificationManager() != null && plugin.getVerificationManager().isVerifying(uuid)) {
            if (plugin.getSettings().isBlockCommandsBeforeVerification()) {
                event.setCancelled(true);
                player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getCommandBlocked());
            }
            return;
        }

        if (!plugin.getAuthManager().isLoggedIn(uuid)) {
            String message = event.getMessage().trim().toLowerCase();
            String root = message.startsWith("/") ? message.substring(1) : message;
            int spaceIdx = root.indexOf(' ');
            String cmdName = (spaceIdx != -1 ? root.substring(0, spaceIdx) : root);
            if (cmdName.contains(":")) {
                cmdName = cmdName.substring(cmdName.indexOf(':') + 1);
            }

            if (cmdName.equals("login") || cmdName.equals("l") || cmdName.equals("zaloguj") ||
                cmdName.equals("register") || cmdName.equals("reg") || cmdName.equals("zarejestruj")) {
                // Dozwolone komendy autoryzacji
                return;
            }

            event.setCancelled(true);
            boolean reg = plugin.getAuthManager().isRegistered(player.getName());
            String prompt = reg ? plugin.getSettings().getAuthLoginPromptChat() : plugin.getSettings().getAuthRegisterPromptChat();
            player.sendMessage(plugin.getSettings().getPrefix() + prompt);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (!plugin.getAuthManager().isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (!plugin.getAuthManager().isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (!plugin.getAuthManager().isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (event.getEntity() instanceof Player p && !plugin.getAuthManager().isLoggedIn(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (event.getDamager() instanceof Player p && !plugin.getAuthManager().isLoggedIn(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (event.getPlayer() instanceof Player p && !plugin.getAuthManager().isLoggedIn(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (event.getWhoClicked() instanceof Player p && !plugin.getAuthManager().isLoggedIn(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!plugin.getSettings().isAuthEnabled()) return;
        if (!plugin.getAuthManager().isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
