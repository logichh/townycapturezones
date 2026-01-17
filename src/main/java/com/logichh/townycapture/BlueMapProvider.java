package com.logichh.townycapture;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.World;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * BlueMap implementation of the MapProvider interface.
 * Integrates capture points with BlueMap's marker system.
 */
public class BlueMapProvider implements MapProvider {
    
    private final TownyCapture plugin;
    private final Logger logger;
    private BlueMapAPI blueMapAPI;
    private final Map<String, ShapeMarker> markers;
    private final Map<String, POIMarker> centerMarkers;
    private boolean available;
    private String markerSetId;
    private String markerSetLabel;
    private boolean centerMarkerEnabled;
    private String centerMarkerIcon;
    private int centerMarkerIconSize;
    private String centerIconAddress;
    
    public BlueMapProvider(TownyCapture plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.markers = new HashMap<>();
        this.centerMarkers = new HashMap<>();
        this.available = false;
    }
    
    @Override
    public boolean initialize() {
        try {
            // BlueMap API uses a callback pattern for initialization
            BlueMapAPI.onEnable(this::onBlueMapEnable);
            BlueMapAPI.onDisable(this::onBlueMapDisable);
            
            // Try to get API instance if already loaded
            BlueMapAPI.getInstance().ifPresent(this::onBlueMapEnable);
            
            return true;
        } catch (Exception e) {
            logger.warning("Failed to initialize BlueMap: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    private void onBlueMapEnable(BlueMapAPI api) {
        try {
            this.blueMapAPI = api;
            this.markerSetId = plugin.getConfig().getString("bluemap.marker-set", "townycapture");
            this.markerSetLabel = plugin.getConfig().getString("bluemap.marker-set-label", "Capture Points");
            this.centerMarkerEnabled = plugin.getConfig().getBoolean("bluemap.center-marker.enabled", true);
            this.centerMarkerIcon = plugin.getConfig().getString("bluemap.center-marker.icon", "skull");
            this.centerMarkerIconSize = clampIconSize(plugin.getConfig().getInt("bluemap.center-marker.icon-size", 16));
            this.centerIconAddress = resolveCenterIconAddress();
            this.available = true;
            
            logger.info("BlueMap integration enabled!");
            
            // Update all markers after initialization
            plugin.getServer().getScheduler().runTaskLater(plugin, this::updateAllMarkers, 20L);
            
        } catch (Exception e) {
            logger.severe("Error enabling BlueMap integration: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    private void onBlueMapDisable(BlueMapAPI api) {
        this.available = false;
        this.markers.clear();
        this.centerMarkers.clear();
        this.centerIconAddress = null;
        logger.info("BlueMap integration disabled.");
    }
    
    @Override
    public boolean isAvailable() {
        return available && blueMapAPI != null;
    }
    
    @Override
    public String getName() {
        return "BlueMap";
    }
    
    @Override
    public void createOrUpdateMarker(CapturePoint point) {
        if (!isAvailable() || !point.isShowOnMap()) {
            return;
        }
        
        try {
            Location location = point.getLocation();
            World world = location.getWorld();
            
            if (world == null) {
                return;
            }

            float markerY = getMarkerHeight(location, point.getRadius());
            
            // Get BlueMap world
            blueMapAPI.getWorld(world).ifPresent(blueMapWorld -> {
                // Create marker for each map in this world
                for (BlueMapMap map : blueMapWorld.getMaps()) {
                    try {
                        // Get or create marker set
                        MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(
                            markerSetId,
                            id -> MarkerSet.builder()
                                .label(markerSetLabel)
                                .toggleable(true)
                                .defaultHidden(false)
                                .build()
                        );
                        
                        // Create shape for the capture zone (circle)
                        Shape shape = createCircleShape(location, point.getRadius());
                        
                        // Determine color based on control status
                        Color color = getMarkerColor(point);
                        
                        // Create detailed info window HTML
                        String infoHtml = createInfoWindow(point);
                        
                        // Create or update marker
                        String markerId = point.getId();
                        ShapeMarker marker = ShapeMarker.builder()
                            .label(point.getName())
                            .position(new Vector3d(location.getX(), markerY, location.getZ()))
                            .shape(shape, markerY)
                            .depthTestEnabled(false)
                            .lineColor(color)
                            .fillColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 0.3f))
                            .lineWidth(2)
                            .detail(infoHtml)
                            .build();
                        
                        markerSet.put(markerId, marker);
                        markers.put(point.getId(), marker);
                        createOrUpdateCenterMarker(markerSet, point, markerY, infoHtml);
                        
                        if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                            logger.info("Updated BlueMap marker for " + point.getId());
                        }
                        
                    } catch (Exception e) {
                        logger.warning("Failed to create marker on map " + map.getId() + ": " + e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            logger.warning("Failed to update BlueMap marker for " + point.getId() + ": " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void removeMarker(String pointId) {
        if (!isAvailable()) {
            return;
        }
        
        try {
            // Remove from all worlds and maps
            for (de.bluecolored.bluemap.api.BlueMapWorld blueMapWorld : blueMapAPI.getWorlds()) {
                for (BlueMapMap map : blueMapWorld.getMaps()) {
                    MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                    if (markerSet != null) {
                        markerSet.remove(pointId);
                        markerSet.remove(getCenterMarkerId(pointId));
                    }
                }
            }
            
            markers.remove(pointId);
            centerMarkers.remove(pointId);
            
        } catch (Exception e) {
            logger.warning("Failed to remove BlueMap marker " + pointId + ": " + e.getMessage());
        }
    }
    
    @Override
    public void updateAllMarkers() {
        if (!isAvailable()) {
            return;
        }
        
        try {
            // Clear all existing markers from the marker set
            for (de.bluecolored.bluemap.api.BlueMapWorld blueMapWorld : blueMapAPI.getWorlds()) {
                for (BlueMapMap map : blueMapWorld.getMaps()) {
                    MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                    if (markerSet != null) {
                        // Remove each marker individually as clear() method doesn't exist
                        java.util.Set<String> markerKeys = new java.util.HashSet<>(markerSet.getMarkers().keySet());
                        for (String key : markerKeys) {
                            markerSet.remove(key);
                        }
                    }
                }
            }
            
            markers.clear();
            centerMarkers.clear();
            
            // Create markers for all capture points
            for (CapturePoint point : plugin.getCapturePoints().values()) {
                if (point.isShowOnMap()) {
                    createOrUpdateMarker(point);
                }
            }
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Updated all BlueMap markers");
            }
            
        } catch (Exception e) {
            logger.severe("Failed to update BlueMap markers: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void cleanup() {
        if (isAvailable()) {
            try {
                // Remove all markers from all maps
                for (de.bluecolored.bluemap.api.BlueMapWorld blueMapWorld : blueMapAPI.getWorlds()) {
                    for (BlueMapMap map : blueMapWorld.getMaps()) {
                        MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                        if (markerSet != null) {
                            map.getMarkerSets().remove(markerSetId);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error cleaning up BlueMap markers: " + e.getMessage());
            }
        }
        
        markers.clear();
        centerMarkers.clear();
        centerIconAddress = null;
        this.available = false;
    }
    
    @Override
    public void reload() {
        updateAllMarkers();
    }
    
    /**
     * Create a circular shape for the capture zone.
     */
    private Shape createCircleShape(Location center, int radius) {
        int points = Math.max(32, radius / 4); // More points for larger circles
        Vector2d[] shapePoints = new Vector2d[points];
        
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            shapePoints[i] = new Vector2d(x, z);
        }
        
        return new Shape(shapePoints);
    }
    
    /**
     * Get the marker color based on capture point status.
     */
    private Color getMarkerColor(CapturePoint point) {
        if (plugin.isPointActive(point.getId())) {
            String colorHex = getZoneColorHex(point, "capturing", "#FFA500");
            return parseHexColor(colorHex, new Color(255, 165, 0));
        }

        String controllingTown = point.getControllingTown();
        if (controllingTown != null && !controllingTown.isEmpty()) {
            java.awt.Color townColor = getTownMapColor(controllingTown);
            if (townColor != null) {
                return new Color(townColor.getRed(), townColor.getGreen(), townColor.getBlue());
            }

            String storedHex = point.getColor();
            Color storedColor = parseHexColor(storedHex, null);
            if (storedColor != null) {
                return storedColor;
            }

            String fallbackHex = getZoneColorHex(point, "controlled", "#8B0000");
            return parseHexColor(fallbackHex, new Color(139, 0, 0));
        }

        String colorHex = getZoneColorHex(point, "unclaimed", "#808080");
        return parseHexColor(colorHex, new Color(128, 128, 128));
    }
    
    /**
     * Create HTML info window content for the marker.
     */
    private String createInfoWindow(CapturePoint point) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: sans-serif;'>");
        
        // Title
        html.append("<h3 style='margin: 0 0 10px 0; color: ");
        String controllingTown = point.getControllingTown();
        if (controllingTown == null || controllingTown.isEmpty()) {
            html.append("#888888;'>").append(Messages.get("dynmap.infowindow.status.unclaimed"));
        } else {
            html.append(getControlledColorHex(point)).append(";'>")
                .append(Messages.get("dynmap.infowindow.status.controlled"))
                .append(" ").append(controllingTown);
        }
        html.append("</h3>");
        
        // Point name
        html.append("<h4 style='margin: 5px 0;'>").append(point.getName()).append("</h4>");
        
        // Details
        html.append("<div style='font-size: 13px;'>");
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.label.type"))
            .append(":</strong> ").append(point.getType()).append("</p>");
        
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.label.reward"))
            .append(":</strong> $").append(String.format("%.2f", plugin.getBaseReward(point))).append("</p>");
        
        html.append("<p><strong>Radius:</strong> ")
            .append(point.getChunkRadius()).append(" chunks (")
            .append(point.getRadius()).append(" blocks)</p>");
        
        html.append("</div>");
        html.append("</div>");
        
        return html.toString();
    }

    private void createOrUpdateCenterMarker(MarkerSet markerSet, CapturePoint point, float markerY, String infoHtml) {
        if (!centerMarkerEnabled) {
            return;
        }

        String iconAddress = resolveCenterIconAddress();
        String markerId = getCenterMarkerId(point.getId());
        Vector3d position = new Vector3d(point.getLocation().getX(), markerY, point.getLocation().getZ());
        String label = point.getName();

        POIMarker marker = null;
        try {
            marker = (POIMarker) markerSet.get(markerId);
        } catch (ClassCastException ignored) {
            markerSet.remove(markerId);
        }

        if (marker == null) {
            POIMarker.Builder builder = POIMarker.builder()
                .label(label)
                .position(position)
                .detail(infoHtml);

            if (iconAddress != null && !iconAddress.isEmpty()) {
                builder.icon(iconAddress, centerMarkerIconSize, centerMarkerIconSize)
                    .anchor(new Vector2i(centerMarkerIconSize / 2, centerMarkerIconSize / 2));
            } else {
                builder.defaultIcon();
            }

            marker = builder.build();
            markerSet.put(markerId, marker);
        } else {
            marker.setLabel(label);
            marker.setPosition(position);
            marker.setDetail(infoHtml);
            if (iconAddress != null && !iconAddress.isEmpty()) {
                marker.setIcon(iconAddress, centerMarkerIconSize, centerMarkerIconSize);
                marker.setAnchor(new Vector2i(centerMarkerIconSize / 2, centerMarkerIconSize / 2));
            }
        }

        centerMarkers.put(point.getId(), marker);
    }

    private String getCenterMarkerId(String pointId) {
        return "center_" + pointId;
    }

    private int clampIconSize(int size) {
        int clamped = Math.max(8, size);
        return Math.min(clamped, 64);
    }

    private String resolveCenterIconAddress() {
        if (centerIconAddress != null) {
            return centerIconAddress;
        }
        if (!centerMarkerEnabled) {
            return null;
        }
        if (centerMarkerIcon == null || centerMarkerIcon.trim().isEmpty()) {
            return null;
        }
        if (isSkullIcon(centerMarkerIcon)) {
            centerIconAddress = registerSkullIcon();
            return centerIconAddress;
        }
        centerIconAddress = centerMarkerIcon.trim();
        return centerIconAddress;
    }

    private boolean isSkullIcon(String value) {
        return "skull".equalsIgnoreCase(value.trim());
    }

    private String registerSkullIcon() {
        if (blueMapAPI == null || blueMapAPI.getWebApp() == null) {
            return null;
        }
        try {
            String imageName = "townycapture_skull_" + centerMarkerIconSize;
            return blueMapAPI.getWebApp().createImage(createSkullImage(centerMarkerIconSize), imageName);
        } catch (Exception e) {
            logger.warning("Failed to register BlueMap skull icon: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private BufferedImage createSkullImage(int size) {
        int baseSize = 16;
        BufferedImage base = new BufferedImage(baseSize, baseSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();

        g.setColor(new java.awt.Color(230, 230, 230, 255));
        g.fillRect(2, 2, 12, 10);
        g.fillRect(4, 12, 8, 2);

        g.setColor(new java.awt.Color(120, 120, 120, 255));
        g.drawRect(2, 2, 12, 10);
        g.drawRect(4, 12, 8, 2);

        g.setColor(new java.awt.Color(60, 60, 60, 255));
        g.fillRect(5, 5, 2, 3);
        g.fillRect(9, 5, 2, 3);
        g.fillRect(7, 8, 2, 2);
        g.fillRect(6, 12, 1, 2);
        g.fillRect(9, 12, 1, 2);

        g.dispose();

        if (size == baseSize) {
            return base;
        }

        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(base, 0, 0, size, size, null);
        g2.dispose();
        return scaled;
    }

    private float getMarkerHeight(Location location, int radius) {
        World world = location.getWorld();
        if (world == null) {
            return (float) location.getY();
        }
        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        int radiusSq = radius * radius;
        int maxSurfaceY = world.getHighestBlockYAt(location);
        int maxAllowedY = world.getMaxHeight() - 1;
        boolean reachedMax = maxSurfaceY >= maxAllowedY;

        for (int x = centerX - radius; x <= centerX + radius && !reachedMax; x++) {
            int dx = x - centerX;
            int dxSq = dx * dx;
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dz = z - centerZ;
                if (dxSq + (dz * dz) > radiusSq) {
                    continue;
                }
                int surfaceY = world.getHighestBlockYAt(x, z);
                if (surfaceY > maxSurfaceY) {
                    maxSurfaceY = surfaceY;
                    if (maxSurfaceY >= maxAllowedY) {
                        reachedMax = true;
                        break;
                    }
                }
            }
        }

        int minY = Math.max(maxSurfaceY + 1, location.getBlockY());
        return (float) Math.min(minY, maxAllowedY);
    }

    private java.awt.Color getTownMapColor(String townName) {
        try {
            Town town = TownyAPI.getInstance().getTown(townName);
            if (town == null) {
                return null;
            }
            return town.getMapColor();
        } catch (Exception e) {
            return null;
        }
    }

    private String getControlledColorHex(CapturePoint point) {
        String controllingTown = point.getControllingTown();
        if (controllingTown == null || controllingTown.isEmpty()) {
            return "#888888";
        }

        java.awt.Color townColor = getTownMapColor(controllingTown);
        if (townColor != null) {
            return String.format("#%02x%02x%02x", townColor.getRed(), townColor.getGreen(), townColor.getBlue());
        }

        String storedHex = point.getColor();
        if (storedHex != null && storedHex.trim().startsWith("#")) {
            return storedHex.trim();
        }

        return getZoneColorHex(point, "controlled", "#8B0000");
    }

    private String getZoneColorHex(CapturePoint point, String key, String defaultColor) {
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager != null) {
            return zoneConfigManager.getString(point.getId(), "bluemap.colors." + key, defaultColor);
        }
        return defaultColor;
    }

    private Color parseHexColor(String colorHex, Color fallback) {
        if (colorHex == null) {
            return fallback;
        }
        String normalized = colorHex.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            int rgb = Integer.parseInt(normalized, 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return new Color(r, g, b);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
