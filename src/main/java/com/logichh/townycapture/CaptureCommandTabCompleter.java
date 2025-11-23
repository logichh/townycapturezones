/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.palmergames.bukkit.towny.TownyAPI
 *  com.palmergames.bukkit.towny.object.TownyObject
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package com.logichh.townycapture;

import com.logichh.townycapture.TownyCapture;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownyObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class CaptureCommandTabCompleter
implements TabCompleter {
    private final TownyCapture plugin;

    public CaptureCommandTabCompleter(TownyCapture plugin) {
        this.plugin = plugin;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> completions = new ArrayList<String>();
        if (!(sender instanceof Player)) {
            return completions;
        }
        Player player = (Player)sender;
        if (args.length == 1) {
            ArrayList<String> subCommands = new ArrayList<String>();
            subCommands.add("help");
            subCommands.add("list");
            subCommands.add("info");
            subCommands.add("capture");
            subCommands.add("notifications");
            if (player.hasPermission("townycapture.admin")) {
                subCommands.add("admin");
            }
            if (player.hasPermission("townycapture.admin.create")) {
                subCommands.add("create");
            }
            if (player.hasPermission("townycapture.admin.stop")) {
                subCommands.add("stop");
            }
            if (player.hasPermission("townycapture.admin.delete")) {
                subCommands.add("deletezone");
            }
            if (player.hasPermission("townycapture.protectchunk")) {
                subCommands.add("protectchunk");
            }
            if (player.hasPermission("townycapture.admin.settype")) {
                subCommands.add("settype");
                subCommands.add("types");
            }
            if (player.hasPermission("townycapture.admin.forcecapture")) {
                subCommands.add("forcecapture");
            }
            if (player.hasPermission("townycapture.admin.reset")) {
                subCommands.add("reset");
                subCommands.add("resetall");
            }
            if (player.hasPermission("townycapture.admin.reload")) {
                subCommands.add("reload");
            }
            if (player.hasPermission("townycapture.admin")) {
                subCommands.add("test");
                subCommands.add("testall");
            }
            return this.filterCompletions(subCommands, args[0]);
        }
        switch (args[0].toLowerCase()) {
            case "info": {
                if (args.length != 2) break;
                return this.filterCompletions(new ArrayList<String>(this.plugin.getCapturePoints().keySet()), args[1]);
            }
            case "stop": {
                if (!player.hasPermission("townycapture.admin.stop") || args.length != 2) break;
                return this.filterCompletions(new ArrayList<String>(this.plugin.getActiveSessions().keySet()), args[1]);
            }
            case "deletezone": {
                if (!player.hasPermission("townycapture.admin.delete") || args.length != 2) break;
                return this.filterCompletions(new ArrayList<String>(this.plugin.getCapturePoints().keySet()), args[1]);
            }
            case "reset": {
                if (!player.hasPermission("townycapture.admin.reset") || args.length != 2) break;
                return this.filterCompletions(new ArrayList<String>(this.plugin.getCapturePoints().keySet()), args[1]);
            }
            case "settype": {
                if (!player.hasPermission("townycapture.admin.settype")) break;
                if (args.length == 2) {
                    return this.filterCompletions(new ArrayList<String>(this.plugin.getCapturePoints().keySet()), args[1]);
                }
                if (args.length != 3) break;
                return this.filterCompletions(new ArrayList<String>(this.plugin.getPointTypes().keySet()), args[2]);
            }
            case "forcecapture": {
                if (!player.hasPermission("townycapture.admin.forcecapture")) break;
                if (args.length == 2) {
                    return this.filterCompletions(new ArrayList<String>(this.plugin.getCapturePoints().keySet()), args[1]);
                }
                if (args.length != 3) break;
                List<String> townNames = TownyAPI.getInstance().getTowns().stream().map(TownyObject::getName).collect(Collectors.toList());
                return this.filterCompletions(townNames, args[2]);
            }
            case "create": {
                if (!player.hasPermission("townycapture.admin.create")) break;
                if (args.length == 2) {
                    return this.filterCompletions(List.of("zone1", "zone2", "zone3"), args[1]);
                }
                if (args.length == 3) {
                    return this.filterCompletions(List.of("Capital", "Resource_Point", "Military_Base"), args[2]);
                }
                if (args.length == 4) {
                    return this.filterCompletions(List.of("1", "2", "3", "4", "5"), args[3]);
                }
                if (args.length != 5) break;
                return this.filterCompletions(List.of("100", "500", "1000", "5000"), args[4]);
            }
        }
        return completions;
    }

    private List<String> filterCompletions(List<String> options, String input) {
        return options.stream().filter(option -> option.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
