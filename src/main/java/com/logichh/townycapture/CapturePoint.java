/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 */
package com.logichh.townycapture;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import com.palmergames.bukkit.towny.object.Town;

public class CapturePoint {
    private String id;
    private String name;
    private Location location;
    private int chunkRadius;
    private double reward;
    private String controllingTown = "";
    private String capturingTown = null;
    private double captureProgress = 0.0;
    private String type = "default";
    private boolean showOnMap = true;
    private String color = "#8B0000"; // Dark red instead of bright red
    private String lastCapturingTown = "";
    private int minPlayers = 1;
    private int maxPlayers = 10;
    private boolean active = true;
    private long captureTime = 1800000L; // 30 minutes in milliseconds
    private UUID worldUUID;
    private long lastCaptureTime;
    private boolean firstCaptureBonusAvailable = false;

    public CapturePoint(String id, String name, Location location, int chunkRadius, double reward) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Capture point ID cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Capture point name cannot be empty");
        }
        if (location == null) {
            throw new IllegalArgumentException("Capture point location cannot be null");
        }
        if (chunkRadius <= 0) {
            throw new IllegalArgumentException("Capture point radius must be greater than 0");
        }
        if (reward < 0.0) {
            throw new IllegalArgumentException("Capture point reward cannot be negative");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.location = location.clone();
        this.chunkRadius = chunkRadius;
        this.reward = reward;
        this.worldUUID = location.getWorld().getUID();
        this.lastCaptureTime = 0L;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public Location getLocation() {
        return this.location;
    }

    public int getChunkRadius() {
        return this.chunkRadius;
    }

    public int getRadius() {
        return this.chunkRadius * 16;
    }

    public double getReward() {
        return this.reward;
    }

    public String getControllingTown() {
        return this.controllingTown;
    }

    public void setControllingTown(String townName) {
        this.controllingTown = townName;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isShowOnMap() {
        return this.showOnMap;
    }

    public void setShowOnMap(boolean showOnMap) {
        this.showOnMap = showOnMap;
    }

    public UUID getWorldUUID() {
        return this.worldUUID;
    }

    public void setWorldUUID(UUID worldUUID) {
        this.worldUUID = worldUUID;
    }

    public void setReward(double reward) {
        this.reward = reward;
    }

    public void setChunkRadius(int chunkRadius) {
        this.chunkRadius = chunkRadius;
    }

    public void setLastCaptureTime(long time) {
        this.lastCaptureTime = time;
    }

    public long getLastCaptureTime() {
        return this.lastCaptureTime;
    }

    public int getMinPlayers() {
        return this.minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        if (minPlayers < 1) {
            throw new IllegalArgumentException("Minimum players must be at least 1");
        }
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        if (maxPlayers < this.minPlayers) {
            throw new IllegalArgumentException("Maximum players must be greater than or equal to minimum players");
        }
        this.maxPlayers = maxPlayers;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getLastCapturingTown() {
        return this.lastCapturingTown;
    }

    public void setLastCapturingTown(String townName) {
        this.lastCapturingTown = townName;
    }

    public boolean canBeCaptured() {
        return this.active;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        CapturePoint that = (CapturePoint)o;
        return Objects.equals(this.id, that.id);
    }

    public int hashCode() {
        return Objects.hash(this.id);
    }

    public String toString() {
        return "CapturePoint{id='" + this.id + "', name='" + this.name + "', location=" + String.valueOf(this.location) + ", chunkRadius=" + this.chunkRadius + ", reward=" + this.reward + ", controllingTown='" + this.controllingTown + "', type='" + this.type + "', isActive=" + this.active + "}";
    }

    public String getCapturingTown() {
        return this.capturingTown;
    }

    public void setCapturingTown(String townName) {
        this.capturingTown = townName;
    }

    public void setCaptureProgress(double progress) {
        this.captureProgress = progress;
    }

    public double getCaptureProgress() {
        return this.captureProgress;
    }

    public boolean isBeingCaptured() {
        return this.capturingTown != null && this.captureProgress > 0.0;
    }

    public boolean isFullyCaptured() {
        return this.captureProgress >= 100.0;
    }

    public String getColor() {
        return this.color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isFirstCaptureBonusAvailable() {
        return this.firstCaptureBonusAvailable;
    }

    public void setFirstCaptureBonusAvailable(boolean available) {
        this.firstCaptureBonusAvailable = available;
    }
}
