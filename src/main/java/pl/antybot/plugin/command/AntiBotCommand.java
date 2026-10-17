package pl.antybot.plugin.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.auth.AuthAccount;
import pl.antybot.plugin.manager.AntiVpnManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AntiBotCommand implements CommandExecutor, TabCompleter {

    private final AntiBotPlugin plugin;

    public AntiBotCommand(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antibot.admin")) {
            sender.sendMessage(plugin.getSettings().getPrefix() + "§cBrak uprawnień (§4antibot.admin§c).");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status":
                sendStatus(sender);
                return true;

            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getSettings().getPrefix() + "§aKonfiguracja pluginu została pomyślnie przeładowana!");
                return true;

            case "attack":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " attack <on|off> [czas_w_sekundach]");
                    return true;
                }
                if (args[1].equalsIgnoreCase("on")) {
                    int duration = plugin.getSettings().getAttackDurationSeconds();
                    if (args.length >= 3) {
                        try {
                            duration = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(plugin.getSettings().getPrefix() + "§cPodany czas musi być liczbą całkowitą!");
                            return true;
                        }
                    }
                    plugin.getAttackManager().triggerAttackMode(duration);
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§aRęcznie aktywowano tryb ochrony na §e" + duration + "s§a.");
                } else if (args[1].equalsIgnoreCase("off")) {
                    plugin.getAttackManager().stopAttackMode();
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cRęcznie wyłączono tryb obrony przed botami.");
                } else {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cWybierz: §eon §club §eoff§c.");
                }
                return true;

            case "captcha":
                if (args.length < 2) {
                    if (sender instanceof Player p) {
                        plugin.getCaptchaManager().startCaptcha(p);
                        sender.sendMessage(plugin.getSettings().getPrefix() + "§aUruchomiono weryfikację captcha na sobie!");
                        return true;
                    }
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " captcha <gracz>");
                    return true;
                }
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cGracz musi być online, aby otworzyć mu captchę!");
                    return true;
                }
                plugin.getCaptchaManager().startCaptcha(targetPlayer);
                sender.sendMessage(plugin.getSettings().getPrefix() + "§aWymuszono weryfikację captcha dla gracza §e" + targetPlayer.getName() + "§a.");
                return true;

            case "trust":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " trust <gracz>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cGracz o podanym nicku nie istnieje.");
                    return true;
                }
                plugin.getTrustManager().addTrusted(target.getUniqueId());
                plugin.getTrustManager().saveAsync();
                sender.sendMessage(plugin.getSettings().getPrefix() + "§aGracz §e" + target.getName() + " §azostał dodany do bazy zaufanych.");
                return true;

            case "untrust":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " untrust <gracz>");
                    return true;
                }
                OfflinePlayer targetUntrust = Bukkit.getOfflinePlayer(args[1]);
                if (targetUntrust == null || targetUntrust.getUniqueId() == null) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cGracz o podanym nicku nie istnieje.");
                    return true;
                }
                plugin.getTrustManager().removeTrusted(targetUntrust.getUniqueId());
                plugin.getTrustManager().saveAsync();
                sender.sendMessage(plugin.getSettings().getPrefix() + "§cGracz §e" + targetUntrust.getName() + " §czostał usunięty z bazy zaufanych.");
                return true;

            case "unregister":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " unregister <gracz>");
                    return true;
                }
                plugin.getAuthManager().unregister(args[1]);
                sender.sendMessage(plugin.getSettings().getPrefix() + "§aWyrejestrowano konto gracza §e" + args[1] + "§a.");
                return true;

            case "setpassword":
                if (args.length < 3) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " setpassword <gracz> <noweHasło>");
                    return true;
                }
                plugin.getAuthManager().setPassword(args[1], args[2]);
                sender.sendMessage(plugin.getSettings().getPrefix() + "§aZmieniono hasło dla konta gracza §e" + args[1] + "§a.");
                return true;

            case "forcelogin":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " forcelogin <gracz>");
                    return true;
                }
                Player forcePlayer = Bukkit.getPlayer(args[1]);
                if (forcePlayer == null || !forcePlayer.isOnline()) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cGracz musi być online!");
                    return true;
                }
                plugin.getAuthManager().finishAuth(forcePlayer);
                sender.sendMessage(plugin.getSettings().getPrefix() + "§aWymuszono zalogowanie gracza §e" + forcePlayer.getName() + "§a.");
                return true;

            case "setpremium":
                if (args.length < 3) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " setpremium <gracz> <true|false>");
                    return true;
                }
                boolean isPrem = Boolean.parseBoolean(args[2]);
                AuthAccount targetAcc = plugin.getAuthManager().getOrCreateAccount(args[1]);
                targetAcc.setPremium(isPrem);
                plugin.getAuthManager().saveAsync();

                Player onlineTarget = Bukkit.getPlayer(args[1]);
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    if (isPrem) {
                        plugin.getAuthManager().autoLoginPremium(onlineTarget, targetAcc);
                    }
                }

                if (isPrem) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§aUstawiono status konta gracza §e" + args[1] + " §ana §6§lPREMIUM§a.");
                } else {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§eUstawiono status konta gracza §e" + args[1] + " §ana §7NON-PREMIUM§e.");
                }
                return true;

            case "clear":
                plugin.getAttackManager().clear();
                plugin.getPingManager().clear();
                plugin.getIpManager().clear();
                plugin.getCaptchaManager().cleanupAll();
                if (plugin.getAntiVpnManager() != null) {
                    plugin.getAntiVpnManager().clearCache();
                }
                if (plugin.getAuthManager() != null) {
                    plugin.getAuthManager().getMojangResolver().clearCache();
                }
                sender.sendMessage(plugin.getSettings().getPrefix() + "§aWyczyszczono pamięć podręczną IP oraz statystyki bieżących ataków.");
                return true;

            case "vpn":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " vpn <check|clear|status> [ip|gracz]");
                    return true;
                }
                if (args[1].equalsIgnoreCase("status")) {
                    sendVpnStatus(sender);
                    return true;
                }
                if (args[1].equalsIgnoreCase("clear")) {
                    if (plugin.getAntiVpnManager() != null) {
                        plugin.getAntiVpnManager().clearCache();
                    }
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§aWyczyszczono pamięć podręczną Anti-VPN.");
                    return true;
                }
                if (args[1].equalsIgnoreCase("check")) {
                    if (args.length < 3) {
                        sender.sendMessage(plugin.getSettings().getPrefix() + "§cUżycie: /" + label + " vpn check <ip|gracz>");
                        return true;
                    }
                    String targetIp = args[2];
                    Player online = Bukkit.getPlayer(args[2]);
                    if (online != null && online.isOnline() && online.getAddress() != null) {
                        targetIp = online.getAddress().getAddress().getHostAddress();
                    }
                    String finalIp = targetIp;
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§7Sprawdzanie IP §e" + finalIp + "§7...");
                    plugin.getAntiVpnManager().checkAsync(finalIp, null, null, false).thenAccept(res -> {
                        sender.sendMessage(plugin.getSettings().getPrefix() + "§7Wynik dla §e" + finalIp + "§7: " + res.toString());
                    });
                    return true;
                }
            case "update":
            case "version":
                if (plugin.getUpdateChecker() == null) {
                    sender.sendMessage(plugin.getSettings().getPrefix() + "§cModuł sprawdzania aktualizacji nie jest aktywny.");
                    return true;
                }
                sender.sendMessage(plugin.getSettings().getPrefix() + "§7Sprawdzanie dostępności aktualizacji na GitHubie (§e" + plugin.getSettings().getUpdateCheckerGithubRepo() + "§7)...");
                plugin.getUpdateChecker().checkAsync().thenAccept(res -> {
                    if (res.isError()) {
                        sender.sendMessage(plugin.getSettings().getPrefix() + "§cWystąpił błąd podczas sprawdzania aktualizacji: §e" + res.getErrorMessage());
                    } else if (res.isUpdateAvailable()) {
                        sender.sendMessage(plugin.getSettings().getPrefix() + "§a§lDOSTĘPNA AKTUALIZACJA! §7Twoja wersja: §cv" + res.getCurrentVersion() + " §7-> Najnowsza: §a§lv" + res.getLatestVersion());
                        sender.sendMessage(plugin.getSettings().getPrefix() + "§7Pobierz nowe wydanie: §b" + res.getDownloadUrl());
                    } else {
                        sender.sendMessage(plugin.getSettings().getPrefix() + "§aPlugin jest w pełni aktualny! Korzystasz z najnowszej wersji (§ev" + res.getCurrentVersion() + "§a).");
                    }
                });
                return true;

            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§8§m----------------§r §c§lAntyBot 1.21.4 §8§m----------------");
        sender.sendMessage("§e/" + label + " status §7- Wyświetla ogólny stan ochrony i statystyki");
        sender.sendMessage("§e/" + label + " captcha [gracz] §7- Wymusza test Sign GUI Captchy na sobie lub graczu");
        sender.sendMessage("§e/" + label + " unregister <gracz> §7- Usuwa konto gracza z bazy");
        sender.sendMessage("§e/" + label + " setpassword <gracz> <hasło> §7- Ustawia nowe hasło dla gracza");
        sender.sendMessage("§e/" + label + " setpremium <gracz> <true|false> §7- Ustawia status konta Premium");
        sender.sendMessage("§e/" + label + " forcelogin <gracz> §7- Wymusza zalogowanie gracza online");
        sender.sendMessage("§e/" + label + " attack <on|off> [czas] §7- Ręcznie włącza/wyłącza tryb obrony");
        sender.sendMessage("§e/" + label + " reload §7- Przeładowuje plik konfiguracyjny (config.yml)");
        sender.sendMessage("§e/" + label + " trust <gracz> §7- Dodaje gracza do zaufanych");
        sender.sendMessage("§e/" + label + " untrust <gracz> §7- Usuwa gracza z zaufanych");
        sender.sendMessage("§e/" + label + " vpn status §7- Szczegółowe metryki i stan kolejki Anti-VPN");
        sender.sendMessage("§e/" + label + " vpn check <ip|gracz> §7- Sprawdza adres IP w bazie Anti-VPN");
        sender.sendMessage("§e/" + label + " vpn clear §7- Czyści pamięć podręczną Anti-VPN");
        sender.sendMessage("§e/" + label + " update §7- Sprawdza dostępność nowej wersji na GitHubie");
        sender.sendMessage("§e/" + label + " clear §7- Czyści pamięć podręczną i statystyki");
        sender.sendMessage("§8§m------------------------------------------------");
    }

    private void sendVpnStatus(CommandSender sender) {
        AntiVpnManager vpn = plugin.getAntiVpnManager();
        if (vpn == null) {
            sender.sendMessage(plugin.getSettings().getPrefix() + "§cModuł Anti-VPN nie jest zainicjalizowany.");
            return;
        }
        sender.sendMessage("§8§m----------------§r §c§lStatus Anti-VPN §8§m----------------");
        sender.sendMessage("§7Ochrona: " + (plugin.getSettings().isAntiVpnEnabled() ? "§a§lWŁĄCZONA" : "§c§lWYŁĄCZONA") + " §8| §7Dostawca: §e" + plugin.getSettings().getAntiVpnProvider());
        sender.sendMessage("§7Wątki robocze: §e" + vpn.getActiveWorkerThreads() + "§7/§e" + plugin.getSettings().getAntiVpnMaxWorkerThreads() + " §8| §7Zadania w kolejce: §e" + vpn.getPendingTasksCount() + "§7/§e" + plugin.getSettings().getAntiVpnMaxPendingTasks());
        sender.sendMessage("§7Wolne permity HTTP: §e" + vpn.getAvailableHttpPermits() + "§7/§e" + plugin.getSettings().getAntiVpnMaxConcurrentRequests());
        sender.sendMessage("§7Wszystkie sprawdzenia: §b" + vpn.getTotalChecks() + " §8| §7Trafienia w cache: §a" + vpn.getCacheHits());
        sender.sendMessage("§7Deduplikacja Single-Flight: §a" + vpn.getSingleFlightHits() + " §8| §7Zablokowane VPN: §c" + vpn.getBlockedVpns());
        sender.sendMessage("§7Odrzucone (przeciążenie): §e" + vpn.getRejectedChecks() + " §8| §7Limit zapytań (RateLimit): §e" + vpn.getRateLimitedChecks());
        sender.sendMessage("§7Błędy API dostawców: §c" + vpn.getApiErrors() + " §8| §7Zapamiętane IP w cache: §b" + vpn.getCacheSize());
        sender.sendMessage("§8§m------------------------------------------------");
    }

    private void sendStatus(CommandSender sender) {
        boolean attack = plugin.getAttackManager().isAttackMode();
        long remaining = plugin.getAttackManager().getRemainingAttackSeconds();
        int cps = plugin.getAttackManager().getCurrentCPS();
        int blocked = plugin.getAttackManager().getBlockedBotsCount();
        int attacks = plugin.getAttackManager().getAttacksCount();
        int trusted = plugin.getTrustManager().getTrustedCount();
        int pings = plugin.getPingManager().getCachedPingCount();

        sender.sendMessage("§8§m----------------§r §c§lStatus AntyBot §8§m----------------");
        sender.sendMessage("§7Tryb ataku: " + (attack ? "§c§lAKTYWNY §8(pozostało: §e" + remaining + "s§8)" : "§a§lSPOKÓJ"));
        sender.sendMessage("§7Bieżące próby połączeń (CPS): §e" + cps);
        sender.sendMessage("§7Wykryte ataki łącznie: §e" + attacks);
        sender.sendMessage("§7Zablokowane boty łącznie: §c" + blocked);
        sender.sendMessage("§7Zaufani gracze w bazie: §a" + trusted);
        sender.sendMessage("§7Zapamiętane pingi IP: §b" + pings);
        sender.sendMessage("§7Ochrona Anti-VPN: " + (plugin.getSettings().isAntiVpnEnabled() ? "§a§lWŁĄCZONA §8(baza: §e" + (plugin.getAntiVpnManager() != null ? plugin.getAntiVpnManager().getCacheSize() : 0) + " IP§8)" : "§c§lWYŁĄCZONA"));
        String updateState = (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) ? "§e§lDOSTĘPNA AKTUALIZACJA (v" + plugin.getUpdateChecker().getLatestVersion() + ")" : "§aAktualny (v" + plugin.getDescription().getVersion() + ")";
        sender.sendMessage("§7Wersja pluginu: " + updateState);
        sender.sendMessage("§8§m------------------------------------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("antibot.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = Arrays.asList("status", "captcha", "unregister", "setpassword", "setpremium", "forcelogin", "attack", "reload", "trust", "untrust", "vpn", "update", "clear", "help");
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("attack")) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("on", "off"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("vpn")) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("check", "clear", "status"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") || args[0].equalsIgnoreCase("captcha") ||
                args[0].equalsIgnoreCase("unregister") || args[0].equalsIgnoreCase("forcelogin") || args[0].equalsIgnoreCase("setpassword") ||
                args[0].equalsIgnoreCase("setpremium")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setpremium")) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList("true", "false"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("vpn") && args[1].equalsIgnoreCase("check")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
            }
        }

        return Collections.emptyList();
    }
}
