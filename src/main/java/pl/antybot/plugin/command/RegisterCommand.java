package pl.antybot.plugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.auth.AuthAccount;
import pl.antybot.plugin.auth.AuthManager;
import pl.antybot.plugin.config.Settings;

public class RegisterCommand implements CommandExecutor {

    private final AntiBotPlugin plugin;

    public RegisterCommand(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest dostepna tylko dla graczy w grze.");
            return true;
        }

        Settings s = plugin.getSettings();
        if (!s.isAuthEnabled()) {
            player.sendMessage(s.getPrefix() + "§cSystem autoryzacji jest wyłączony.");
            return true;
        }

        if (plugin.getCaptchaManager() != null && plugin.getCaptchaManager().isPending(player.getUniqueId())) {
            player.sendMessage(s.getPrefix() + s.getCaptchaBlockedAction());
            return true;
        }

        if (plugin.getVerificationManager() != null && plugin.getVerificationManager().isVerifying(player.getUniqueId())) {
            player.sendMessage(s.getPrefix() + s.getCommandBlocked());
            return true;
        }

        AuthManager am = plugin.getAuthManager();
        AuthAccount account = am.getAccount(player.getName());
        if (account != null && account.isPremium()) {
            if (!am.isLoggedIn(player.getUniqueId())) {
                am.autoLoginPremium(player, account);
            } else {
                player.sendMessage(s.getPrefix() + "§aJesteś już zalogowany z oficjalnego konta §6§lPREMIUM§a.");
            }
            return true;
        }

        if (am.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(s.getPrefix() + "§aJesteś już zalogowany na serwerze.");
            return true;
        }

        if (am.isRegistered(player.getName())) {
            player.sendMessage(s.getPrefix() + "§cTwoje konto jest już zarejestrowane! Użyj: §e/login <hasło>");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(s.getPrefix() + "§cUżycie: §e/" + label + " <hasło> <powtórzHasło>");
            return true;
        }

        String pass1 = args[0];
        String pass2 = args[1];

        if (!pass1.equals(pass2)) {
            player.sendMessage(s.getPrefix() + s.getAuthPasswordsNotMatch());
            return true;
        }

        if (pass1.length() < s.getAuthMinPasswordLength()) {
            player.sendMessage(s.getPrefix() + "§cHasło jest za krótkie! Minimum §e" + s.getAuthMinPasswordLength() + " §cznaków.");
            return true;
        }

        if (pass1.length() > s.getAuthMaxPasswordLength()) {
            player.sendMessage(s.getPrefix() + "§cHasło jest za długie! Maksimum §e" + s.getAuthMaxPasswordLength() + " §cznaków.");
            return true;
        }

        if (s.isAuthRequireComplexPassword() && !AuthManager.isPasswordComplex(pass1)) {
            player.sendMessage(s.getPrefix() + s.getAuthPasswordNotComplex());
            return true;
        }

        am.register(player, pass1);
        return true;
    }
}
