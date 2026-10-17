package pl.antybot.plugin.manager;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class VerificationSession {

    private final UUID playerUuid;
    private final int targetCameraMoves;
    private final int targetWalkSteps;
    private int cameraMoves = 0;
    private int walkSteps = 0;
    private float lastYaw;
    private float lastPitch;
    private Location lastStepLocation;
    private boolean cameraPhaseComplete = false;
    private BukkitTask timeoutTask;
    private BukkitTask actionbarTask;

    public VerificationSession(UUID playerUuid, int targetCameraMoves, int targetWalkSteps, float initialYaw, float initialPitch, Location initialLocation) {
        this.playerUuid = playerUuid;
        this.targetCameraMoves = targetCameraMoves;
        this.targetWalkSteps = targetWalkSteps;
        this.lastYaw = initialYaw;
        this.lastPitch = initialPitch;
        this.lastStepLocation = initialLocation != null ? initialLocation.clone() : null;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getTargetCameraMoves() {
        return targetCameraMoves;
    }

    public int getTargetWalkSteps() {
        return targetWalkSteps;
    }

    public int getCameraMoves() {
        return cameraMoves;
    }

    public void incrementCameraMoves() {
        this.cameraMoves++;
    }

    public int getWalkSteps() {
        return walkSteps;
    }

    public void incrementWalkSteps() {
        this.walkSteps++;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public void setLastYaw(float lastYaw) {
        this.lastYaw = lastYaw;
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public void setLastPitch(float lastPitch) {
        this.lastPitch = lastPitch;
    }

    public Location getLastStepLocation() {
        return lastStepLocation;
    }

    public void setLastStepLocation(Location lastStepLocation) {
        this.lastStepLocation = lastStepLocation;
    }

    public boolean isCameraPhaseComplete() {
        return cameraPhaseComplete;
    }

    public void setCameraPhaseComplete(boolean cameraPhaseComplete) {
        this.cameraPhaseComplete = cameraPhaseComplete;
    }

    public BukkitTask getTimeoutTask() {
        return timeoutTask;
    }

    public void setTimeoutTask(BukkitTask timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    public BukkitTask getActionbarTask() {
        return actionbarTask;
    }

    public void setActionbarTask(BukkitTask actionbarTask) {
        this.actionbarTask = actionbarTask;
    }

    public void cancelTasks() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }
    }
}
