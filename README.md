# TownyCapture

A Minecraft plugin that adds capturable points for towns in Towny, creating dynamic territorial control mechanics.

## Description

TownyCapture extends the Towny plugin by adding strategic capture points that towns can fight over. Players can capture zones to gain rewards and control over territory, creating engaging PvP and territorial gameplay.

## Dependacies

- Towny

## Optional supported plugins

- Dynmap
- Bluemap
- MythicMobs
- WorldGuard
- PlaceholderAPI
- Dynmap-Towny

## Features

- **Capture Points**: Create strategic points on the map that can be captured by towns
- **Dynamic Capture Sessions**: Real-time capturing with progress tracking
- **Discord Integration**: Real-time webhook notifications with rich embeds for all capture events (13 alert types, fully customizable)
- **Modular Zone Configurations**: Each zone can have its own config file with independent settings
- **Modular Zone Boundaries**: Particle boundaries with ON / REDUCED / OFF modes and auto-cleaned tasks
- **Rewards System**: Configure item payouts and rewards for successful captures
- **Weekly Resets + First-Capture Bonus**: Auto-neutralize points on a configurable day/time with a one-time bonus for the first recapture
- **Protection Systems**: Block and zone protection during capture sessions
- **Death Handling**: Custom death listener for capture-related events
- **Time Windows**: Configure specific time windows when capture points are active
- **Dynmap Integration**: Visual representation of capture points on Dynmap with fully customizable/localized infowindows
- **BlueMap Integration**: Alternative/additional web map integration with BlueMap (can run both simultaneously!)
- **Reinforcements**: Optimized, rate-limited defender spawns around the capturing player
- **MythicMobs Support**: Optional integration with MythicMobs for custom reinforcement mobs (fully backwards compatible)
- **WorldGuard Integration**: Region protection support
- **PlaceholderAPI Support**: Use capture data in other plugins
- **bStats Metrics**: Anonymous usage statistics collection
- **Statistics System**: Comprehensive stat tracking with interactive GUIs for players, towns, zones, and server records
- **Zone Shop System**: Per-zone player shops with economy integration, dynamic pricing, and GUI-based admin configuration (100% optional)

## Dependencies

### Required
- **Towny**: Core dependency for town management

### Optional
- **Dynmap**: For map visualization (v3.0+)
- **Dynmap-Towny**: Enhanced Dynmap integration with Towny
- **BlueMap**: Alternative web map visualization (v3.0+)
- **WorldGuard**: For advanced region protection
- **PlaceholderAPI**: For placeholder support
- **MythicMobs**: For custom reinforcement mobs (v5.0+)

## Installation

1. Download the latest release
2. Place the `.jar` file in your server's `plugins` folder
3. Ensure Towny is installed
4. Restart your server
5. Configure the plugin in `plugins/TownyCapture/config.yml`

**Note**: This plugin uses bStats for anonymous usage statistics. You can opt-out in the `plugins/bStats/config.yml` file if desired.

## Commands

### Main Command
- `/capturepoint` (alias `/cap`) - Main command for capture points

### Subcommands
- `/cap list` - List all capture points
- `/cap info <name>` - Get information about a specific capture point
- `/cap capture <name>` - Attempt to capture a point
- `/cap stats` - Open interactive statistics GUI (5-minute cooldown)

### Admin Commands (require `capturepoints.admin` permission)
- `/cap create <name>` - Create a new capture point
- `/cap deletezone <name> [CONFIRM]` - Delete a capture point (requires explicit CONFIRM)
- `/cap reload` - Reload the plugin configuration
- `/cap stop <name>` - Stop an active capture session
- `/cap forcecapture <name> <town>` - Force capture a point for a town
- `/cap reset <name>` - Reset a capture point
- `/cap settype <name> <type>` - Set the type of a capture point
- `/cap setitempayout <name>` - Configure item rewards
- `/cap timewindow <name> <start> <end>` - Set active time windows
- `/cap togglechat` - Toggle capture-related chat messages
- `/cap stats remove <player>` - Remove a player's statistics
- `/cap stats reset CONFIRM` - Reset all server statistics (requires CONFIRM)
- `/cap shop edit <zone>` - Open shop editor GUI
- `/cap shop reload <zone>` - Reload shop configuration
- `/cap shop restock <zone>` - Manually restock shop
- `/cap shop enable <zone>` - Enable zone shop
- `/cap shop disable <zone>` - Disable zone shop

## Permissions

