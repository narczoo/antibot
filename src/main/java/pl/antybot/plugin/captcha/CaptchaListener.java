package pl.antybot.plugin.captcha;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.antybot.plugin.AntiBotPlugin;

import java.util.UUID;

public class CaptchaListener implements Listener {

    private final AntiBotPlugin plugin;

    public CaptchaListener(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        CaptchaSession session = plugin.getCaptchaManager().getSession(uuid);
        if (session == null) {
            // Sprawdzenie również po koordynatach tabliczki
            session = plugin.getCaptchaManager().getSessionByLocation(event.getBlock().getLocation());
        }

        if (session != null) {
            // Zawsze anulujemy zdarzenie tabliczki, aby nie modyfikowała świata
            event.setCancelled(true);
            plugin.getCaptchaManager().verifySubmission(player, session, event.getLines());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getCaptchaManager().cancelSession(player.getUniqueId(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCaptchaManager().isPending(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            // Zezwalamy na obrót kamery, blokujemy przemieszczanie się
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
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getCaptchaManager().isPending(uuid)) {
            event.setCancelled(true);

            if (plugin.getSettings().isCaptchaAllowChatFallback()) {
                String message = event.getMessage();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CaptchaSession session = plugin.getCaptchaManager().getSession(uuid);
                    if (session != null && player.isOnline()) {
                        String[] lines = new String[4];
                        lines[session.getTargetLine()] = message;
                        plugin.getCaptchaManager().verifySubmission(player, session, lines);
                    }
                });
            } else {
                player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getCaptchaBlockedAction());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCaptchaManager().isPending(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getSettings().getPrefix() + plugin.getSettings().getCaptchaBlockedAction());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCaptchaManager().isPending(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCaptchaManager().isPending(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.getCaptchaManager().isPending(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (plugin.getCaptchaManager().isPending(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
