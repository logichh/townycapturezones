# TownyCapture

A Minecraft plugin that adds capturable points for towns in Towny, creating dynamic territorial control mechanics.

## Description

TownyCapture extends the Towny plugin by adding strategic capture points that towns can fight over. Players can capture zones to gain rewards and control over territory, creating engaging PvP and territorial gameplay.

## Features

- **Capture Points**: Create strategic points on the map that can be captured by towns
- **Dynamic Capture Sessions**: Real-time capturing with progress tracking
- **Modular Zone Boundaries**: Particle boundaries with ON / REDUCED / OFF modes and auto-cleaned tasks
- **Rewards System**: Configure item payouts and rewards for successful captures
- **Weekly Resets + First-Capture Bonus**: Auto-neutralize points on a configurable day/time with a one-time bonus for the first recapture
- **Protection Systems**: Block and zone protection during capture sessions
- **Death Handling**: Custom death listener for capture-related events
- **Time Windows**: Configure specific time windows when capture points are active
- **Dynmap Integration**: Visual representation of capture points on Dynmap with fully customizable/localized infowindows
- **Reinforcements**: Optimized, rate-limited defender spawns around the capturing player
- **WorldGuard Integration**: Region protection support
- **PlaceholderAPI Support**: Use capture data in other plugins
- **bStats Metrics**: Anonymous usage statistics collection

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

**Note**: This plugin uses bStats for anonymous usage statistics. You can opt-out in the `plugins/bStats/config.yml` file if desired.

## Commands

### Main Command
- `/capturepoint` or `/cp` - Main command for capture points

### Subcommands
- `/cp list` - List all capture points
- `/cp info <name>` - Get information about a specific capture point
- `/cp capture <name>` - Attempt to capture a point

### Admin Commands (require `capturepoints.admin` permission)
- `/cp create <name>` - Create a new capture point
- `/cp deletezone <name> [CONFIRM]` - Delete a capture point (requires explicit CONFIRM)
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
- Reward systems (daily / hourly, STATIC or DYNAMIC ranges)
- Protection settings
- Integration with other plugins
- Messages and notifications
- Boundary visualization modes (ON / REDUCED / OFF)
- Reinforcement spawn rate (`reinforcements.spawn-rate.max-per-tick`)
- Weekly resets (`weekly-reset.*`) and first-capture bonus amounts
- Dynmap infowindow template and placeholders (fully localizable)

Key new settings (excerpt):

```yaml
boundary-visuals:
  mode: "ON" # ON, REDUCED, OFF

rewards:
  reward-type: "hourly"
  hourly-mode: "DYNAMIC" # or STATIC
  hourly-dynamic:
    min: 50
    max: 100

weekly-reset:
  enabled: true
  day: FRIDAY
  time: "00:00"
  first-capture-bonus:
    enabled: true
    amount-range: { min: 500, max: 1500 }

dynmap:
  infowindow:
    template: "<div class=\"regioninfo\">...</div>" # fully editable HTML-like template
```

The Dynmap infowindow supports placeholders such as `%control_status%`, `%controlling_town%`, `%name%`, `%type%`, `%reward%`, `%active_status%`, `%label_*%`, and `%item_payout%`, and all labels/status text can be translated in `lang/*.json`.

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
