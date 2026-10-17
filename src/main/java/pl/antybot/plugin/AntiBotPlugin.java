package pl.antybot.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.antybot.plugin.auth.AuthListener;
import pl.antybot.plugin.auth.AuthManager;
import pl.antybot.plugin.captcha.CaptchaListener;
import pl.antybot.plugin.captcha.CaptchaManager;
import pl.antybot.plugin.command.AntiBotCommand;
import pl.antybot.plugin.command.ChangePasswordCommand;
import pl.antybot.plugin.command.LoginCommand;
import pl.antybot.plugin.command.PremiumCommand;
import pl.antybot.plugin.command.RegisterCommand;
import pl.antybot.plugin.config.Settings;
import pl.antybot.plugin.listener.PingListener;
import pl.antybot.plugin.listener.PlayerListener;
import pl.antybot.plugin.listener.PreLoginListener;
import pl.antybot.plugin.manager.AntiVpnManager;
import pl.antybot.plugin.manager.AttackManager;
import pl.antybot.plugin.manager.IpManager;
import pl.antybot.plugin.manager.PingManager;
import pl.antybot.plugin.manager.TrustManager;
import pl.antybot.plugin.manager.VerificationManager;
import pl.antybot.plugin.updater.UpdateChecker;

public class AntiBotPlugin extends JavaPlugin {

    private static AntiBotPlugin instance;

    private Settings settings;
    private AttackManager attackManager;
    private PingManager pingManager;
    private IpManager ipManager;
    private TrustManager trustManager;
    private VerificationManager verificationManager;
    private CaptchaManager captchaManager;
    private AuthManager authManager;
    private AntiVpnManager antiVpnManager;
    private UpdateChecker updateChecker;

    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        instance = this;

        // Inicjalizacja konfiguracji
        saveDefaultConfig();
        this.settings = new Settings();
        this.settings.load(getConfig());

        // Inicjalizacja menedzerow
        this.attackManager = new AttackManager(this);
        this.attackManager.startTicker();
        this.pingManager = new PingManager();
        this.ipManager = new IpManager();
        this.trustManager = new TrustManager(this);
        this.verificationManager = new VerificationManager(this);
        this.captchaManager = new CaptchaManager(this);
        this.authManager = new AuthManager(this);
        this.antiVpnManager = new AntiVpnManager(this);
        this.updateChecker = new UpdateChecker(this);
        this.updateChecker.start();

        // Ladowanie bazy zaufanych graczy oraz bazy kont
        this.trustManager.load();
        this.authManager.load();

        // Rejestracja listenerow
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PingListener(this), this);
        pm.registerEvents(new PreLoginListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new CaptchaListener(this), this);
        pm.registerEvents(new AuthListener(this), this);

        // Rejestracja komendy glownej
        AntiBotCommand command = new AntiBotCommand(this);
        PluginCommand cmd = getCommand("antibot");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        // Rejestracja komend autoryzacji
        PluginCommand loginCmd = getCommand("login");
        if (loginCmd != null) loginCmd.setExecutor(new LoginCommand(this));

        PluginCommand regCmd = getCommand("register");
        if (regCmd != null) regCmd.setExecutor(new RegisterCommand(this));

        PluginCommand cpCmd = getCommand("changepassword");
        if (cpCmd != null) cpCmd.setExecutor(new ChangePasswordCommand(this));

        PluginCommand premCmd = getCommand("premium");
        if (premCmd != null) premCmd.setExecutor(new PremiumCommand(this));

        // Zadanie okresowego czyszczenia pamieci podrecznej oraz zrzutu brudnych danych na dysk
        this.cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (pingManager != null) {
                pingManager.cleanExpired(settings.getMaxPingAgeSeconds());
            }
            if (attackManager != null) {
                attackManager.cleanExpiredReconnects();
            }
            if (ipManager != null) {
                ipManager.cleanExpiredBurst(settings.getIpBurstWindowSeconds());
            }
            if (antiVpnManager != null) {
                antiVpnManager.cleanExpiredCache();
            }
            if (authManager != null) {
                authManager.getMojangResolver().cleanExpiredCache();
            }
            if (trustManager != null && trustManager.isDirty()) {
                trustManager.save();
            }
            if (authManager != null && authManager.isDirty()) {
                authManager.save();
            }
        }, 600L, 600L); // co 30 sekund

        getLogger().info("==========================================");
        getLogger().info("  AntyBot v1.0.0 zostal pomyslnie wlaczony!");
        getLogger().info("  Wsparcie dla Minecraft 1.21.4");
        getLogger().info("  Sign GUI Captcha + Auth Premium Auto-Login");
        getLogger().info("  Zaufanych graczy w bazie: " + trustManager.getTrustedCount());
        getLogger().info("==========================================");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (authManager != null) {
            authManager.cleanupAll();
        }

        if (captchaManager != null) {
            captchaManager.cleanupAll();
        }

        if (verificationManager != null) {
            verificationManager.clearAll();
        }

        if (attackManager != null) {
            attackManager.stopAttackMode();
            attackManager.stopTicker();
        }

        if (trustManager != null) {
            trustManager.save();
        }

        if (antiVpnManager != null) {
            antiVpnManager.shutdown();
        }

        if (updateChecker != null) {
            updateChecker.stop();
        }

        getLogger().info("AntyBot v1.0.0 zostal wylaczony.");
        instance = null;
    }

    public void reloadPlugin() {
        reloadConfig();
        this.settings.load(getConfig());
        this.trustManager.load();
        this.authManager.load();
        if (this.antiVpnManager != null) {
            this.antiVpnManager.shutdown();
            this.antiVpnManager.initExecutionEngine();
        }
        if (this.updateChecker != null) {
            this.updateChecker.start();
        }
    }

    public static AntiBotPlugin getInstance() {
        return instance;
    }

    public Settings getSettings() {
        return settings;
    }

    public AttackManager getAttackManager() {
        return attackManager;
    }

    public PingManager getPingManager() {
        return pingManager;
    }

    public IpManager getIpManager() {
        return ipManager;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public VerificationManager getVerificationManager() {
        return verificationManager;
    }

    public CaptchaManager getCaptchaManager() {
        return captchaManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public AntiVpnManager getAntiVpnManager() {
        return antiVpnManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
