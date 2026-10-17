package pl.antybot.plugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.auth.AuthAccount;

public class PremiumCommand implements CommandExecutor {

    private final AntiBotPlugin plugin;

    public PremiumCommand(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest dostepna tylko dla graczy w grze.");
            return true;
        }

        String prefix = plugin.getSettings().getPrefix();
        AuthAccount account = plugin.getAuthManager().getAccount(player.getName());

        if (account != null && account.isPremium()) {
            player.sendMessage(prefix + "§aTwoje konto (§e" + player.getName() + "§a) jest zweryfikowane jako §6§lPREMIUM§a!");
            player.sendMessage(prefix + "§7Automatyczne logowanie Mojang jest §aAKTYWNE§7 (nie musisz podawać hasła).");
        } else {
            player.sendMessage(prefix + "§7Twoje konto (§f" + player.getName() + "§7) posiada status §eNon-Premium§7.");
            player.sendMessage(prefix + "§7Logujesz się przy pomocy hasła (/login <hasło>).");
        }
        return true;
    }
}
