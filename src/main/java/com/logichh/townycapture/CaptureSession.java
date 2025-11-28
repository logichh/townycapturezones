/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.boss.BossBar
 *  org.bukkit.entity.Player
 *  org.bukkit.scheduler.BukkitRunnable
 */
package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.palmergames.bukkit.towny.object.Town;

public class CaptureSession {
    private final String pointId;
    private final String townName;
    private final String playerName;
    private final long startTime;
    private boolean inPreparationPhase;
    private int remainingTime;
    private int remainingPreparationTime;
    private int initialPreparationTime;
    private int remainingCaptureTime;
    private int initialCaptureTime;
    private final CapturePoint point;
    private final Set<Player> players;
    private final Map<Player, Double> progress;
    private final Set<Player> warnedPlayers;
    private final Map<Location, BlockData> originalBlocks;
    private final Map<Player, Long> firstJoinTimes;
    private final Set<Player> hiddenBossBars;
    private BossBar bossBar;
    private BukkitRunnable task;
    private boolean isActive;
    private final UUID initiatorUUID;

    public CaptureSession(CapturePoint point, Town town, Player player, int preparationTime, int captureTime) {
        this.pointId = point.getId();
        this.townName = town.getName();
        this.playerName = player.getName();
        this.startTime = System.currentTimeMillis();
        this.inPreparationPhase = true;
        this.remainingPreparationTime = preparationTime * 60;
        this.initialPreparationTime = preparationTime * 60;
        this.remainingCaptureTime = captureTime * 60;
        this.initialCaptureTime = captureTime * 60;
        this.remainingTime = (preparationTime + captureTime) * 60;
        this.point = point;
        this.players = new HashSet<Player>();
        this.progress = new HashMap<Player, Double>();
        this.warnedPlayers = new HashSet<Player>();
        this.originalBlocks = new HashMap<Location, BlockData>();
        this.firstJoinTimes = new HashMap<Player, Long>();
        this.hiddenBossBars = new HashSet<Player>();
        this.isActive = true;
        this.initiatorUUID = player.getUniqueId();
    }

    public String getPointId() {
        return pointId;
    }

    public String getTownName() {
        return townName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isInPreparationPhase() {
        return inPreparationPhase;
    }

    public void setInPreparationPhase(boolean inPreparationPhase) {
        this.inPreparationPhase = inPreparationPhase;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }

    public void stop() {
        if (this.task != null && !this.task.isCancelled()) {
            this.task.cancel();
        }
        if (this.bossBar != null) {
            this.bossBar.removeAll();
        }
        if (this.point != null) {
            this.point.setLastCaptureTime(System.currentTimeMillis());
        }
        this.players.clear();
        this.progress.clear();
        this.warnedPlayers.clear();
        this.originalBlocks.clear();
        this.firstJoinTimes.clear();
        this.hiddenBossBars.clear();
        this.isActive = false;
    }

    public CapturePoint getPoint() {
        return this.point;
    }

    public Set<Player> getPlayers() {
        return this.players;
    }

    public Map<Player, Double> getProgress() {
        return this.progress;
    }

    public Set<Player> getWarnedPlayers() {
        return this.warnedPlayers;
    }

    public Map<Location, BlockData> getOriginalBlocks() {
        return this.originalBlocks;
    }

    public Map<Player, Long> getFirstJoinTimes() {
        return this.firstJoinTimes;
    }

    public Set<Player> getHiddenBossBars() {
        return this.hiddenBossBars;
    }

    public BossBar getBossBar() {
        return this.bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public BukkitRunnable getTask() {
        return this.task;
    }

    public void setTask(BukkitRunnable task) {
        this.task = task;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public int getRemainingPreparationTime() {
        return this.remainingPreparationTime;
    }

    public int getInitialPreparationTime() {
        return this.initialPreparationTime;
    }

    public int getRemainingCaptureTime() {
        return this.remainingCaptureTime;
    }
    
    public void setRemainingCaptureTime(int time) {
        this.remainingCaptureTime = time;
    }

    public int getInitialCaptureTime() {
        return this.initialCaptureTime;
    }

    public UUID getInitiatorUUID() {
        return this.initiatorUUID;
    }

    public void decrementPreparationTime() {
        if (this.remainingPreparationTime > 0) {
            this.remainingPreparationTime--;
            this.remainingTime--;
        }
    }

    public void decrementCaptureTime() {
        if (this.remainingCaptureTime > 0) {
            this.remainingCaptureTime--;
            this.remainingTime--;
        }
    }

    public void startCapturePhase() {
        this.inPreparationPhase = false;
    }
}