- `capturepoints.use` - Basic usage (default: true)
- `capturepoints.admin` - Full admin access (default: op)
- `capturepoints.admin.create` - Create capture points
- `capturepoints.admin.delete` - Delete capture points
- `capturepoints.admin.reload` - Reload configuration
- `capturepoints.admin.stats` - Manage statistics (remove/reset)
- `capturepoints.admin.stats.nocooldown` - Bypass stats command cooldown
- `capturepoints.admin.shop` - Manage zone shops (edit, enable, disable)
- `capturepoints.admin.shop.restock` - Manually restock zone shops
- `capturepoints.shop.use` - Access and use zone shops (default: true)
- `capturepoints.protectchunk` - Protect individual chunks

## Configuration

Configuration is located in `plugins/TownyCapture/config.yml`. The config file allows you to customize:

- **Discord webhooks** - Real-time notifications with 13 individually toggleable alert types
- Capture mechanics and timings
- Reward systems (daily / hourly, STATIC or DYNAMIC ranges)
- Protection settings
- Integration with other plugins
- Messages and notifications
- Boundary visualization modes (ON / REDUCED / OFF)
- Reinforcement spawn rate (`reinforcements.spawn-rate.max-per-tick`)
- MythicMobs integration (`reinforcements.mythicmobs.enabled`)
- Web map integrations (Dynmap and/or BlueMap via `dynmap.enabled` and `bluemap.enabled`)
- Weekly resets (`weekly-reset.*`) and first-capture bonus amounts
- Dynmap infowindow template and placeholders (fully localizable)
- Per-zone configurations via `zone-defaults` template

Key set# Discord webhook integration
discord:
  enabled: false
  webhook-url: "https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN"
  use-embeds: true  # Rich colored embeds vs plain text
  mention-role: ""  # Optional: <@&ROLE_ID> to ping a role
  show-coordinates: true
  show-rewards-amount: true
  rate-limit-ms: 1000  # Per-zone rate limiting
  alerts:
    capture-started: true
    capture-completed: true
    capture-failed: true
    capture-cancelled: true
    rewards-distributed: true
    first-capture-bonus: true
    reinforcement-phases: false  # Can be spammy
    player-death: true
    zone-created: true
    zone-deleted: true
    weekly-reset: true
    new-records: false
    milestones: false

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

reinforcements:
  mythicmobs:
    enabled: false  # Enable to use MythicMobs instead of vanilla mobs
    mob-types:      # List of MythicMobs internal names
      - "SkeletonKnight"
      - "ZombieWarrior"
      - "CustomGuard"
    mob-level: 1.0  # Level affects mob stats

dynmap:
  enabled: true  # Enable/disable Dynmap integration
  marker-set: townycapture.markerset
  marker-set-label: Capture Points

# Zone shop system (100% optional)
shops:
  enabled: false  # Global toggle
  defaults:
    access-mode: "ALWAYS"        # Who can access: ALWAYS / CONTROLLED_ONLY / OWNER_ONLY
    stock-system: "INFINITE"     # Stock behavior: INFINITE / LIMITED / FINITE
    layout-mode: "SINGLE_PAGE"   # Organization: SINGLE_PAGE / CATEGORIES / PAGINATED
    pricing-mode: "FIXED"        # Pricing: FIXED / DYNAMIC
    restock-schedule: "HOURLY"   # When to restock: HOURLY / DAILY / WEEKLY / MANUAL
  dynamic-pricing:
    sensitivity: 0.1              # Price variation (0.1 = 10%)
    min-multiplier: 0.5           # Minimum price (50% of base)
    max-multiplier: 2.0           # Maximum price (200% of base)
