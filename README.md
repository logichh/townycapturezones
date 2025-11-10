# TownyCapture

A Minecraft plugin that adds capturable points for towns in Towny, creating dynamic territorial control mechanics.

## Description

TownyCapture extends the Towny plugin by adding strategic capture points that towns can fight over. Players can capture zones to gain rewards and control over territory, creating engaging PvP and territorial gameplay.

## Features

- **Capture Points**: Create strategic points on the map that can be captured by towns
- **Dynamic Capture Sessions**: Real-time capturing with progress tracking
- **Rewards System**: Configure item payouts and rewards for successful captures
- **Protection Systems**: Block and zone protection during capture sessions
- **Death Handling**: Custom death listener for capture-related events
- **Time Windows**: Configure specific time windows when capture points are active
- **Dynmap Integration**: Visual representation of capture points on Dynmap
- **WorldGuard Integration**: Region protection support
- **PlaceholderAPI Support**: Use capture data in other plugins

## Dependencies

### Required
- **Towny**: Core dependency for town management

### Optional
- **Dynmap**: For map visualization
- **Dynmap-Towny**: Enhanced Dynmap integration with Towny
- **WorldGuard**: For advanced region protection
- **PlaceholderAPI**: For placeholder support

## Installation

1. Download the latest release
2. Place the `.jar` file in your server's `plugins` folder
3. Ensure Towny is installed
4. Restart your server
5. Configure the plugin in `plugins/TownyCapture/config.yml`

## Commands

### Main Command
- `/capturepoint` or `/cp` - Main command for capture points

### Subcommands
- `/cp list` - List all capture points
- `/cp info <name>` - Get information about a specific capture point
- `/cp capture <name>` - Attempt to capture a point

### Admin Commands (require `capturepoints.admin` permission)
- `/cp create <name>` - Create a new capture point
- `/cp delete <name>` - Delete a capture point
- `/cp reload` - Reload the plugin configuration
- `/cp stop <name>` - Stop an active capture session
- `/cp forcecapture <name> <town>` - Force capture a point for a town
- `/cp reset <name>` - Reset a capture point
- `/cp settype <name> <type>` - Set the type of a capture point
- `/cp setitempayout <name>` - Configure item rewards
- `/cp timewindow <name> <start> <end>` - Set active time windows
- `/cp togglechat` - Toggle capture-related chat messages

## Permissions

- `capturepoints.use` - Basic usage (default: true)
- `capturepoints.admin` - Full admin access (default: op)
- `capturepoints.admin.create` - Create capture points
- `capturepoints.admin.delete` - Delete capture points
- `capturepoints.admin.reload` - Reload configuration
- `capturepoints.protectchunk` - Protect individual chunks

## Configuration

Configuration is located in `plugins/TownyCapture/config.yml`. The config file allows you to customize:

- Capture mechanics and timings
- Reward systems
- Protection settings
- Integration with other plugins
- Messages and notifications

## Building from Source

This project uses Maven. To build:

```bash
mvn clean package
```

The compiled JAR will be in the `target` directory.

## Requirements

- Java 17 or higher
- Spigot/Paper 1.20 or higher
- Towny plugin



## Author

Logichh - Milin (Discord: milin001)
