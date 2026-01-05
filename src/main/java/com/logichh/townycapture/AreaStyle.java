package com.logichh.townycapture;

import java.awt.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;

public class AreaStyle {
    int strokecolor;
    double strokeopacity;
    int strokeweight;
    int fillcolor;
    double fillopacity;
    MarkerIcon homeicon;

    public AreaStyle(FileConfiguration config, String path, MarkerAPI markerAPI) {
        String strokeColorStr = config.getString(path + ".strokeColor", "#FF0000");
        String fillColorStr = config.getString(path + ".fillColor", "#FF0000");
        this.strokecolor = this.parseColor(strokeColorStr, 0xFF0000);
        this.strokeopacity = config.getDouble(path + ".strokeOpacity", 0.8);
        this.strokeweight = config.getInt(path + ".strokeWeight", 3);
        this.fillcolor = this.parseColor(fillColorStr, 0xFF0000);
        this.fillopacity = config.getDouble(path + ".fillOpacity", 0.35);
        String homeIconName = config.getString(path + ".homeicon", null);
        this.homeicon = homeIconName != null && markerAPI != null ? markerAPI.getMarkerIcon(homeIconName) : null;
    }

    private int parseColor(String colorStr, int defaultColor) {
        if (colorStr == null || colorStr.trim().isEmpty()) {
            return defaultColor;
        }
        
        colorStr = colorStr.replace("&#39;", "").replace("'", "").replace("\"", "").trim();
        
        if (colorStr.startsWith("#")) {
            try {
                return Integer.parseInt(colorStr.substring(1), 16);
            } catch (NumberFormatException e) {
                return defaultColor;
            }
        }
        
        if (colorStr.startsWith("(") && colorStr.endsWith(")")) {
            try {
                String[] rgb = colorStr.substring(1, colorStr.length() - 1).split(",");
                if (rgb.length == 3) {
                    int r = Integer.parseInt(rgb[0].trim());
                    int g = Integer.parseInt(rgb[1].trim());
                    int b = Integer.parseInt(rgb[2].trim());
                    if (r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255) {
                        return r << 16 | g << 8 | b;
                    }
                }
            } catch (NumberFormatException e) {
                // Continue to next parsing attempt
            }
        }
        
        try {
            Color color = (Color)Color.class.getField(colorStr.toUpperCase()).get(null);
            return color.getRGB() & 0xFFFFFF;
        } catch (Exception e) {
            try {
                return Integer.parseInt(colorStr, 16);
            } catch (NumberFormatException ex) {
                return defaultColor;
            }
        }
    }

    public int getStrokeColor() {
        return this.strokecolor;
    }

    public double getStrokeOpacity() {
        return this.strokeopacity;
    }

    public int getStrokeWeight() {
        return this.strokeweight;
    }

    public int getFillColor() {
        return this.fillcolor;
    }

    public double getFillOpacity() {
        return this.fillopacity;
    }

    public MarkerIcon getHomeIcon() {
        return this.homeicon;
    }
}