```

## Zone Shop System

TownyCapture includes an optional per-zone shop system with full economy integration and dynamic pricing.

### Features
- **Per-Zone Configuration**: Each zone can have its own independent shop
- **GUI-Based Setup**: Admin editor for visual shop configuration
- **Buy & Sell**: Support for both purchasing and selling items
- **Quantity Selectors**: Choose from 1, 2, 3, 5, 10, 16, 32, or 64 items
- **Visual Feedback**: Green panes for buy actions, red panes for sell actions
- **Dynamic Pricing**: Prices adjust based on stock levels, demand, and time
- **Stock Management**: Configure unlimited, limited, or finite stock per item
- **Access Control**: Restrict shop access (everyone, controlling town, or owner only)
- **Economy Integration**: Uses Towny account system (same as rewards)
- **Transaction Tracking**: View total buys, sells, and revenue per shop

### Setup
1. **Enable in config**: Set `shops.enabled: true` in config.yml
2. **Create a shop**: Use `/cap shop edit <zone_id>` to open the editor
3. **Configure settings**: Click settings icon to adjust access mode, stock system, pricing mode, etc.
4. **Add items**: Drop items into the shop inventory slots
5. **Configure items**: Click items to set buy/sell prices, stock limits, and categories
6. **Enable shop**: Toggle the shop enabled in the main menu
7. **Players access**: Players use `/cap shop` while inside a zone

### Configuration Dimensions
Each shop can be configured independently across 5 dimensions:

1. **Access Mode**
   - `ALWAYS` - Anyone can use the shop
   - `CONTROLLED_ONLY` - Only players from the controlling town
   - `OWNER_ONLY` - Only the town that owns the zone

2. **Stock System**
   - `INFINITE` - Items never run out
   - `LIMITED` - Items restock on a schedule
   - `FINITE` - Items never restock (one-time stock)

3. **Layout Mode**
   - `SINGLE_PAGE` - All items in one inventory (54 slots max)
   - `CATEGORIES` - Items organized by category
   - `PAGINATED` - Multiple pages of items

4. **Pricing Mode**
   - `FIXED` - Prices never change
   - `DYNAMIC` - Prices adjust based on supply and demand

5. **Restock Schedule** (only for LIMITED stock system)
   - `HOURLY` - Restock every hour
   - `DAILY` - Restock every 24 hours
   - `WEEKLY` - Restock every 7 days
   - `MANUAL` - Admin must manually restock with `/cap shop restock`

### Dynamic Pricing
When pricing mode is set to `DYNAMIC`, prices adjust automatically based on:
- **Stock Level**: Low stock increases prices, high stock decreases them
- **Transaction Frequency**: High demand increases prices
- **Time Decay**: Prices gradually return to base (10% per day)
- **Clamping**: Prices constrained to configured min/max multipliers (default: 0.5x to 2.0x)

### Admin Commands
- `/cap shop edit <zone>` - Open visual shop editor
- `/cap shop enable <zone>` - Enable shop for zone
- `/cap shop disable <zone>` - Disable shop for zone
- `/cap shop reload <zone>` - Reload shop configuration
- `/cap shop restock <zone>` - Manually restock all items

### Player Commands
- `/cap shop` - Open shop for nearest zone (must be inside zone)

```yaml
Discord Integration

TownyCapture can send real-time notifications to Discord via webhooks for all major plugin events.

### Setup
1. **Create a webhook** in your Discord server:
   - Server Settings → Integrations → Webhooks → New Webhook
   - Copy the webhook URL
2. **Configure TownyCapture**:
   - Edit `plugins/TownyCapture/config.yml`
   - Set `discord.enabled: true`
   - Paste your webhook URL in `discord.webhook-url`
3. **Customize alerts**: Enable/disable individual alert types in the `discord.alerts` section
4. **Reload**: Run `/cap reload` to apply changes

### Alert Types
All 13 alert types can be individually enabled/disabled:
- **capture-started** 🟡 - Town begins capturing a zone
- **capture-completed** 🟢 - Capture succeeds (includes time)
- **capture-failed** 🔴 - Capture fails (includes reason)
- **capture-cancelled** 🟠 - Admin cancels capture
- **rewards-distributed** 🟡 - Daily/hourly rewards given
- **first-capture-bonus** 🟤 - Post-reset bonus awarded
- **reinforcement-phases** 🔴 - Defenders spawn (disabled by default - can spam)
- **player-death** ⚫ - Player dies during capture
- **zone-created** 🔵 - Admin creates new zone
- **zone-deleted** ⚫ - Admin deletes zone
- **weekly-reset** 🟣 - Weekly neutralization event
- **new-records** 🟡 - Server records broken (disabled by default)
- **milestones** 🟣 - Capture milestones reached (disabled by default)

### Features
- **Rich Embeds**: Color-coded with structured fields and timestamps
- **Plain Text Mode**: Fallback if embeds disabled
- **Privacy Controls**: Hide coordinates and/or reward amounts
- **Role Mentions**: Optional Discord role pings for important events
- **Rate Limiting**: Per-zone spam prevention (default 1000ms)
- **Async Execution**: Won't lag your server

