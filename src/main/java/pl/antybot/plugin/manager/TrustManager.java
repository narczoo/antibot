package pl.antybot.plugin.manager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.antybot.plugin.AntiBotPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class TrustManager {

    private final AntiBotPlugin plugin;
    private final Set<UUID> trustedPlayers = ConcurrentHashMap.newKeySet();
    private final File trustFile;

    private final ReentrantLock fileLock = new ReentrantLock();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public TrustManager(AntiBotPlugin plugin) {
        this.plugin = plugin;
        this.trustFile = new File(plugin.getDataFolder(), "trusted.yml");
    }

    public void load() {
        fileLock.lock();
        try {
            trustedPlayers.clear();
            dirty.set(false);
            if (!trustFile.exists()) {
                return;
            }

            FileConfiguration yaml = YamlConfiguration.loadConfiguration(trustFile);
            for (String key : yaml.getStringList("trusted-uuids")) {
                try {
                    trustedPlayers.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                }
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
            List<String> list = new ArrayList<>(trustedPlayers.size());
            for (UUID uuid : trustedPlayers) {
                if (uuid != null) {
                    list.add(uuid.toString());
                }
            }
            yaml.set("trusted-uuids", list);

            try {
                yaml.save(trustFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Nie mozna zapisac bazy zaufanych graczy (trusted.yml): " + e.getMessage());
            }
        } finally {
            fileLock.unlock();
        }
    }

    public boolean isTrusted(UUID uuid) {
        return uuid != null && trustedPlayers.contains(uuid);
    }

    public void addTrusted(UUID uuid) {
        if (uuid != null) {
            boolean added = trustedPlayers.add(uuid);
            if (added) {
                dirty.set(true);
            }
        }
    }

    public void removeTrusted(UUID uuid) {
        if (uuid != null) {
            boolean removed = trustedPlayers.remove(uuid);
            if (removed) {
                dirty.set(true);
            }
        }
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public int getTrustedCount() {
        return trustedPlayers.size();
    }

    public Set<UUID> getTrustedPlayers() {
        return Collections.unmodifiableSet(trustedPlayers);
    }
}
