package pl.antybot.plugin.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Settings {

    // Prefix
    private String prefix;

    // Attack Mode
    private boolean attackModeEnabled;
    private int triggerJoins;
    private int triggerSeconds;
    private int attackDurationSeconds;
    private boolean blockNewPlayers;
    private boolean requireReconnect;
    private int reconnectDelaySeconds;

    // Ping Check
    private boolean pingCheckEnabled;
    private int maxPingAgeSeconds;

    // IP Limit
    private boolean ipLimitEnabled;
    private int maxConnectionsPerIp;
    private boolean sharedIpEnabled;
    private int sharedIpExtraConnections;
    private boolean globalThrottleEnabled;
    private int globalMaxJoinsPerSecond;
    private boolean ipBurstProtectionEnabled;
    private int ipBurstMaxConnections;
    private int ipBurstWindowSeconds;
    private boolean tempBlockEnabled;
    private int tempBlockDurationSeconds;
    private int maxConcurrentVerifications;
    private boolean singleFlightHandshake;
    private String ipBurstKick;
    private String globalThrottleKick;
    private String tempBlockKick;
    private String verificationQueueFullKick;
    private String duplicateHandshakeKick;

    // Anti-VPN
    private boolean antiVpnEnabled;
    private String antiVpnTriggerMode;
    private String antiVpnProvider;
    private String antiVpnApiKey;
    private boolean antiVpnBlockHosting;
    private boolean antiVpnBypassTrusted;
    private int antiVpnCacheTimeHours;
    private int antiVpnMaxWorkerThreads;
    private int antiVpnMaxPendingTasks;
    private int antiVpnMaxConcurrentRequests;
    private int antiVpnConnectTimeoutMs;
    private int antiVpnRequestTimeoutMs;
    private int antiVpnGlobalChecksPerMinute;
    private int antiVpnIpCheckCooldownSeconds;
    private String antiVpnOverloadPolicy;
    private String antiVpnFailurePolicy;
    private List<String> antiVpnWhitelistIps = new ArrayList<>();
    private Set<String> antiVpnWhitelistPlayers = new HashSet<>();
    private String antiVpnKick;
    private String antiVpnOverloadKick;

    // Nickname Filter
    private boolean nicknameFilterEnabled;
    private int minNickLength;
    private int maxNickLength;
    private List<Pattern> blockedNickPatterns = new ArrayList<>();

    // Captcha
    private boolean captchaEnabled;
    private String captchaTriggerMode; // "ALWAYS" or "ATTACK_ONLY"
    private int captchaCodeLength;
    private String captchaCharacters;
    private int captchaTimeoutSeconds;
    private int captchaMaxAttempts;
    private boolean captchaCaseSensitive;
    private boolean captchaAllowChatFallback;
    private int captchaOpenDelayTicks;
    private String captchaRequiredLine; // "RANDOM", "1", "2", "3", "4"
    private boolean captchaBlindness;

    // Auth
    private boolean authEnabled;
    private boolean authPremiumAutoDetect;
    private int authTimeoutSeconds;
    private int authMaxFailedAttempts;
    private int authMinPasswordLength;
    private int authMaxPasswordLength;
    private boolean authRequireComplexPassword;
    private boolean authBlindness;
    private boolean authSessionEnabled;
    private int authSessionTimeoutMinutes;

    // Movement Check
    private boolean movementCheckEnabled;
    private int movementTimeoutSeconds;
    private int requiredCameraMoves;
    private int requiredWalkSteps;
    private boolean blockChatBeforeVerification;
    private boolean blockCommandsBeforeVerification;

    // Notifications
    private boolean notifyOnAttackState;
    private boolean notifyOnBotBlock;
    private boolean showActionbar;

    // Update Checker
    private boolean updateCheckerEnabled;
    private String updateCheckerGithubRepo;
    private boolean updateCheckerNotifyAdmins;
    private int updateCheckerIntervalHours;

    // Messages
    private String attackModeKick;
    private String pingCheckKick;
    private String ipLimitKick;
    private String invalidNicknameKick;
    private String reconnectWaitKick;
    private String captchaTitle;
    private String captchaSubtitle;
    private String captchaChatPrompt;
    private String captchaActionbar;
    private String captchaSuccess;
    private String captchaFailedAttempt;
    private String captchaWrongLine;
    private String captchaKickFailed;
    private String captchaKickTimeout;
    private String captchaBlockedAction;

    // Auth Messages
    private String authLoginPromptActionbar;
    private String authLoginPromptChat;
    private String authRegisterPromptActionbar;
    private String authRegisterPromptChat;
    private String authLoginSuccess;
    private String authRegisterSuccess;
    private String authWrongPassword;
    private String authPasswordsNotMatch;
    private String authPasswordNotComplex;
    private String authPremiumWelcome;
    private String authPremiumActionbar;
    private String authPremiumTitle;
    private String authPremiumSubtitle;
    private String authSessionRestored;
    private String authTimeoutKick;
    private String authMaxAttemptsKick;

    private String movementPrompt;
    private String movementVerified;
    private String movementCameraActionbar;
    private String movementWalkActionbar;
    private String movementVerifiedActionbar;
    private String chatBlocked;
    private String commandBlocked;
    private String adminAttackStarted;
    private String adminAttackStopped;
    private String adminBotBlocked;

    public void load(FileConfiguration config) {
        this.prefix = color(config.getString("prefix", "&8[&c&lAntyBot&8]&r "));

        // Attack Mode
        this.attackModeEnabled = config.getBoolean("attack-mode.enabled", true);
        this.triggerJoins = config.getInt("attack-mode.trigger-joins", 4);
        this.triggerSeconds = config.getInt("attack-mode.trigger-seconds", 3);
        this.attackDurationSeconds = config.getInt("attack-mode.duration-seconds", 60);
        this.blockNewPlayers = config.getBoolean("attack-mode.block-new-players", true);
        this.requireReconnect = config.getBoolean("attack-mode.require-reconnect", true);
        this.reconnectDelaySeconds = config.getInt("attack-mode.reconnect-delay-seconds", 4);

        // Ping Check
        this.pingCheckEnabled = config.getBoolean("ping-check.enabled", true);
        this.maxPingAgeSeconds = config.getInt("ping-check.max-ping-age-seconds", 900);

        // IP Limit
        this.ipLimitEnabled = config.getBoolean("ip-limit.enabled", true);
        this.maxConnectionsPerIp = config.getInt("ip-limit.max-connections-per-ip", 3);
        this.sharedIpEnabled = config.getBoolean("ip-limit.shared-ip.enabled", true);
        this.sharedIpExtraConnections = config.getInt("ip-limit.shared-ip.extra-connections", 2);
        this.globalThrottleEnabled = config.getBoolean("ip-limit.global-throttle.enabled", true);
        this.globalMaxJoinsPerSecond = config.getInt("ip-limit.global-throttle.max-joins-per-second", 15);
        this.ipBurstProtectionEnabled = config.getBoolean("ip-limit.burst-protection.enabled", true);
        this.ipBurstMaxConnections = config.getInt("ip-limit.burst-protection.max-connections", 5);
        this.ipBurstWindowSeconds = config.getInt("ip-limit.burst-protection.window-seconds", 3);
        this.tempBlockEnabled = config.getBoolean("ip-limit.temp-block.enabled", true);
        this.tempBlockDurationSeconds = config.getInt("ip-limit.temp-block.duration-seconds", 15);
        this.maxConcurrentVerifications = config.getInt("ip-limit.verification-queue.max-concurrent-verifications", 25);
        this.singleFlightHandshake = config.getBoolean("ip-limit.single-flight.enabled", true);

        // Anti-VPN
        this.antiVpnEnabled = config.getBoolean("anti-vpn.enabled", true);
        this.antiVpnTriggerMode = config.getString("anti-vpn.trigger-mode", "ALWAYS").toUpperCase();
        this.antiVpnProvider = config.getString("anti-vpn.provider", "AUTO").toUpperCase();
        this.antiVpnApiKey = config.getString("anti-vpn.api-key", "");
        this.antiVpnBlockHosting = config.getBoolean("anti-vpn.block-hosting", true);
        this.antiVpnBypassTrusted = config.getBoolean("anti-vpn.bypass-trusted", true);
        this.antiVpnCacheTimeHours = Math.max(1, config.getInt("anti-vpn.cache-time-hours", 24));
        this.antiVpnMaxWorkerThreads = Math.max(1, config.getInt("anti-vpn.max-worker-threads", 4));
        this.antiVpnMaxPendingTasks = Math.max(10, config.getInt("anti-vpn.max-pending-tasks", 100));
        this.antiVpnMaxConcurrentRequests = Math.max(1, config.getInt("anti-vpn.max-concurrent-requests", 5));
        this.antiVpnConnectTimeoutMs = Math.max(500, config.getInt("anti-vpn.connect-timeout-ms", 2000));
        this.antiVpnRequestTimeoutMs = Math.max(500, config.getInt("anti-vpn.request-timeout-ms", 2500));
        this.antiVpnGlobalChecksPerMinute = Math.max(0, config.getInt("anti-vpn.global-checks-per-minute", 60));
        this.antiVpnIpCheckCooldownSeconds = Math.max(1, config.getInt("anti-vpn.ip-check-cooldown-seconds", 10));
        this.antiVpnOverloadPolicy = config.getString("anti-vpn.overload-policy", "FAIL_OPEN").toUpperCase();
        this.antiVpnFailurePolicy = config.getString("anti-vpn.failure-policy", "FAIL_OPEN").toUpperCase();
        this.antiVpnWhitelistIps = config.getStringList("anti-vpn.whitelist-ips");
        this.antiVpnWhitelistPlayers = new HashSet<>();
        for (String p : config.getStringList("anti-vpn.whitelist-players")) {
            this.antiVpnWhitelistPlayers.add(p.toLowerCase());
        }

        // Nickname Filter
        this.nicknameFilterEnabled = config.getBoolean("nickname-filter.enabled", true);
        this.minNickLength = config.getInt("nickname-filter.min-length", 3);
        this.maxNickLength = config.getInt("nickname-filter.max-length", 16);
        this.blockedNickPatterns = new ArrayList<>();
        List<String> patterns = config.getStringList("nickname-filter.blocked-patterns");
        for (String patternStr : patterns) {
            try {
                this.blockedNickPatterns.add(Pattern.compile(patternStr));
            } catch (Exception ignored) {
            }
        }

        // Captcha
        this.captchaEnabled = config.getBoolean("captcha.enabled", true);
        this.captchaTriggerMode = config.getString("captcha.trigger-mode", "ALWAYS").toUpperCase();
        this.captchaCodeLength = config.getInt("captcha.code-length", 4);
        this.captchaCharacters = config.getString("captcha.characters", "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz");
        this.captchaTimeoutSeconds = config.getInt("captcha.timeout-seconds", 30);
        this.captchaMaxAttempts = config.getInt("captcha.max-attempts", 3);
        this.captchaCaseSensitive = config.getBoolean("captcha.case-sensitive", false);
        this.captchaAllowChatFallback = config.getBoolean("captcha.allow-chat-fallback", true);
        this.captchaOpenDelayTicks = config.getInt("captcha.open-delay-ticks", 5);
        this.captchaRequiredLine = config.getString("captcha.required-line", "RANDOM").toUpperCase();
        this.captchaBlindness = config.getBoolean("captcha.blindness", true);

        // Auth
        this.authEnabled = config.getBoolean("auth.enabled", true);
        this.authPremiumAutoDetect = config.getBoolean("auth.premium-auto-detect", true);
        this.authTimeoutSeconds = config.getInt("auth.timeout-seconds", 60);
        this.authMaxFailedAttempts = config.getInt("auth.max-failed-attempts", 5);
        this.authMinPasswordLength = config.getInt("auth.min-password-length", 4);
        this.authMaxPasswordLength = config.getInt("auth.max-password-length", 32);
        this.authRequireComplexPassword = config.getBoolean("auth.require-complex-password", true);
        this.authBlindness = config.getBoolean("auth.blindness", true);
        this.authSessionEnabled = config.getBoolean("auth.session.enabled", true);
        this.authSessionTimeoutMinutes = config.getInt("auth.session.timeout-minutes", 60);

        // Movement Check
        this.movementCheckEnabled = config.getBoolean("movement-check.enabled", true);
        this.movementTimeoutSeconds = config.getInt("movement-check.timeout-seconds", 45);
        this.requiredCameraMoves = Math.max(1, config.getInt("movement-check.required-camera-moves", 5));
        this.requiredWalkSteps = Math.max(1, config.getInt("movement-check.required-walk-steps", 5));
        this.blockChatBeforeVerification = config.getBoolean("movement-check.block-chat", true);
        this.blockCommandsBeforeVerification = config.getBoolean("movement-check.block-commands", true);

        // Notifications
        this.notifyOnAttackState = config.getBoolean("notifications.notify-on-attack-state", true);
        this.notifyOnBotBlock = config.getBoolean("notifications.notify-on-bot-block", true);
        this.showActionbar = config.getBoolean("notifications.show-actionbar", true);

        // Update Checker
        this.updateCheckerEnabled = config.getBoolean("update-checker.enabled", true);
        this.updateCheckerGithubRepo = config.getString("update-checker.github-repo", "Narczo12/api");
        this.updateCheckerNotifyAdmins = config.getBoolean("update-checker.notify-admins-on-join", true);
        this.updateCheckerIntervalHours = Math.max(0, config.getInt("update-checker.check-interval-hours", 6));

        // Messages
        this.attackModeKick = color(config.getString("messages.attack-mode-kick", "&cWykryto atak botow!"));
        this.pingCheckKick = color(config.getString("messages.ping-check-kick", "&cMusisz odswiezyc liste serwerow!"));
        this.ipLimitKick = color(config.getString("messages.ip-limit-kick", "&cPrzekroczono limit kont z tego IP!"));
        this.ipBurstKick = color(config.getString("messages.ip-burst-kick", "&c&lZBYT CZĘSTE POŁĄCZENIA\n\n&7Wykryto zbyt wiele prób połączeń z Twojego adresu IP.\n&eOdczekaj chwilę przed kolejną próbą!"));
        this.globalThrottleKick = color(config.getString("messages.global-throttle-kick", "&c&lSERWER JEST PRZECIĄŻONY\n\n&7Wykryto zbyt dużą liczbę nowych połączeń jednocześnie.\n&eOdczekaj chwilę i połącz się ponownie."));
        this.tempBlockKick = color(config.getString("messages.temp-block-kick", "&c&lTYMCZASOWA BLOKADA RATE-LIMIT\n\n&7Wykryto podejrzany spam połączeń z Twojego adresu IP.\n&eOdczekaj &a{SECONDS}s&e przed kolejną próbą."));
        this.verificationQueueFullKick = color(config.getString("messages.verification-queue-full-kick", "&c&lKOLEJKA WERYFIKACJI JEST PEŁNA\n\n&7Trwa weryfikacja maksymalnej liczby graczy ({LIMIT}).\n&eSpróbuj połączyć się ponownie za kilka sekund."));
        this.duplicateHandshakeKick = color(config.getString("messages.duplicate-handshake-kick", "&c&lPOŁĄCZENIE W TOKU\n\n&7Trwa już przetwarzanie Twojego połączenia z serwerem.\n&eOdczekaj chwilę przed kolejną próbą."));
        this.antiVpnKick = color(config.getString("messages.anti-vpn-kick", "&c&lWYKRYTO VPN / PROXY\n\n&7Połączenia z sieci VPN, serwerów Proxy oraz centrów danych są zablokowane na tym serwerze.\n&eWyłącz VPN/Proxy i spróbuj połączyć się ponownie."));
        this.antiVpnOverloadKick = color(config.getString("messages.anti-vpn-overload-kick", "&c&lSERWER JEST PRZECIĄŻONY\n\n&7Trwa weryfikacja zbyt wielu połączeń jednocześnie.\n&eSpróbuj połączyć się ponownie za chwilę."));
        this.invalidNicknameKick = color(config.getString("messages.invalid-nickname", "&cNieprawidlowy nick!"));
        this.reconnectWaitKick = color(config.getString("messages.reconnect-wait", "&ePolacz sie ponownie za {SECONDS}s!"));
        this.captchaTitle = color(config.getString("messages.captcha-title", "&e&lKOD: &a&l{CODE}"));
        this.captchaSubtitle = color(config.getString("messages.captcha-subtitle", "&7Wpisz kod w &e&lLINIJCE {LINE}&7 na tabliczce!"));
        this.captchaChatPrompt = color(config.getString("messages.captcha-chat-prompt", "&7Spójrz na pasek &eActionbar &7i przepisz kod w &e&lLINIJCE {LINE}&7!"));
        this.captchaActionbar = color(config.getString("messages.captcha-actionbar", "&e&lKOD: &a&l{CODE} &8| &7Pozostały czas: &c&l{TIME}s"));
        this.captchaSuccess = color(config.getString("messages.captcha-success", "&a&lSUKCES! &7Kod poprawny. Zostales pomyslnie zweryfikowany!"));
        this.captchaFailedAttempt = color(config.getString("messages.captcha-failed-attempt", "&c&lBLEDNY KOD! &7Pozostalo prob: &e{ATTEMPTS}&7. Otwieranie tabliczki ponownie..."));
        this.captchaWrongLine = color(config.getString("messages.captcha-wrong-line", "&c&lZLA LINIJKA! &7Kod musisz wpisac w &e&lLINIJCE {LINE}&7! Pozostalo prob: &e{ATTEMPTS}&7."));
        this.captchaKickFailed = color(config.getString("messages.captcha-kick-failed", "&c&lWERYFIKACJA NIEUDANA\n\n&7Wprowadzono bledny kod captchy zbyt wiele razy!"));
        this.captchaKickTimeout = color(config.getString("messages.captcha-kick-timeout", "&c&lUPLYNAL CZAS\n\n&7Nie wpisano kodu captchy w wymaganym czasie ({SECONDS}s)!"));
        this.captchaBlockedAction = color(config.getString("messages.captcha-blocked-action", "&cMusisz najpierw wpisac kod captchy na tabliczce!"));

        // Auth Messages
        this.authLoginPromptActionbar = color(config.getString("messages.auth-login-prompt-actionbar", "&eZaloguj sie: &a/login <haslo>"));
        this.authLoginPromptChat = color(config.getString("messages.auth-login-prompt-chat", "&eZaloguj sie uzywajac: &a/login <haslo>"));
        this.authRegisterPromptActionbar = color(config.getString("messages.auth-register-prompt-actionbar", "&cZarejestruj sie: &e/register <haslo> <powtorzHaslo>"));
        this.authRegisterPromptChat = color(config.getString("messages.auth-register-prompt-chat", "&cZarejestruj sie uzywajac: &e/register <haslo> <powtorzHaslo>"));
        this.authLoginSuccess = color(config.getString("messages.auth-login-success", "&aPomyslnie zalogowano!"));
        this.authRegisterSuccess = color(config.getString("messages.auth-register-success", "&aPomyslnie zarejestrowano!"));
        this.authWrongPassword = color(config.getString("messages.auth-wrong-password", "&c&lBLEDNE HASLO! &7Pozostalo prob: &e{ATTEMPTS}&7."));
        this.authPasswordsNotMatch = color(config.getString("messages.auth-passwords-not-match", "&cHasla nie sa identyczne!"));
        this.authPasswordNotComplex = color(config.getString("messages.auth-password-not-complex", "&c&lBŁĄD! &7Hasło musi zawierać: &edużą literę&7, &emałą literę&7, &ecyfrę &7oraz &eznak specjalny&7!"));
        this.authPremiumWelcome = color(config.getString("messages.auth-premium-welcome", "&aWykryto oficjalne konto &6&lPREMIUM&a! Zostales zalogowany automatycznie bez hasla."));
        this.authPremiumActionbar = color(config.getString("messages.auth-premium-actionbar", "&a✔ Zalogowano z konta &6&lPREMIUM"));
        this.authPremiumTitle = color(config.getString("messages.auth-premium-title", "&a&lZALOGOWANO"));
        this.authPremiumSubtitle = color(config.getString("messages.auth-premium-subtitle", "&7Zalogowano z konta &6&lPREMIUM"));
        this.authSessionRestored = color(config.getString("messages.auth-session-restored", "&aWitaj ponownie! Przywrocono aktywna sesje."));
        this.authTimeoutKick = color(config.getString("messages.auth-timeout-kick", "&cPrzekroczono limit czasu na zalogowanie!"));
        this.authMaxAttemptsKick = color(config.getString("messages.auth-max-attempts-kick", "&cPrzekroczono limit blednych hasel!"));

        this.movementPrompt = color(config.getString("messages.movement-prompt", "&e&lWERYFIKACJA: &7Obróć kamerę &e{CAMERA_TARGET}x&7, a następnie zrób &e{WALK_TARGET}&7 kroków!"));
        this.movementVerified = color(config.getString("messages.movement-verified", "&a&lSUKCES! &7Weryfikacja ruchu zakończona pomyślnie. Miłej gry na serwerze!"));
        this.movementCameraActionbar = color(config.getString("messages.movement-camera-actionbar", "&e&lWERYFIKACJA &8| &fObróć kamerę: &a&l{CURRENT}&7/&e&l{TARGET}"));
        this.movementWalkActionbar = color(config.getString("messages.movement-walk-actionbar", "&e&lWERYFIKACJA &8| &fZrób kroki: &a&l{CURRENT}&7/&e&l{TARGET}"));
        this.movementVerifiedActionbar = color(config.getString("messages.movement-verified-actionbar", "&a✔ Ruch pomyślnie zweryfikowany!"));
        this.chatBlocked = color(config.getString("messages.chat-blocked", "&cMusisz najpierw zweryfikowac konto!"));
        this.commandBlocked = color(config.getString("messages.command-blocked", "&cMusisz najpierw zweryfikowac konto!"));
        this.adminAttackStarted = color(config.getString("messages.admin-attack-started", "&cWykryto atak botow!"));
        this.adminAttackStopped = color(config.getString("messages.admin-attack-stopped", "&aAtak botow ustal."));
        this.adminBotBlocked = color(config.getString("messages.admin-bot-blocked", "&7Zablokowano: {PLAYER}"));
    }

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // Getters
    public String getPrefix() { return prefix; }
    public boolean isAttackModeEnabled() { return attackModeEnabled; }
    public int getTriggerJoins() { return triggerJoins; }
    public int getTriggerSeconds() { return triggerSeconds; }
    public int getAttackDurationSeconds() { return attackDurationSeconds; }
    public boolean isBlockNewPlayers() { return blockNewPlayers; }
    public boolean isRequireReconnect() { return requireReconnect; }
    public int getReconnectDelaySeconds() { return reconnectDelaySeconds; }
    public boolean isPingCheckEnabled() { return pingCheckEnabled; }
    public int getMaxPingAgeSeconds() { return maxPingAgeSeconds; }
    public boolean isIpLimitEnabled() { return ipLimitEnabled; }
    public int getMaxConnectionsPerIp() { return maxConnectionsPerIp; }
    public boolean isNicknameFilterEnabled() { return nicknameFilterEnabled; }
    public int getMinNickLength() { return minNickLength; }
    public int getMaxNickLength() { return maxNickLength; }
    public List<Pattern> getBlockedNickPatterns() { return blockedNickPatterns; }
    public boolean isCaptchaEnabled() { return captchaEnabled; }
    public String getCaptchaTriggerMode() { return captchaTriggerMode; }
    public int getCaptchaCodeLength() { return captchaCodeLength; }
    public String getCaptchaCharacters() { return captchaCharacters; }
    public int getCaptchaTimeoutSeconds() { return captchaTimeoutSeconds; }
    public int getCaptchaMaxAttempts() { return captchaMaxAttempts; }
    public boolean isCaptchaCaseSensitive() { return captchaCaseSensitive; }
    public boolean isCaptchaAllowChatFallback() { return captchaAllowChatFallback; }
    public int getCaptchaOpenDelayTicks() { return captchaOpenDelayTicks; }
    public String getCaptchaRequiredLine() { return captchaRequiredLine; }
    public boolean isCaptchaBlindness() { return captchaBlindness; }
    public boolean isAuthEnabled() { return authEnabled; }
    public boolean isAuthPremiumAutoDetect() { return authPremiumAutoDetect; }
    public int getAuthTimeoutSeconds() { return authTimeoutSeconds; }
    public int getAuthMaxFailedAttempts() { return authMaxFailedAttempts; }
    public int getAuthMinPasswordLength() { return authMinPasswordLength; }
    public int getAuthMaxPasswordLength() { return authMaxPasswordLength; }
    public boolean isAuthBlindness() { return authBlindness; }
    public boolean isAuthSessionEnabled() { return authSessionEnabled; }
    public int getAuthSessionTimeoutMinutes() { return authSessionTimeoutMinutes; }
    public boolean isMovementCheckEnabled() { return movementCheckEnabled; }
    public int getMovementTimeoutSeconds() { return movementTimeoutSeconds; }
    public boolean isBlockChatBeforeVerification() { return blockChatBeforeVerification; }
    public boolean isBlockCommandsBeforeVerification() { return blockCommandsBeforeVerification; }
    public boolean isNotifyOnAttackState() { return notifyOnAttackState; }
    public boolean isNotifyOnBotBlock() { return notifyOnBotBlock; }
    public boolean isShowActionbar() { return showActionbar; }
    public String getAttackModeKick() { return attackModeKick; }
    public String getPingCheckKick() { return pingCheckKick; }
    public String getIpLimitKick() { return ipLimitKick; }
    public String getInvalidNicknameKick() { return invalidNicknameKick; }
    public String getReconnectWaitKick() { return reconnectWaitKick; }
    public String getCaptchaTitle() { return captchaTitle; }
    public String getCaptchaSubtitle() { return captchaSubtitle; }
    public String getCaptchaChatPrompt() { return captchaChatPrompt; }
    public String getCaptchaActionbar() { return captchaActionbar; }
    public String getCaptchaSuccess() { return captchaSuccess; }
    public String getCaptchaFailedAttempt() { return captchaFailedAttempt; }
    public String getCaptchaWrongLine() { return captchaWrongLine; }
    public String getCaptchaKickFailed() { return captchaKickFailed; }
    public String getCaptchaKickTimeout() { return captchaKickTimeout; }
    public String getCaptchaBlockedAction() { return captchaBlockedAction; }
    public String getAuthLoginPromptActionbar() { return authLoginPromptActionbar; }
    public String getAuthLoginPromptChat() { return authLoginPromptChat; }
    public String getAuthRegisterPromptActionbar() { return authRegisterPromptActionbar; }
    public String getAuthRegisterPromptChat() { return authRegisterPromptChat; }
    public String getAuthLoginSuccess() { return authLoginSuccess; }
    public String getAuthRegisterSuccess() { return authRegisterSuccess; }
    public String getAuthWrongPassword() { return authWrongPassword; }
    public String getAuthPasswordsNotMatch() { return authPasswordsNotMatch; }
    public String getAuthPasswordNotComplex() { return authPasswordNotComplex; }
    public boolean isAuthRequireComplexPassword() { return authRequireComplexPassword; }
    public String getAuthPremiumWelcome() { return authPremiumWelcome; }
    public String getAuthPremiumActionbar() { return authPremiumActionbar; }
    public String getAuthPremiumTitle() { return authPremiumTitle; }
    public String getAuthPremiumSubtitle() { return authPremiumSubtitle; }
    public String getAuthSessionRestored() { return authSessionRestored; }
    public String getAuthTimeoutKick() { return authTimeoutKick; }
    public String getAuthMaxAttemptsKick() { return authMaxAttemptsKick; }
    public String getMovementPrompt() { return movementPrompt; }
    public String getMovementVerified() { return movementVerified; }
    public int getRequiredCameraMoves() { return requiredCameraMoves; }
    public int getRequiredWalkSteps() { return requiredWalkSteps; }
    public String getMovementCameraActionbar() { return movementCameraActionbar; }
    public String getMovementWalkActionbar() { return movementWalkActionbar; }
    public String getMovementVerifiedActionbar() { return movementVerifiedActionbar; }
    public String getChatBlocked() { return chatBlocked; }
    public String getCommandBlocked() { return commandBlocked; }
    public String getAdminAttackStarted() { return adminAttackStarted; }
    public String getAdminAttackStopped() { return adminAttackStopped; }
    public String getAdminBotBlocked() { return adminBotBlocked; }

    public boolean isAntiVpnEnabled() { return antiVpnEnabled; }
    public String getAntiVpnTriggerMode() { return antiVpnTriggerMode; }
    public String getAntiVpnProvider() { return antiVpnProvider; }
    public String getAntiVpnApiKey() { return antiVpnApiKey; }
    public boolean isAntiVpnBlockHosting() { return antiVpnBlockHosting; }
    public boolean isAntiVpnBypassTrusted() { return antiVpnBypassTrusted; }
    public int getAntiVpnCacheTimeHours() { return antiVpnCacheTimeHours; }
    public int getAntiVpnMaxWorkerThreads() { return antiVpnMaxWorkerThreads; }
    public int getAntiVpnMaxPendingTasks() { return antiVpnMaxPendingTasks; }
    public int getAntiVpnMaxConcurrentRequests() { return antiVpnMaxConcurrentRequests; }
    public int getAntiVpnConnectTimeoutMs() { return antiVpnConnectTimeoutMs; }
    public int getAntiVpnRequestTimeoutMs() { return antiVpnRequestTimeoutMs; }
    public int getAntiVpnGlobalChecksPerMinute() { return antiVpnGlobalChecksPerMinute; }
    public int getAntiVpnIpCheckCooldownSeconds() { return antiVpnIpCheckCooldownSeconds; }
    public String getAntiVpnOverloadPolicy() { return antiVpnOverloadPolicy; }
    public String getAntiVpnFailurePolicy() { return antiVpnFailurePolicy; }
    public List<String> getAntiVpnWhitelistIps() { return antiVpnWhitelistIps; }
    public Set<String> getAntiVpnWhitelistPlayers() { return antiVpnWhitelistPlayers; }
    public String getAntiVpnKick() { return antiVpnKick; }
    public String getAntiVpnOverloadKick() { return antiVpnOverloadKick; }

    public boolean isIpBurstProtectionEnabled() { return ipBurstProtectionEnabled; }
    public int getIpBurstMaxConnections() { return ipBurstMaxConnections; }
    public int getIpBurstWindowSeconds() { return ipBurstWindowSeconds; }
    public String getIpBurstKick() { return ipBurstKick; }

    public boolean isSharedIpEnabled() { return sharedIpEnabled; }
    public int getSharedIpExtraConnections() { return sharedIpExtraConnections; }
    public boolean isGlobalThrottleEnabled() { return globalThrottleEnabled; }
    public int getGlobalMaxJoinsPerSecond() { return globalMaxJoinsPerSecond; }
    public boolean isTempBlockEnabled() { return tempBlockEnabled; }
    public int getTempBlockDurationSeconds() { return tempBlockDurationSeconds; }
    public int getMaxConcurrentVerifications() { return maxConcurrentVerifications; }
    public boolean isSingleFlightHandshake() { return singleFlightHandshake; }
    public String getGlobalThrottleKick() { return globalThrottleKick; }
    public String getTempBlockKick() { return tempBlockKick; }
    public String getVerificationQueueFullKick() { return verificationQueueFullKick; }
    public String getDuplicateHandshakeKick() { return duplicateHandshakeKick; }

    public boolean isUpdateCheckerEnabled() { return updateCheckerEnabled; }
    public String getUpdateCheckerGithubRepo() { return updateCheckerGithubRepo; }
    public boolean isUpdateCheckerNotifyAdmins() { return updateCheckerNotifyAdmins; }
    public int getUpdateCheckerIntervalHours() { return updateCheckerIntervalHours; }
}
