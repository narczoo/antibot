package pl.antybot.plugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.auth.AuthAccount;
import pl.antybot.plugin.auth.AuthManager;
import pl.antybot.plugin.config.Settings;

public class ChangePasswordCommand implements CommandExecutor {

    private final AntiBotPlugin plugin;

    public ChangePasswordCommand(AntiBotPlugin plugin) {
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

        AuthManager am = plugin.getAuthManager();
        if (!am.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(s.getPrefix() + "§cMusisz się najpierw zalogować!");
            return true;
        }

        AuthAccount account = am.getAccount(player.getName());
        if (account != null && account.isPremium()) {
            player.sendMessage(s.getPrefix() + "§cKonta Premium korzystają z automatycznego logowania i nie posiadają hasła.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(s.getPrefix() + "§cUżycie: §e/" + label + " <stareHasło> <noweHasło>");
            return true;
        }

        String oldPass = args[0];
        String newPass = args[1];

        if (newPass.length() < s.getAuthMinPasswordLength() || newPass.length() > s.getAuthMaxPasswordLength()) {
            player.sendMessage(s.getPrefix() + "§cNowe hasło musi mieć od §e" + s.getAuthMinPasswordLength() + " §cdo §e" + s.getAuthMaxPasswordLength() + " §cznaków!");
            return true;
        }

        if (s.isAuthRequireComplexPassword() && !AuthManager.isPasswordComplex(newPass)) {
            player.sendMessage(s.getPrefix() + s.getAuthPasswordNotComplex());
            return true;
        }

        boolean success = am.changePassword(player, oldPass, newPass);
        if (success) {
            player.sendMessage(s.getPrefix() + "§aHasło zostało pomyślnie zmienione!");
        } else {
            player.sendMessage(s.getPrefix() + "§cPodane stare hasło jest nieprawidłowe!");
        }
        return true;
    }
}
