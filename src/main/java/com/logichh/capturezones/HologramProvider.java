package com.logichh.capturezones;

import org.bukkit.Location;

import java.util.List;

/**
 * Abstraction for hologram rendering backends.
 */
public interface HologramProvider {
    boolean initialize();

    boolean isAvailable();

    String getName();

    void createOrUpdate(String pointId, Location baseLocation, List<String> lines, double lineSpacing, boolean fixedOrientation);

    void remove(String pointId);

    void cleanup();
}