### Example Messages
**Capture Started:**
```
⚔️ Capture Started!
MyTown has started capturing Strategic Point!

Zone: strategic_point_1
Town: MyTown
Location: x: 123, y: 64, z: -456
```

**Rewards Distributed:**
```
💰 Rewards Distributed
MyTown received hourly rewards for controlling Strategic Point

Zone: strategic_point_1
Town: MyTown
Amount: $41.67
Type: hourly
```

## 
bluemap:
  enabled: true  # Enable/disable BlueMap integration
  marker-set: townycapture
  marker-set-label: Capture Points
  colors:
    unclaimed: '#808080'
    controlled: '#8B0000'
    capturing: '#FFA500'

statistics:
  enabled: true
  auto-save-interval: 300  # Save stats every 5 minutes
  command-cooldown: 300    # 5-minute cooldown on /cap stats

dynmap:
  infowindow:
    template: "<div class=\"regioninfo\">...</div>" # fully editable HTML-like template
```

The Dynmap infowindow supports placeholders such as `%control_status%`, `%controlling_town%`, `%name%`, `%type%`, `%reward%`, `%active_status%`, `%label_*%`, and `%item_payout%`, and all labels/status text can be translated in `lang/*.json`.

## Per-Zone Configurations

**New in v1.0.11**: Each capture zone can now have its own dedicated configuration file!

### Overview
- **Global Config**: `config.yml` contains plugin-wide settings and the `zone-defaults` template
- **Zone Configs**: Each zone gets its own `{zone_id}_config.yml` with complete settings
- **Auto-Generation**: Zone configs are automatically created when you create a zone
- **Migration**: Existing zones are automatically migrated on first load

### How It Works
1. When you create a zone with `/cap create`, the plugin generates `{zone_id}_config.yml` from the `zone-defaults` template in `config.yml`
2. Each zone config contains all zone-specific settings: rewards, reinforcements, capture mechanics, etc.
3. Modify a zone config to customize that zone without affecting others
4. If a zone config is missing, the plugin falls back to `zone-defaults`

### Zone Config Structure
Zone configs contain:
- `rewards.*` - Reward type, hourly mode, dynamic ranges
- `reinforcements.*` - Mob spawning, types, limits
- `capture.*` - Timing, progress requirements (if added in future)
- `protection.*` - Zone protection settings (if added in future)
- Other zone-specific mechanics

### Admin Commands
- `/cap zoneconfig <zone_id> set <path> <value>` - Set a zone-specific value
  - Example: `/cap zoneconfig zone1 set rewards.hourly-mode DYNAMIC`
- `/cap zoneconfig <zone_id> reset [path]` - Reset to defaults (entire config or specific path)
  - Example: `/cap zoneconfig zone1 reset rewards.hourly-mode`
  - Example: `/cap zoneconfig zone1 reset` (resets all settings)
- `/cap zoneconfig <zone_id> reload` - Reload zone config from disk

### Example Use Cases
- Make one zone have higher rewards: edit `zone1_config.yml` → `rewards.hourly-dynamic.max: 500`
- Disable reinforcements for a peaceful zone: `/cap zoneconfig peaceful_zone set reinforcements.enabled false`
- Create a hard-mode zone with more mobs: `/cap zoneconfig hardcore set reinforcements.mobs-per-wave 5`

## Statistics System

TownyCapture includes a comprehensive statistics tracking system:

### Categories
- **Captures**: Top towns and players by total captures, success rates
- **Combat**: Player kills, deaths, K/D ratios within capture zones
- **Control**: Hold times, simultaneous zones controlled
- **Economy**: Total rewards earned by towns
- **Activity**: Mob kills, participation rates
- **Records**: Server-wide records (fastest/longest captures, most captured zones, etc.)

### Features
- Interactive GUI with 54-slot menus and pagination
- Public statistics visible to all players
- On-demand calculation (no caching overhead)
- 5-minute cooldown to prevent spam (admins can bypass)
- Admin commands to remove player stats or reset all stats
- Auto-save every 5 minutes (configurable)
- JSON-based persistence

### Usage
- `/cap stats` - Open the main statistics menu
- Click categories to view detailed leaderboards
- Admins can use `/cap stats remove <player>` or `/cap stats reset CONFIRM`

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

## Support

Discord support server: https://discord.gg/t96nrf7Nav
Support my work on patreon: https://www.patreon.com/cw/logich
Become a github sponsor to show support: https://github.com/sponsors/logichh
Visit wiki for more help: https://github.com/logichh/townycapturezones/wiki
