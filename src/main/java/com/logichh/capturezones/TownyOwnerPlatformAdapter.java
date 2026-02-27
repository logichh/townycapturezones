package com.logichh.capturezones;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TownyOwnerPlatformAdapter implements OwnerPlatformAdapter {
    @Override
    public String getPlatformKey() {
        return "towny";
    }

    @Override
    public EnumSet<CaptureOwnerType> getSupportedOwnerTypes() {
        return EnumSet.of(CaptureOwnerType.PLAYER, CaptureOwnerType.TOWN, CaptureOwnerType.NATION);
    }

    @Override
    public String resolveOwnerName(Player player, CaptureOwnerType ownerType) {
        if (player == null || ownerType == null) {
            return null;
        }

        switch (ownerType) {
            case PLAYER:
                return player.getName();
            case TOWN:
                return resolveTownName(player);
            case NATION:
                return resolveNationName(player);
            default:
                return null;
        }
    }

    @Override
    public boolean doesPlayerMatchOwner(Player player, String ownerName, CaptureOwnerType ownerType) {
        if (player == null || ownerName == null || ownerType == null) {
            return false;
        }
        String resolved = resolveOwnerName(player, ownerType);
        return resolved != null && resolved.equalsIgnoreCase(ownerName.trim());
    }

    @Override
    public List<String> getAvailableOwners(CaptureOwnerType ownerType) {
        if (ownerType == null) {
            return Collections.emptyList();
        }

        if (ownerType == CaptureOwnerType.PLAYER) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }

        try {
            if (ownerType == CaptureOwnerType.TOWN) {
                List<String> names = new ArrayList<>();
                for (Town town : TownyAPI.getInstance().getTowns()) {
                    if (town != null) {
                        names.add(town.getName());
                    }
                }
                return names;
            }

            Set<String> nationNames = new HashSet<>();
            for (Town town : TownyAPI.getInstance().getTowns()) {
                if (town == null || !town.hasNation()) {
                    continue;
                }
                try {
                    nationNames.add(town.getNation().getName());
                } catch (Exception ignored) {
                    // Ignore broken nation references.
                }
            }
            return new ArrayList<>(nationNames);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    @Override
    public String normalizeOwnerName(String ownerName, CaptureOwnerType ownerType) {
        if (ownerName == null || ownerType == null) {
            return null;
        }
        String raw = ownerName.trim();
        if (raw.isEmpty()) {
            return null;
        }

        switch (ownerType) {
            case PLAYER:
                return raw;
            case TOWN:
                try {
                    Town town = TownyAPI.getInstance().getTown(raw);
                    return town != null ? town.getName() : null;
                } catch (Exception ex) {
                    return null;
                }
            case NATION:
                try {
                    for (Town town : TownyAPI.getInstance().getTowns()) {
                        if (town == null || !town.hasNation()) {
                            continue;
                        }
                        try {
                            String nationName = town.getNation().getName();
                            if (nationName.equalsIgnoreCase(raw)) {
                                return nationName;
                            }
                        } catch (Exception ignored) {
                            // Ignore broken nation entries.
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore Towny iteration failures.
                }
                return null;
            default:
                return null;
        }
    }

    @Override
    public boolean ownerExists(String ownerName, CaptureOwnerType ownerType) {
        return normalizeOwnerName(ownerName, ownerType) != null;
    }

    @Override
    public boolean depositControlReward(String ownerName, double amount, String reason, CaptureOwnerType ownerType) {
        if (ownerType != CaptureOwnerType.TOWN) {
            return false;
        }
        try {
            Town town = TownyAPI.getInstance().getTown(ownerName);
            if (town == null || town.getAccount() == null) {
                return false;
            }
            town.getAccount().deposit(amount, reason);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean depositFirstCaptureBonus(UUID playerId, double amount, String reason) {
        if (playerId == null) {
            return false;
        }
        try {
            Resident resident = TownyAPI.getInstance().getResident(playerId);
            if (resident == null || resident.getAccount() == null) {
                return false;
            }
            resident.getAccount().deposit(amount, reason);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public String resolveMapColorHex(String ownerName, CaptureOwnerType ownerType, String fallbackHex) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return fallbackHex;
        }
        try {
            if (ownerType == CaptureOwnerType.TOWN) {
                Town town = TownyAPI.getInstance().getTown(ownerName);
                if (town != null) {
                    Color color = town.getMapColor();
                    if (color != null) {
                        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                    }
                }
            } else if (ownerType == CaptureOwnerType.NATION) {
                for (Town town : TownyAPI.getInstance().getTowns()) {
                    if (town == null || !town.hasNation()) {
                        continue;
                    }
                    try {
                        if (!town.getNation().getName().equalsIgnoreCase(ownerName)) {
                            continue;
                        }
                        Color color = town.getNation().getMapColor();
                        if (color != null) {
                            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                        }
                    } catch (Exception ignored) {
                        // Ignore nation lookups that fail.
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return fallbackHex;
    }

    @Override
    public boolean isPlayerInSameTown(Player player, CaptureOwner owner) {
        if (player == null || owner == null) {
            return false;
        }
        try {
            Town playerTown = TownyAPI.getInstance().getTown(player);
            if (playerTown == null) {
                return false;
            }
            String ownerTownName = resolveOwnerTownName(owner);
            return ownerTownName != null && ownerTownName.equalsIgnoreCase(playerTown.getName());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean isPlayerInSameNation(Player player, CaptureOwner owner) {
        if (player == null || owner == null) {
            return false;
        }
        try {
            Town playerTown = TownyAPI.getInstance().getTown(player);
            if (playerTown == null || !playerTown.hasNation()) {
                return false;
            }
            String playerNationName = playerTown.getNation().getName();
            String ownerNationName = resolveOwnerNationName(owner);
            return ownerNationName != null && ownerNationName.equalsIgnoreCase(playerNationName);
        } catch (Exception ex) {
            return false;
        }
    }

    private String resolveOwnerTownName(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return null;
        }
        try {
            if (owner.getType() == CaptureOwnerType.TOWN) {
                Town town = TownyAPI.getInstance().getTown(owner.getDisplayName());
                return town != null ? town.getName() : null;
            }

            if (owner.getType() == CaptureOwnerType.PLAYER) {
                Resident resident = null;
                String id = owner.getId();
                if (id != null && !id.trim().isEmpty()) {
                    try {
                        resident = TownyAPI.getInstance().getResident(UUID.fromString(id.trim()));
                    } catch (Exception ignored) {
                        // Fallback to display-name lookup.
                    }
                }
                if (resident == null && owner.getDisplayName() != null && !owner.getDisplayName().trim().isEmpty()) {
                    resident = TownyAPI.getInstance().getResident(owner.getDisplayName().trim());
                }
                if (resident == null || !resident.hasTown()) {
                    return null;
                }
                Town town = resident.getTown();
                return town != null ? town.getName() : null;
            }
        } catch (Exception ignored) {
            // Return null below.
        }
        return null;
    }

    private String resolveOwnerNationName(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return null;
        }
        try {
            if (owner.getType() == CaptureOwnerType.NATION) {
                return owner.getDisplayName();
            }

            String ownerTownName = resolveOwnerTownName(owner);
            if (ownerTownName == null || ownerTownName.isEmpty()) {
                return null;
            }
            Town town = TownyAPI.getInstance().getTown(ownerTownName);
            if (town == null || !town.hasNation()) {
                return null;
            }
            return town.getNation().getName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveTownName(Player player) {
        try {
            Town town = TownyAPI.getInstance().getTown(player);
            return town != null ? town.getName() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveNationName(Player player) {
        try {
            Town town = TownyAPI.getInstance().getTown(player);
            if (town == null || !town.hasNation()) {
                return null;
            }
            return town.getNation().getName();
        } catch (Exception ex) {
            return null;
        }
    }
}

