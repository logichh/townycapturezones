package com.logichh.capturezones;

import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public interface OwnerPlatformAdapter {
    default String getPlatformKey() {
        return "custom";
    }

    default EnumSet<CaptureOwnerType> getSupportedOwnerTypes() {
        return EnumSet.of(CaptureOwnerType.PLAYER);
    }

    String resolveOwnerName(Player player, CaptureOwnerType ownerType);

    boolean doesPlayerMatchOwner(Player player, String ownerName, CaptureOwnerType ownerType);

    List<String> getAvailableOwners(CaptureOwnerType ownerType);

    String normalizeOwnerName(String ownerName, CaptureOwnerType ownerType);

    boolean ownerExists(String ownerName, CaptureOwnerType ownerType);

    boolean depositControlReward(String ownerName, double amount, String reason, CaptureOwnerType ownerType);

    boolean depositFirstCaptureBonus(UUID playerId, double amount, String reason);

    String resolveMapColorHex(String ownerName, CaptureOwnerType ownerType, String fallbackHex);

    boolean isPlayerInSameTown(Player player, CaptureOwner owner);

    boolean isPlayerInSameNation(Player player, CaptureOwner owner);
}

