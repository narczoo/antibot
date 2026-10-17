package pl.antybot.plugin.captcha;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class CaptchaSession {

    private final UUID playerUuid;
    private String code;
    private int targetLine; // 0, 1, 2, 3
    private int attemptsLeft;
    private final Location initialPlayerLocation;
    private Location signLocation;
    private BlockData originalBlockData;
    private BukkitTask timeoutTask;
    private BukkitTask reminderTask;
    private BukkitTask retryTask;
    private long lastGuiOpenAttempt;
    private final long startedAt;

    public CaptchaSession(UUID playerUuid, String code, int targetLine, int maxAttempts, Location initialPlayerLocation) {
        this.playerUuid = playerUuid;
        this.code = code;
        this.targetLine = targetLine;
        this.attemptsLeft = maxAttempts;
        this.initialPlayerLocation = initialPlayerLocation;
        this.startedAt = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getTargetLine() {
        return targetLine;
    }

    public void setTargetLine(int targetLine) {
        this.targetLine = targetLine;
    }

    public int getDisplayLine() {
        return targetLine + 1; // 1, 2, 3, 4 dla czlowieka
    }

    public int getAttemptsLeft() {
        return attemptsLeft;
    }

    public int decrementAttempts() {
        return --this.attemptsLeft;
    }

    public Location getInitialPlayerLocation() {
        return initialPlayerLocation;
    }

    public Location getSignLocation() {
        return signLocation;
    }

    public void setSignLocation(Location signLocation) {
        this.signLocation = signLocation;
    }

    public BlockData getOriginalBlockData() {
        return originalBlockData;
    }

    public void setOriginalBlockData(BlockData originalBlockData) {
        this.originalBlockData = originalBlockData;
    }

    public BukkitTask getTimeoutTask() {
        return timeoutTask;
    }

    public void setTimeoutTask(BukkitTask timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    public BukkitTask getReminderTask() {
        return reminderTask;
    }

    public void setReminderTask(BukkitTask reminderTask) {
        this.reminderTask = reminderTask;
    }

    public BukkitTask getRetryTask() {
        return retryTask;
    }

    public void setRetryTask(BukkitTask retryTask) {
        this.retryTask = retryTask;
    }

    public long getLastGuiOpenAttempt() {
        return lastGuiOpenAttempt;
    }

    public void setLastGuiOpenAttempt(long lastGuiOpenAttempt) {
        this.lastGuiOpenAttempt = lastGuiOpenAttempt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getRemainingSeconds(int totalTimeoutSeconds) {
        long elapsed = (System.currentTimeMillis() - startedAt) / 1000L;
        return Math.max(0, totalTimeoutSeconds - elapsed);
    }

    public void cancelTasks() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
    }
}
