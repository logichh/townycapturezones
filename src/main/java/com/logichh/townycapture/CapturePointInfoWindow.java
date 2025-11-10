/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.palmergames.bukkit.towny.TownyAPI
 *  com.palmergames.bukkit.towny.object.Town
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.logichh.townycapture;

import com.logichh.townycapture.CapturePoint;
import com.logichh.townycapture.CaptureSession;
import com.logichh.townycapture.TownyCapture;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class CapturePointInfoWindow {
    public static String formatInfoWindow(CapturePoint point) {
        String statusColor;
        String statusText;
        TownyCapture plugin = (TownyCapture)TownyCapture.getPlugin(TownyCapture.class);
        FileConfiguration config = plugin.getConfig();
        StringBuilder infoWindow = new StringBuilder("<div class=\"regioninfo\">");
        String headerSize = config.getString("infowindow.font-size-header", "120%");
        String nameSize = config.getString("infowindow.font-size-name", "150%");
        String otherSize = config.getString("infowindow.font-size-other", "100%");
        String controllingTown = point.getControllingTown();
        boolean isActive = plugin.getActiveSessions().containsKey(point.getId());
        CaptureSession session = isActive ? plugin.getActiveSessions().get(point.getId()) : null;
        CaptureSession captureSession = session;
        if (isActive) {
            controllingTown = session.getTownName();
            statusText = config.getString("infowindow.text.capturing", "Being captured by");
            statusColor = config.getString("infowindow.colors.capturing", "#FFAA00");
            String townColor = CapturePointInfoWindow.getTownColor(controllingTown);
            if (townColor != null) {
                statusColor = townColor;
            }
        } else if (controllingTown == null || controllingTown.isEmpty()) {
            statusText = config.getString("infowindow.text.unclaimed", "Unclaimed");
            statusColor = config.getString("infowindow.colors.unclaimed", "#FF0000");
        } else {
            statusText = config.getString("infowindow.text.controlled", "Controlled by");
            statusColor = config.getString("infowindow.colors.controlled", "#00FF00");
            String townColor = CapturePointInfoWindow.getTownColor(controllingTown);
            if (townColor != null) {
                statusColor = townColor;
            }
        }
        if (controllingTown == null || controllingTown.isEmpty()) {
            infoWindow.append("<div style=\"font-size:").append(headerSize).append("%; font-weight:bold; color:").append(statusColor).append(";\">").append(statusText).append("</div>");
        } else {
            infoWindow.append("<div style=\"font-size:").append(headerSize).append("%; font-weight:bold; color:").append(statusColor).append(";\">").append(statusText).append(" ").append(controllingTown).append("</div>");
        }
        infoWindow.append("<div style=\"font-size:").append(nameSize).append("%; margin-top:5px;\">").append(point.getName()).append("</div>");
        infoWindow.append("<div style=\"font-size:").append(otherSize).append("%;\">\u2022 Type - ").append(plugin.getPointTypeName(point.getType())).append("</div>");
        infoWindow.append("<div style=\"font-size:").append(otherSize).append("%;\">\u2022 Active: ");
        if (isActive) {
            infoWindow.append("<span style=\"color:").append(statusColor).append(";\">Yes</span>");
        } else {
            infoWindow.append("<span style=\"color:#777777;\">No</span>");
        }
        infoWindow.append("</div>");
        infoWindow.append("<div style=\"font-size:").append(otherSize).append("%;\">\u2022 Preparation Duration - ").append(plugin.getConfig().getInt("preparation_time", 5)).append(" minutes</div>");
        infoWindow.append("<div style=\"font-size:").append(otherSize).append("%;\">\u2022 Capture Duration - ").append(plugin.getConfig().getInt("capture_time", 30)).append(" minutes</div>");
        infoWindow.append("<div style=\"font-size:").append(otherSize).append("%;\">\u2022 Daily Payout - ").append(point.getReward()).append(" money</div>");
        infoWindow.append("</div>");
        return infoWindow.toString();
    }

    private static String getTownColor(String townName) {
        if (townName == null || townName.isEmpty()) {
            return null;
        }
        Plugin dynmapTowny = ((TownyCapture)TownyCapture.getPlugin(TownyCapture.class)).getServer().getPluginManager().getPlugin("Dynmap-Towny");
        if (dynmapTowny == null || !dynmapTowny.isEnabled()) {
            return null;
        }
        try {
            String nationName;
            String nationStylePath;
            FileConfiguration dynmapTownyConfig = ((JavaPlugin)dynmapTowny).getConfig();
            String townStylePath = "custstyle." + townName.toLowerCase();
            if (dynmapTownyConfig.contains(townStylePath + ".fillcolor")) {
                return dynmapTownyConfig.getString(townStylePath + ".fillcolor");
            }
            TownyAPI api = TownyAPI.getInstance();
            Town town = api.getTown(townName);
            if (town != null && town.hasNation() && dynmapTownyConfig.contains((nationStylePath = "nationstyle." + (nationName = town.getNation().getName()).toLowerCase()) + ".fillcolor")) {
                return dynmapTownyConfig.getString(nationStylePath + ".fillcolor");
            }
            return dynmapTownyConfig.getString("fillcolor");
        }
        catch (Exception e) {
            return null;
        }
    }
}
