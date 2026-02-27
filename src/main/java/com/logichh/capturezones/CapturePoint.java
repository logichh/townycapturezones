package com.logichh.capturezones;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public class CapturePoint {
    private String id;
    private String name;
    private Location location;
    private int chunkRadius;
    private ZoneShapeType shapeType = ZoneShapeType.CIRCLE;
    private int cuboidMinX;
    private int cuboidMinY;
    private int cuboidMinZ;
    private int cuboidMaxX;
    private int cuboidMaxY;
    private int cuboidMaxZ;
    private double reward;
    private String controllingTown = "";
    private String capturingTown = null;
    private CaptureOwner controllingOwner;
    private CaptureOwner capturingOwner;
    private double captureProgress = 0.0;
    private String type = "default";
    private boolean showOnMap = true;
    private String color = "#8B0000";
    private String lastCapturingTown = "";
    private int minPlayers = 1;
    private int maxPlayers = 10;
    private boolean active = true;
    private long captureTime = 1800000L;
    private UUID worldUUID;
    private long lastCaptureTime;
    private long cooldownUntilTime;
    private CaptureOwner recaptureLockPreviousOwner;
    private long recaptureLockPreviousOwnerUntilTime;
    private CaptureOwner recaptureLockPreviousAttacker;
    private long recaptureLockPreviousAttackerUntilTime;
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
        this.cooldownUntilTime = 0L;
        this.recaptureLockPreviousOwner = null;
        this.recaptureLockPreviousOwnerUntilTime = 0L;
        this.recaptureLockPreviousAttacker = null;
        this.recaptureLockPreviousAttackerUntilTime = 0L;
        setCircleShape(chunkRadius);
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
        if (isCuboid()) {
            int halfWidth = (int) Math.ceil(getCuboidWidthBlocks() / 2.0);
            int halfDepth = (int) Math.ceil(getCuboidDepthBlocks() / 2.0);
            return Math.max(halfWidth, halfDepth);
        }
        return Math.max(1, this.chunkRadius) * 16;
    }

    public double getReward() {
        return this.reward;
    }

    public String getControllingTown() {
        if (this.controllingOwner != null && this.controllingOwner.getDisplayName() != null) {
            return this.controllingOwner.getDisplayName();
        }
        return this.controllingTown == null ? "" : this.controllingTown;
    }

    public void setControllingTown(String townName) {
        String sanitized = sanitizeName(townName);
        this.controllingTown = sanitized == null ? "" : sanitized;
        this.controllingOwner = sanitized == null
            ? null
            : CaptureOwner.fromDisplayName(CaptureOwnerType.TOWN, sanitized);
    }

    public CaptureOwner getControllingOwner() {
        if (this.controllingOwner != null) {
            return this.controllingOwner;
        }
        if (this.controllingTown != null && !this.controllingTown.isEmpty()) {
            this.controllingOwner = CaptureOwner.fromDisplayName(CaptureOwnerType.TOWN, this.controllingTown);
        }
        return this.controllingOwner;
    }

    public void setControllingOwner(CaptureOwner owner) {
        this.controllingOwner = owner;
        this.controllingTown = owner != null && owner.getDisplayName() != null
            ? owner.getDisplayName()
            : "";
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
        setCircleShape(chunkRadius);
    }

    public ZoneShapeType getShapeType() {
        return this.shapeType;
    }

    public boolean isCircle() {
        return this.shapeType == ZoneShapeType.CIRCLE;
    }

    public boolean isCuboid() {
        return this.shapeType == ZoneShapeType.CUBOID;
    }

    public void setCircleShape(int chunkRadius) {
        int normalizedRadius = Math.max(1, chunkRadius);
        this.shapeType = ZoneShapeType.CIRCLE;
        this.chunkRadius = normalizedRadius;
        syncCuboidBoundsFromCircle();
    }

    public void setCuboidBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.cuboidMinX = Math.min(minX, maxX);
        this.cuboidMinY = Math.min(minY, maxY);
        this.cuboidMinZ = Math.min(minZ, maxZ);
        this.cuboidMaxX = Math.max(minX, maxX);
        this.cuboidMaxY = Math.max(minY, maxY);
        this.cuboidMaxZ = Math.max(minZ, maxZ);
        this.shapeType = ZoneShapeType.CUBOID;

        int width = getCuboidWidthBlocks();
        int depth = getCuboidDepthBlocks();
        int halfHorizontal = (int) Math.ceil(Math.max(width, depth) / 2.0);
        this.chunkRadius = Math.max(1, (int) Math.ceil(halfHorizontal / 16.0));

        if (this.location != null && this.location.getWorld() != null) {
            this.location = new Location(
                this.location.getWorld(),
                (this.cuboidMinX + this.cuboidMaxX + 1) / 2.0,
                (this.cuboidMinY + this.cuboidMaxY + 1) / 2.0,
                (this.cuboidMinZ + this.cuboidMaxZ + 1) / 2.0
            );
            this.worldUUID = this.location.getWorld().getUID();
        }
    }

    public boolean contains(Location target, int extraChunkRadius) {
        if (target == null || this.location == null || this.location.getWorld() == null || target.getWorld() == null) {
            return false;
        }
        if (!this.location.getWorld().getUID().equals(target.getWorld().getUID())) {
            return false;
        }

        int extraBlocks = Math.max(0, extraChunkRadius) * 16;
        if (isCuboid()) {
            double x = target.getX();
            double y = target.getY();
            double z = target.getZ();
            return x >= this.cuboidMinX - extraBlocks && x <= this.cuboidMaxX + 1 + extraBlocks
                && z >= this.cuboidMinZ - extraBlocks && z <= this.cuboidMaxZ + 1 + extraBlocks
                && y >= this.cuboidMinY && y <= this.cuboidMaxY + 1;
        }

        double radiusBlocks = (Math.max(1, this.chunkRadius) + Math.max(0, extraChunkRadius)) * 16.0;
        double dx = this.location.getX() - target.getX();
        double dz = this.location.getZ() - target.getZ();
        return (dx * dx + dz * dz) <= (radiusBlocks * radiusBlocks);
    }

    public int getSpatialMinChunkX(int extraChunkRadius) {
        int extraBlocks = Math.max(0, extraChunkRadius) * 16;
        if (isCuboid()) {
            return Math.floorDiv(this.cuboidMinX - extraBlocks, 16);
        }
        int radiusBlocks = (Math.max(1, this.chunkRadius) + Math.max(0, extraChunkRadius)) * 16;
        return Math.floorDiv(this.location.getBlockX() - radiusBlocks, 16);
    }

    public int getSpatialMaxChunkX(int extraChunkRadius) {
        int extraBlocks = Math.max(0, extraChunkRadius) * 16;
        if (isCuboid()) {
            return Math.floorDiv(this.cuboidMaxX + extraBlocks, 16);
        }
        int radiusBlocks = (Math.max(1, this.chunkRadius) + Math.max(0, extraChunkRadius)) * 16;
        return Math.floorDiv(this.location.getBlockX() + radiusBlocks, 16);
    }

    public int getSpatialMinChunkZ(int extraChunkRadius) {
        int extraBlocks = Math.max(0, extraChunkRadius) * 16;
        if (isCuboid()) {
            return Math.floorDiv(this.cuboidMinZ - extraBlocks, 16);
        }
        int radiusBlocks = (Math.max(1, this.chunkRadius) + Math.max(0, extraChunkRadius)) * 16;
        return Math.floorDiv(this.location.getBlockZ() - radiusBlocks, 16);
    }

    public int getSpatialMaxChunkZ(int extraChunkRadius) {
        int extraBlocks = Math.max(0, extraChunkRadius) * 16;
        if (isCuboid()) {
            return Math.floorDiv(this.cuboidMaxZ + extraBlocks, 16);
        }
        int radiusBlocks = (Math.max(1, this.chunkRadius) + Math.max(0, extraChunkRadius)) * 16;
        return Math.floorDiv(this.location.getBlockZ() + radiusBlocks, 16);
    }

    public double[][] getMapPolygonXZ() {
        if (isCuboid()) {
            double[] xs = {
                this.cuboidMinX,
                this.cuboidMaxX + 1,
                this.cuboidMaxX + 1,
                this.cuboidMinX
            };
            double[] zs = {
                this.cuboidMinZ,
                this.cuboidMinZ,
                this.cuboidMaxZ + 1,
                this.cuboidMaxZ + 1
            };
            return new double[][]{xs, zs};
        }

        int blockRadius = Math.max(1, this.chunkRadius) * 16;
        int sides = Math.max(32, blockRadius / 2);
        double[] xs = new double[sides];
        double[] zs = new double[sides];
        for (int i = 0; i < sides; i++) {
            double angle = (2.0 * Math.PI * i) / sides;
            xs[i] = this.location.getX() + blockRadius * Math.cos(angle);
            zs[i] = this.location.getZ() + blockRadius * Math.sin(angle);
        }
        return new double[][]{xs, zs};
    }

    public int getCuboidMinX() {
        return this.cuboidMinX;
    }

    public int getCuboidMinY() {
        return this.cuboidMinY;
    }

    public int getCuboidMinZ() {
        return this.cuboidMinZ;
    }

    public int getCuboidMaxX() {
        return this.cuboidMaxX;
    }

    public int getCuboidMaxY() {
        return this.cuboidMaxY;
    }

    public int getCuboidMaxZ() {
        return this.cuboidMaxZ;
    }

    public int getCuboidWidthBlocks() {
        return Math.max(1, this.cuboidMaxX - this.cuboidMinX + 1);
    }

    public int getCuboidHeightBlocks() {
        return Math.max(1, this.cuboidMaxY - this.cuboidMinY + 1);
    }

    public int getCuboidDepthBlocks() {
        return Math.max(1, this.cuboidMaxZ - this.cuboidMinZ + 1);
    }

    public void setLastCaptureTime(long time) {
        this.lastCaptureTime = time;
    }

    public long getLastCaptureTime() {
        return this.lastCaptureTime;
    }

    public long getCooldownUntilTime() {
        return this.cooldownUntilTime;
    }

    public void setCooldownUntilTime(long cooldownUntilTime) {
        this.cooldownUntilTime = Math.max(0L, cooldownUntilTime);
    }

    public CaptureOwner getRecaptureLockPreviousOwner() {
        return this.recaptureLockPreviousOwner;
    }

    public long getRecaptureLockPreviousOwnerUntilTime() {
        return this.recaptureLockPreviousOwnerUntilTime;
    }

    public void setRecaptureLockPreviousOwner(CaptureOwner owner, long untilTime) {
        this.recaptureLockPreviousOwner = owner;
        this.recaptureLockPreviousOwnerUntilTime = Math.max(0L, untilTime);
    }

    public void clearRecaptureLockPreviousOwner() {
        this.recaptureLockPreviousOwner = null;
        this.recaptureLockPreviousOwnerUntilTime = 0L;
    }

    public CaptureOwner getRecaptureLockPreviousAttacker() {
        return this.recaptureLockPreviousAttacker;
    }

    public long getRecaptureLockPreviousAttackerUntilTime() {
        return this.recaptureLockPreviousAttackerUntilTime;
    }

    public void setRecaptureLockPreviousAttacker(CaptureOwner owner, long untilTime) {
        this.recaptureLockPreviousAttacker = owner;
        this.recaptureLockPreviousAttackerUntilTime = Math.max(0L, untilTime);
    }

    public void clearRecaptureLockPreviousAttacker() {
        this.recaptureLockPreviousAttacker = null;
        this.recaptureLockPreviousAttackerUntilTime = 0L;
    }

    public void clearCaptureCooldownAndLocks() {
        this.cooldownUntilTime = 0L;
        clearRecaptureLockPreviousOwner();
        clearRecaptureLockPreviousAttacker();
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
        return "CapturePoint{id='" + this.id + "', name='" + this.name + "', location=" + String.valueOf(this.location)
            + ", shapeType=" + this.shapeType + ", chunkRadius=" + this.chunkRadius + ", reward=" + this.reward
            + ", controllingOwner=" + String.valueOf(this.controllingOwner)
            + ", type='" + this.type + "', isActive=" + this.active + "}";
    }

    public String getCapturingTown() {
        if (this.capturingOwner != null && this.capturingOwner.getDisplayName() != null) {
            return this.capturingOwner.getDisplayName();
        }
        return this.capturingTown;
    }

    public void setCapturingTown(String townName) {
        String sanitized = sanitizeName(townName);
        this.capturingTown = sanitized;
        this.capturingOwner = sanitized == null
            ? null
            : CaptureOwner.fromDisplayName(CaptureOwnerType.TOWN, sanitized);
    }

    public CaptureOwner getCapturingOwner() {
        if (this.capturingOwner != null) {
            return this.capturingOwner;
        }
        if (this.capturingTown != null && !this.capturingTown.isEmpty()) {
            this.capturingOwner = CaptureOwner.fromDisplayName(CaptureOwnerType.TOWN, this.capturingTown);
        }
        return this.capturingOwner;
    }

    public void setCapturingOwner(CaptureOwner owner) {
        this.capturingOwner = owner;
        this.capturingTown = owner != null ? owner.getDisplayName() : null;
    }

    public void setCaptureProgress(double progress) {
        this.captureProgress = progress;
    }

    public double getCaptureProgress() {
        return this.captureProgress;
    }

    public boolean isBeingCaptured() {
        return (this.capturingOwner != null || this.capturingTown != null) && this.captureProgress > 0.0;
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

    private void syncCuboidBoundsFromCircle() {
        if (this.location == null) {
            return;
        }
        int blockRadius = Math.max(1, this.chunkRadius) * 16;
        int centerX = this.location.getBlockX();
        int centerZ = this.location.getBlockZ();
        this.cuboidMinX = centerX - blockRadius;
        this.cuboidMaxX = centerX + blockRadius;
        this.cuboidMinZ = centerZ - blockRadius;
        this.cuboidMaxZ = centerZ + blockRadius;

        World world = this.location.getWorld();
        if (world != null) {
            this.cuboidMinY = world.getMinHeight();
            this.cuboidMaxY = world.getMaxHeight() - 1;
            this.worldUUID = world.getUID();
        } else {
            this.cuboidMinY = this.location.getBlockY();
            this.cuboidMaxY = this.location.getBlockY();
        }
    }

    private String sanitizeName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

