package com.logichh.townycapture;

import com.logichh.townycapture.AreaStyle;
import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.CapturePointInfoWindow;
import com.logichh.townycapture.CaptureSession;
import com.logichh.townycapture.TownyCapture;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class UpdateZones {
    private TownyCapture plugin;
    private MarkerSet markerSet;
    private MarkerAPI markerAPI;
    private AreaStyle areaStyle;
    private Map<String, AreaMarker> areaMarkers = new HashMap<String, AreaMarker>();
    private Map<String, Marker> centerMarkers = new HashMap<String, Marker>();
    private Plugin dynmapTowny;
    private Plugin papiPlugin;

    public UpdateZones(TownyCapture plugin, MarkerSet markerSet, AreaStyle areaStyle) {
        this.plugin = plugin;
        this.markerSet = markerSet;
        this.areaStyle = areaStyle;
        this.dynmapTowny = plugin.getServer().getPluginManager().getPlugin("Dynmap-Towny");
        this.papiPlugin = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
    }

    public void run() {
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            this.updateMarker(point);
        }
    }

    public void updateMarker(CapturePoint point) {
        this.updateAreaMarker(point);
        if (this.plugin.getConfig().getBoolean("dynmap.center-marker.show", true)) {
            this.updateCenterMarker(point);
        }
    }

    private void updateAreaMarker(CapturePoint point) {
        int strokeColor;
        int fillColor;
        String markerId = "capture_" + point.getId();
        AreaMarker marker = this.markerSet.findAreaMarker(markerId);
        Location center = point.getLocation();
        
        // Calculate circular zone with polygon approximation
        int blockRadius = point.getChunkRadius() * 16;
        int sides = Math.max(32, blockRadius / 2); // More sides for larger circles, better approximation
        double[] x = new double[sides];
        double[] z = new double[sides];
        
        for (int i = 0; i < sides; i++) {
            double angle = 2.0 * Math.PI * i / sides;
            x[i] = center.getX() + blockRadius * Math.cos(angle);
            z[i] = center.getZ() + blockRadius * Math.sin(angle);
        }
        double fillOpacity = Math.max(this.areaStyle.getFillOpacity(), 0.3);
        double strokeOpacity = Math.max(this.areaStyle.getStrokeOpacity(), 0.5);
        int strokeWeight = Math.max(this.areaStyle.getStrokeWeight(), 2);
        String townName = null;
        if (this.plugin.getActiveSessions().containsKey(point.getId())) {
            CaptureSession session = this.plugin.getActiveSessions().get(point.getId());
            townName = session.getTownName();
            fillColor = this.parseColor(this.plugin.getConfig().getString("colors.capturing-fill", "#FF0000"), 0xFF0000);
            strokeColor = this.parseColor(this.plugin.getConfig().getString("colors.capturing-border", "#FF0000"), 0xFF0000);
        } else {
            townName = point.getControllingTown();
            if (townName != null && !townName.isEmpty()) {
                int[] colors = this.getTownColors(townName);
                if (colors != null) {
                    fillColor = colors[0];
                    strokeColor = colors[1];
                } else {
                    fillColor = this.parseColor(this.plugin.getConfig().getString("colors.claimed-fill", "#0000FF"), 255);
                    strokeColor = this.parseColor(this.plugin.getConfig().getString("colors.claimed-border", "#FF0000"), 0xFF0000);
                }
            } else {
                fillColor = this.parseColor(this.plugin.getConfig().getString("colors.unclaimed-fill", "#8B0000"), 0x8B0000); // Dark red
                strokeColor = this.parseColor(this.plugin.getConfig().getString("colors.unclaimed-border", "#404040"), 0x404040); // Dark gray border
            }
        }
        String label = this.createMarkerLabel(point);
        if (marker == null) {
            marker = this.markerSet.createAreaMarker(markerId, label, true, center.getWorld().getName(), x, z, false);
            if (marker == null) {
                this.plugin.getLogger().warning("Failed to create area marker for " + point.getName());
                return;
            }
            if (this.plugin.getConfig().getBoolean("debug", false)) {
                this.plugin.getLogger().info("Created new marker for " + point.getName());
            }
        } else {
            marker.setCornerLocations(x, z);
            marker.setLabel(label);
        }
        marker.setFillStyle(fillOpacity, fillColor);
        marker.setLineStyle(strokeWeight, strokeOpacity, strokeColor);
        String description = CapturePointInfoWindow.formatInfoWindow(point);
        marker.setDescription(description);
        this.areaMarkers.put(markerId, marker);
    }

    private void updateCenterMarker(CapturePoint point) {
        String markerId = "center_" + point.getId();
        Marker marker = this.markerSet.findMarker(markerId);
        Location center = point.getLocation();
        String iconName = this.plugin.getConfig().getString("dynmap.center-marker.icon", "chest");
        boolean showLabel = this.plugin.getConfig().getBoolean("dynmap.center-marker.show-label", true);
        String labelFormat = this.plugin.getConfig().getString("dynmap.center-marker.label-format", "%name%");
        DynmapAPI dynmap = (DynmapAPI)this.plugin.getServer().getPluginManager().getPlugin("dynmap");
        if (dynmap == null) {
            this.plugin.getLogger().warning("Failed to get Dynmap plugin");
            return;
        }
        MarkerAPI markerAPI = dynmap.getMarkerAPI();
        if (markerAPI == null) {
            this.plugin.getLogger().warning("Failed to get MarkerAPI from Dynmap");
            return;
        }
        
        // Try multiple icon names as fallbacks for better compatibility
        MarkerIcon icon = null;
        String[] iconTryOrder = {iconName, "chest", "skull", "pointer", "signpost", "man"};
        for (String tryIcon : iconTryOrder) {
            icon = markerAPI.getMarkerIcon(tryIcon);
            if (icon != null) {
                break;
            }
        }
        
        if (icon == null) {
            this.plugin.getLogger().warning("Failed to find any valid marker icon!");
            return;
        }
        String label = labelFormat.replace("%name%", point.getName()).replace("%type%", this.plugin.getPointTypeName(point.getType())).replace("%town%", point.getControllingTown());
        if (marker == null) {
            marker = this.markerSet.createMarker(markerId, label, center.getWorld().getName(), center.getX(), center.getY(), center.getZ(), icon, false);
            if (marker == null) {
                this.plugin.getLogger().warning("Failed to create center marker for " + point.getName());
                return;
            }
        } else {
            marker.setLocation(center.getWorld().getName(), center.getX(), center.getY(), center.getZ());
            marker.setLabel(label);
            marker.setMarkerIcon(icon);
        }
        marker.setDescription(CapturePointInfoWindow.formatInfoWindow(point));
        this.centerMarkers.put(markerId, marker);
    }

    private String createMarkerLabel(CapturePoint point) {
        StringBuilder label = new StringBuilder(point.getName());
        if (point.getControllingTown() != null && !point.getControllingTown().isEmpty()) {
            label.append(" (").append(point.getControllingTown()).append(")");
        } else {
            label.append(" (Unclaimed)");
        }
        if (this.plugin.getActiveSessions().containsKey(point.getId())) {
            CaptureSession session = this.plugin.getActiveSessions().get(point.getId());
            label.append(" - Being captured by ").append(session.getTownName());
        }
        return label.toString();
    }

    private int[] getTownColors(String townName) {
        try {
            TownyAPI api = TownyAPI.getInstance();
            Town town = api.getTown(townName);
            if (town == null) {
                this.plugin.getLogger().warning("Could not find town: " + townName);
                return null;
            }

            // Get town color for fill
            Color townColor = town.getMapColor();
            if (townColor == null) {
                return null;
            }
            int fillColor = townColor.getRGB() & 0xFFFFFF;

            // Get nation color for stroke, or use town color if no nation
            int strokeColor;
            if (town.hasNation()) {
                Color nationColor = town.getNation().getMapColor();
                if (nationColor != null) {
                    strokeColor = nationColor.getRGB() & 0xFFFFFF;
                } else {
                    strokeColor = fillColor;
                }
            } else {
                strokeColor = fillColor;
            }

            return new int[]{fillColor, strokeColor};
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting town colors for " + townName + ": " + e.getMessage());
            return null;
        }
    }

    private int getColorFromName(String colorName) {
        switch (colorName.toLowerCase()) {
            case "black": {
                return 0;
            }
            case "darkblue": {
                return 170;
            }
            case "darkgreen": {
                return 43520;
            }
            case "darkaqua": {
                return 43690;
            }
            case "darkred": {
                return 0xAA0000;
            }
            case "darkpurple": {
                return 0xAA00AA;
            }
            case "gold": {
                return 0xFFAA00;
            }
            case "gray": {
                return 0xAAAAAA;
            }
            case "darkgray": {
                return 0x555555;
            }
            case "blue": {
                return 0x5555FF;
            }
            case "green": {
                return 0x55FF55;
            }
            case "aqua": {
                return 0x55FFFF;
            }
            case "red": {
                return 0xFF5555;
            }
            case "lightpurple": {
                return 0xFF55FF;
            }
            case "yellow": {
                return 0xFFFF55;
            }
            case "white": {
                return 0xFFFFFF;
            }
            case "purple": {
                return 0xAA00AA;
            }
            case "magenta": {
                return 0xFF55FF;
            }
            case "orange": {
                return 0xFFAA00;
            }
            case "brown": {
                return 0xAA5500;
            }
            case "cyan": {
                return 43690;
            }
            case "lime": {
                return 0x55FF55;
            }
            case "navy": {
                return 128;
            }
            case "pink": {
                return 16738740;
            }
            case "teal": {
                return 32896;
            }
        }
        return -1;
    }

    private int parseColor(String colorStr, int defaultColor) {
        if (colorStr == null || colorStr.isEmpty()) {
            this.plugin.getLogger().warning("Empty color string, using default: " + String.format("#%06X", defaultColor));
            return defaultColor;
        }
        try {
            if (colorStr.startsWith("#")) {
                colorStr = colorStr.substring(1);
            }
            return Integer.parseInt(colorStr, 16);
        }
        catch (NumberFormatException e) {
            this.plugin.getLogger().warning("Failed to parse color: " + colorStr + ", using default: " + String.format("#%06X", defaultColor));
            return defaultColor;
        }
    }

    public void removeMarkers(String pointId) {
        String centerMarkerId;
        Marker centerMarker;
        String areaMarkerId = "capture_" + pointId;
        AreaMarker areaMarker = this.markerSet.findAreaMarker(areaMarkerId);
        if (areaMarker != null) {
            areaMarker.deleteMarker();
            this.areaMarkers.remove(areaMarkerId);
        }
        if ((centerMarker = this.markerSet.findMarker(centerMarkerId = "center_" + pointId)) != null) {
            centerMarker.deleteMarker();
            this.centerMarkers.remove(centerMarkerId);
        }
    }

    public AreaMarker getAreaMarker(String id) {
        return this.areaMarkers.get(id);
    }

    public Marker getCenterMarker(String id) {
        return this.centerMarkers.get(id);
    }
}
