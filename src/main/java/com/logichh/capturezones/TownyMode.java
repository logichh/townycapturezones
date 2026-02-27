package com.logichh.capturezones;

import java.util.Locale;

public enum TownyMode {
    AUTO,
    REQUIRED,
    DISABLED;

    public static TownyMode fromConfigValue(String value, TownyMode fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return TownyMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}

