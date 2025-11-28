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
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class CapturePointInfoWindow {
    public static String formatInfoWindow(CapturePoint point) {
        TownyCapture plugin = (TownyCapture)TownyCapture.getPlugin(TownyCapture.class);
        FileConfiguration config = plugin.getConfig();
        String controllingTown = point.getControllingTown();
        boolean isActive = plugin.getActiveSessions().containsKey(point.getId());
        CaptureSession session = isActive ? plugin.getActiveSessions().get(point.getId()) : null;
        CaptureSession captureSession = session;
        String statusText;
        String statusColor;
        if (isActive) {
            controllingTown = session.getTownName();
            statusText = com.logichh.townycapture.Messages.get("dynmap.infowindow.status.capturing");
            statusColor = config.getString("infowindow.colors.capturing", "#FFAA00");
            String townColor = CapturePointInfoWindow.getTownColor(controllingTown);
            if (townColor != null) {
                statusColor = townColor;
            }
        } else if (controllingTown == null || controllingTown.isEmpty()) {
            statusText = com.logichh.townycapture.Messages.get("dynmap.infowindow.status.unclaimed");
            statusColor = config.getString("infowindow.colors.unclaimed", "#FF0000");
        } else {
            statusText = com.logichh.townycapture.Messages.get("dynmap.infowindow.status.controlled");
            statusColor = config.getString("infowindow.colors.controlled", "#00FF00");
            String townColor = CapturePointInfoWindow.getTownColor(controllingTown);
            if (townColor != null) {
                statusColor = townColor;
            }
        }

        String template = config.getString("dynmap.infowindow.template");
        if (template == null || template.isEmpty()) {
            template = config.getString("infowindow.template", com.logichh.townycapture.Messages.get("dynmap.infowindow.template"));
        }
        if (template == null || template.isEmpty()) {
            template = "<div class=\"regioninfo\"><div style=\"font-size:120%; font-weight:bold; color:%control_color%;\">%control_status% %controlling_town%</div><div style=\"font-size:115%; margin-top:5px;\">%name%</div><div>\u2022 %label_type% - %type%</div><div>\u2022 %label_active%: %active_status%</div><div>\u2022 %label_prep_time% - %prep_time% minutes</div><div>\u2022 %label_capture_time% - %capture_time% minutes</div><div>\u2022 %label_reward% - %reward%</div></div>";
        }

        Map<String, String> replacements = new HashMap<>();
        replacements.put("%control_status%", statusText);
        replacements.put("%controlling_town%", controllingTown == null ? "" : controllingTown);
        replacements.put("%name%", point.getName());
        replacements.put("%type%", plugin.getPointTypeName(point.getType()));
        replacements.put("%active_status%", isActive ? com.logichh.townycapture.Messages.get("dynmap.infowindow.active.yes") : com.logichh.townycapture.Messages.get("dynmap.infowindow.active.no"));
        replacements.put("%prep_time%", String.valueOf(plugin.getConfig().getInt("preparation_time", 5)));
        replacements.put("%capture_time%", String.valueOf(plugin.getConfig().getInt("capture_time", 30)));
        replacements.put("%reward%", String.valueOf(point.getReward()));
        replacements.put("%item_payout%", plugin.getConfig().getString("dynmap.infowindow.item-payout-placeholder", "None"));
        replacements.put("%control_color%", statusColor);
        replacements.put("%label_type%", com.logichh.townycapture.Messages.get("dynmap.infowindow.label.type"));
        replacements.put("%label_active%", com.logichh.townycapture.Messages.get("dynmap.infowindow.label.active"));
        replacements.put("%label_prep_time%", com.logichh.townycapture.Messages.get("dynmap.infowindow.label.prep_time"));
        replacements.put("%label_capture_time%", com.logichh.townycapture.Messages.get("dynmap.infowindow.label.capture_time"));
        replacements.put("%label_reward%", com.logichh.townycapture.Messages.get("dynmap.infowindow.label.reward"));
        replacements.put("%label_item_reward%", com.logichh.townycapture.Messages.get("dynmap.infowindow.label.item_reward"));

        return applyPlaceholders(template, replacements);
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

    private static String applyPlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
