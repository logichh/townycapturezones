package com.logichh.capturezones;

import java.util.Locale;

public enum CaptureOwnerType {
    PLAYER,
    TOWN,
    NATION;

    public static CaptureOwnerType fromConfigValue(String value, CaptureOwnerType fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return CaptureOwnerType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}

