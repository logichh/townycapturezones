package com.logichh.townycapture;

import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.logging.Logger;

/**
 * Dynmap implementation of the MapProvider interface.
 * Integrates capture points with Dynmap's marker system.
 */
public class DynmapProvider implements MapProvider {
    
    private final TownyCapture plugin;
    private final Logger logger;
    private DynmapAPI dynmapAPI;
    private MarkerAPI markerAPI;
    private MarkerSet markerSet;
    private AreaStyle areaStyle;
    private UpdateZones townUpdater;
    private boolean available;
    
    public DynmapProvider(TownyCapture plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.available = false;
    }
    
    @Override
    public boolean initialize() {
        try {
            Plugin dynmapPlugin = plugin.getServer().getPluginManager().getPlugin("Dynmap");
            
            if (dynmapPlugin == null || !dynmapPlugin.isEnabled()) {
                logger.info("Dynmap not found or not enabled.");
                return false;
            }
            
            this.dynmapAPI = (DynmapAPI) dynmapPlugin;
            this.markerAPI = this.dynmapAPI.getMarkerAPI();
            
            if (this.markerAPI == null) {
                logger.severe("Failed to get Dynmap MarkerAPI!");
                return false;
            }
            
            // Get or create marker set
            String markerSetId = plugin.getConfig().getString("dynmap.marker-set", "townycapture.markerset");
            String markerSetLabel = plugin.getConfig().getString("dynmap.marker-set-label", "Capture Points");
            
            this.markerSet = this.markerAPI.getMarkerSet(markerSetId);
            if (this.markerSet == null) {
                this.markerSet = this.markerAPI.createMarkerSet(markerSetId, markerSetLabel, null, false);
            }
            
            if (this.markerSet == null) {
                logger.severe("Failed to create Dynmap marker set!");
                return false;
            }
            
            // Configure marker set
            this.markerSet.setLayerPriority(10);
            this.markerSet.setHideByDefault(false);
            
            // Initialize area style
            this.areaStyle = new AreaStyle(plugin.getConfig(), "dynmap", this.markerAPI);
            
            // Create updater
            this.townUpdater = new UpdateZones(plugin, this.markerSet, this.areaStyle);
            
            this.available = true;
            logger.info("Dynmap integration initialized successfully!");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to initialize Dynmap: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
            this.available = false;
            return false;
        }
    }
    
    @Override
    public boolean isAvailable() {
        return available && markerSet != null;
    }
    
    @Override
    public String getName() {
        return "Dynmap";
    }
    
    @Override
    public void createOrUpdateMarker(CapturePoint point) {
        if (!isAvailable() || !point.isShowOnMap()) {
            return;
        }
        
        try {
            townUpdater.updateMarker(point);
        } catch (Exception e) {
            logger.warning("Failed to update Dynmap marker for " + point.getId() + ": " + e.getMessage());
        }
    }
    
    @Override
    public void removeMarker(String pointId) {
        if (!isAvailable()) {
            return;
        }
        
        try {
            Marker marker = markerSet.findMarker(pointId);
            if (marker != null) {
                marker.deleteMarker();
            }
        } catch (Exception e) {
            logger.warning("Failed to remove Dynmap marker " + pointId + ": " + e.getMessage());
        }
    }
    
    @Override
    public void updateAllMarkers() {
        if (!isAvailable()) {
            return;
        }
        
        try {
            // Clear existing markers
            for (Marker marker : markerSet.getMarkers()) {
                marker.deleteMarker();
            }
            
            // Update all capture points
            for (CapturePoint point : plugin.getCapturePoints().values()) {
                if (point.isShowOnMap()) {
                    townUpdater.updateMarker(point);
                }
            }
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Updated all Dynmap markers");
            }
        } catch (Exception e) {
            logger.severe("Failed to update Dynmap markers: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void cleanup() {
        if (markerSet != null) {
            try {
                for (Marker marker : markerSet.getMarkers()) {
                    marker.deleteMarker();
                }
            } catch (Exception e) {
                logger.warning("Error cleaning up Dynmap markers: " + e.getMessage());
            }
        }
        this.available = false;
    }
    
    @Override
    public void reload() {
        cleanup();
        initialize();
        updateAllMarkers();
    }
    
    /**
     * Get the UpdateZones instance for this provider.
     * Used for backward compatibility with existing code.
     */
    public UpdateZones getTownUpdater() {
        return townUpdater;
    }
}
