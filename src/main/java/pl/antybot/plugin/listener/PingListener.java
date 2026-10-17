package pl.antybot.plugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import pl.antybot.plugin.AntiBotPlugin;

import java.net.InetAddress;

public class PingListener implements Listener {

    private final AntiBotPlugin plugin;

    public PingListener(AntiBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerListPing(ServerListPingEvent event) {
        InetAddress address = event.getAddress();
        if (address != null) {
            String ip = address.getHostAddress();
            plugin.getPingManager().recordPing(ip);
        }
    }
}
