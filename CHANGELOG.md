# TownyCapture - Change Log

## Version 1.1.0 - [2026-01-16]

### Wiki

- Updated wiki

### Added

#### 🔔 Discord Webhook Integration
Real-time Discord notifications for all capture zone events with rich embeds and full customization!

- **13 Alert Types**: capture-started, capture-completed, capture-failed, capture-cancelled, rewards-distributed, first-capture-bonus, reinforcement-phases, player-death, zone-created, zone-deleted, weekly-reset, new-records, milestones
- **Rich Embeds**: Color-coded embeds with structured fields, titles, and footers
- **Individual Toggles**: Enable/disable each alert type independently
- **Privacy Controls**: Hide coordinates and/or reward amounts in messages
- **Role Mentions**: Optional Discord role pings for important events
- **Per-Zone Rate Limiting**: Prevents spam from same zone (configurable ms)
- **Async Execution**: Non-blocking HTTP requests won't lag server
- **Fallback Mode**: Plain text messages if embeds disabled
- **Configuration**: Complete control via `discord:` section in config.yml

**Alert Colors:**
- 🟡 Yellow - Capture started
- 🟢 Green - Capture completed
- 🔴 Red - Capture failed, reinforcements
- 🟠 Orange - Capture cancelled
- 🟡 Gold - Rewards distributed
- 🟤 Dark Orange - First capture bonus
- ⚫ Dark Gray - Player deaths
- 🔵 Cyan - Zone created
- ⚫ Gray - Zone deleted
- 🟣 Magenta - Weekly reset
- 🟡 Goldenrod - New records
- 🟣 Pink - Milestones

#### 📊 Modular Per-Zone Configurations
Each capture zone can now have its own dedicated config file!

- **Auto-Generation**: Zone configs automatically created as `{zone_id}_config.yml` when zones are created
- **Zone-Specific Settings**: Customize rewards, reinforcements, and mechanics per zone
- **Global Defaults**: `zone-template.yml` contains template for new zones
- **Automatic Migration**: Existing zones get configs generated on first load
- **Fallback System**: Uses zone-defaults if zone config is missing or invalid
- **Backup on Delete**: Zone deletion creates `{zone_id}_config.yml.backup`
- **ZoneConfigManager**: New manager class handles lifecycle and access

#### 📈 Comprehensive Statistics System
Track and visualize all capture zone activity!

- **Interactive GUI**: 54-slot menus with category-based navigation
- **6 Categories**: Captures, Combat, Control, Economy, Activity, Records
- **Player Stats**: Captures, kills, deaths, K/D ratio, mob kills, streaks
- **Town Stats**: Total captures, hold time, rewards earned, simultaneous zones
- **Zone Stats**: Captures, control time, deaths, fastest/longest captures
- **Server Records**: All-time bests, milestones, first capture tracking
- **Leaderboards**: Top 10 rankings in each category with pagination
- **Real-Time Tracking**: Capture events, combat, rewards, all logged automatically
- **JSON Persistence**: Auto-save every 5 minutes (configurable)
- **5-Minute Cooldown**: Prevents spam on `/cap stats` command (admins bypass)
- **Admin Tools**: Remove player stats or reset all statistics

#### 🗺️ BlueMap Integration
Full support for BlueMap as an alternative to Dynmap!

- Run Dynmap, BlueMap, both, or neither
- 100% optional - works without any map plugin installed
- Automatic marker synchronization across both maps
- Configurable styles and colors per map provider
- Independent enable/disable controls for each map plugin

#### 🎭 MythicMobs Integration
Full support for MythicMobs custom mobs as reinforcements!

- 100% optional - vanilla mobs works without MythicMobs installed
- Configure custom mob types in <zone-id>.config.yml
- Set mob levels for difficulty scaling
- Seamless integration with existing reinforcement system

#### 🛒 Zone Shop System
Per-zone player shops with full economy integration and dynamic pricing!

- **100% Optional**: Global toggle in config.yml (`shops.enabled: false` by default)
- **Per-Zone Shops**: Each zone can have its own independent shop
- **GUI-Based Admin Editor**: No complex commands - configure shops visually with GUIs
- **Buy & Sell**: Support for both purchase and sale of items
- **Quantity Selectors**: Visual selection (1, 2, 3, 5, 10, 16, 32, 64 items)
- **5 Configuration Dimensions** per shop:
  - **Access Mode**: ALWAYS / CONTROLLED_ONLY / OWNER_ONLY (who can access)
  - **Stock System**: INFINITE / LIMITED / FINITE (stock behavior)
  - **Layout Mode**: SINGLE_PAGE / CATEGORIES / PAGINATED (organization)
  - **Pricing Mode**: FIXED / DYNAMIC (price behavior)
  - **Restock Schedule**: HOURLY / DAILY / WEEKLY / MANUAL (when stock replenishes)
- **Dynamic Pricing**: Multi-factor algorithm (stock level + demand + time decay)
- **Stock Management**: Configure max stock per item, auto-restock on schedule
- **Category Organization**: Group items for easier browsing
- **Economy Integration**: Uses Towny account system (same as rewards)
- **Transaction Tracking**: Per-shop statistics (total buys, sells, revenue)
- **Visual Feedback**: Green panes for buy, red panes for sell
- **Persistence**: Each shop saved as `{zone_id}_shop.yml`

**Shop Features:**
- Player-facing shop GUI with category navigation
- Admin editor with visual settings panels
- Per-item configuration (buyable/sellable, prices, stock limits, categories)
- Real-time price display with total cost calculation
- Stock availability indicators
- Manual restock capability for admins

**Dynamic Pricing Algorithm:**
- Factor 1: Stock level (low stock = higher prices)
- Factor 2: Transaction frequency (high demand = higher prices)
- Factor 3: Time-based decay (gradual return to base prices)
- Configurable sensitivity and min/max multipliers (default: 0.5x to 2.0x)

### Commands Added
- `/cap zoneconfig <zone_id> set <path> <value>` - Set zone-specific config value
- `/cap zoneconfig <zone_id> reset [path]` - Reset zone config to defaults
- `/cap zoneconfig <zone_id> reload` - Reload zone config from disk
- `/cap stats` - Open interactive statistics GUI
- `/cap stats remove <player>` - Remove a player's statistics (admin)
- `/cap stats reset CONFIRM` - Reset all server statistics (admin)
- `/cap shop` - Open nearest zone shop (player)
- `/cap shop edit <zone_id>` - Open shop editor GUI (admin)
- `/cap shop reload <zone_id>` - Reload shop configuration (admin)
- `/cap shop restock <zone_id>` - Manually restock shop (admin)
- `/cap shop enable <zone_id>` - Enable shop for zone (admin)
- `/cap shop disable <zone_id>` - Disable shop for zone (admin)

### Permissions Added
- `capturepoints.admin.zoneconfig` - Access zone config commands
- `capturepoints.admin.zoneconfig.set` - Set zone config values
- `capturepoints.admin.zoneconfig.reset` - Reset zone configs
- `capturepoints.admin.zoneconfig.reload` - Reload zone configs
- `capturepoints.admin.stats` - Manage statistics (remove/reset)
- `capturepoints.admin.stats.nocooldown` - Bypass stats command cooldown
- `capturepoints.shop.use` - Access and use zone shops (default: true)
- `capturepoints.admin.shop` - Manage zone shops (edit, enable, disable, reload)
- `capturepoints.admin.shop.restock` - Manually restock zone shops

### Configuration Added
```yaml
# NEW: zone-defaults section in config.yml
zone-defaults:
  rewards:
    hourly-mode: STATIC
    hourly-dynamic:
      min: 50
      max: 100
  reinforcements:
    enabled: true
    mobs-per-wave: 1
    max-mobs-per-point: 50

statistics:
  enabled: true
  auto-save-interval: 300  # Save stats every 5 minutes
  command-cooldown: 300    # 5-minute cooldown on /cap stats

# NEW: Shop system configuration
shops:
  enabled: false  # Global toggle - 100% optional
  defaults:
    access-mode: "ALWAYS"        # ALWAYS / CONTROLLED_ONLY / OWNER_ONLY
    stock-system: "INFINITE"     # INFINITE / LIMITED / FINITE
    layout-mode: "SINGLE_PAGE"   # SINGLE_PAGE / CATEGORIES / PAGINATED
    pricing-mode: "FIXED"        # FIXED / DYNAMIC
    restock-schedule: "HOURLY"   # HOURLY / DAILY / WEEKLY / MANUAL
  dynamic-pricing:
    sensitivity: 0.1              # Price variation sensitivity
    min-multiplier: 0.5           # Minimum price (50% of base)
    max-multiplier: 2.0           # Maximum price (200% of base)
```

### Technical Implementation
- `ZoneConfigManager.java` - Manages per-zone config files with fallback to zone-defaults
- Refactored reward calculation to use zone-specific settings (`calculateHourlyReward`)
- Refactored reinforcement spawning to use zone configs (`ReinforcementListener`)
- Zone creation auto-generates config from zone-defaults template
- Zone deletion backs up config as `.backup` file
- Added `getZoneConfigManager()` accessor to `TownyCapture`
- Updated `CaptureCommands` with zone config admin command routing
- Enhanced `CaptureCommandTabCompleter` with zone config tab completion
- `StatisticsData.java` - Comprehensive data models (PlayerStats, TownStats, ZoneStats, ServerRecords)
- `StatisticsManager.java` - Core tracking, persistence, and query engine
- `StatisticsGUI.java` - Interactive menu system with category navigation
- `StatisticsGUIListener.java` - Inventory click and close event handling
- `StatisticsTrackingListener.java` - Combat tracking within capture zones
- Integrated tracking hooks in capture lifecycle (start, complete, fail)
- Integrated reward distribution tracking (hourly and one-time)
- On-demand calculation with HashMap-based storage for efficiency
- `ShopItemConfig.java` - Item configuration with stock management, pricing state, and YAML persistence
- `ShopData.java` - Shop data container with 5 configuration enums and complete YAML serialization
- `DynamicPricing.java` - Multi-factor pricing engine with stock, demand, and time decay algorithms
- `ShopManager.java` - Core shop logic with transaction processing, access control, Towny economy integration, auto-restock tasks
- `ShopGUI.java` - Player shop browsing interface with quantity selectors and visual feedback (318 lines)
- `ShopEditorGUI.java` - Admin configuration GUI with visual settings panels and item placement (413 lines)
- `ShopListener.java` - GUI click event handling for both player and admin interfaces (305 lines)
- Integrated shop initialization and cleanup in `TownyCapture` onEnable/onDisable
- Shop data persisted as `{zone_id}_shop.yml` in `plugins/TownyCapture/shops/` directory
- Periodic tasks: auto-restock every 5 minutes, dynamic pricing updates every hour

### Localization
Added messages to `lang/en.json`:
- Discord webhook notification messages and labels
- Zone config command usage and error messages
- Statistics GUI labels and navigation text
- BlueMap integration messages
- MythicMobs integration messages
- Error messages for disabled features and cooldowns
- Success messages for admin operations
- Shop GUI titles and navigation labels (10 messages)
- Shop transaction messages (buy/sell success, 2 messages)
- Shop management messages (restocked, saved, enabled, disabled, reloaded, 5 messages)
- Shop error messages (11 messages for access, stock, funds, etc.)
- Shop command usage messages (5 messages)

## Version 1.0.8 - [2025-12-02]

### Fixed
- **Critical**: Fixed post-deletion capture completion spam. Deleting a zone during an active capture session no longer triggers "no town was found" warnings or completion callbacks.
- Added defensive guards in `completeCapture()` to prevent execution if the capture point has been removed from the registry.
- Zone deletion and capture stopping now properly cancel all scheduled tasks (preparation/capture timers) and remove boss bars to ensure clean state.

### Added
- **Startup Banner**: Added ASCII art banner displaying "TOWNY CAPTURE" on server startup with version and author information.

## Version 1.0.7 - [2025]

### Changed
- Towns can no longer start a capture on zones they already control (configurable via `capture-conditions.prevent-self-capture`).
- Added per-zone capture cooldown (default 5 minutes) between any successful captures, configurable under `capture-conditions.capture-cooldown`.
- Successful captures now stamp the last capturing town and timestamp for accurate cooldown enforcement without blocking admin resets/cancellations.

## Version 1.0.6 - [2025]

### Added (Boundary visualization, weekly resets are modular)
- Modular boundary visualization modes (ON / REDUCED / OFF) with safe task cleanup.
- Reinforcement optimization: per-tick spawn limiter, queued spawns around the capturing player, smarter targeting, and cleanup on cancel.
- Dynmap infowindow now fully localizable and template-configurable (HTML-friendly placeholders, item payout placeholder).
- Weekly reset system (configurable day/time) that neutralizes points and flags a configurable first-capture bonus.
- Hourly reward mode selector (STATIC vs DYNAMIC range) alongside existing daily/hourly scheduling.

### Fixed/Changed
- /capturepoint deletezone now requires an explicit CONFIRM and actually removes zones from saved data.
- Capture point saving no longer skips empty datasets, preventing deleted zones from respawning.
- Boundary auto-visualization respects config modes and cancels tasks when disabled.

## Version 1.0.2 - [2025]

### ✨ New Features

#### 💰 Configurable Reward System
A flexible reward distribution system that allows server owners to choose between daily or hourly reward payouts, with full customization options.

**Reward Types:**
- **Daily Mode**: Traditional system - towns receive full daily reward once per Towny day
- **Hourly Mode**: Proportional rewards - towns receive `(daily_reward / 24)` every hour they control a zone

**Configuration:**
```yaml
rewards:
  reward-type: "hourly"  # "daily" or "hourly"
  hourly-interval: 3600  # Interval in seconds (default: 1 hour)
```

**How It Works:**
- **Daily Mode**: Same as before - full reward at Towny new day
- **Hourly Mode**: 
  - Every hour, controlling towns get proportional reward
  - Example: 1000 daily reward = 41.67 hourly reward (1000/24)
  - Holding for 6 hours = 6 × 41.67 = 250 total reward
  - Holding for 24 hours = same total as daily mode (1000)

**Benefits:**
- More frequent feedback for players
- Rewards scale with actual holding time
- Server owners can choose based on their economy
- Fully backward compatible

**Messages:**
- Daily: "Town has received 1000 for controlling Point!"
- Hourly: "Town has received 41.67 hourly reward for controlling Point!"

## Version 1.0.1 - [2025]

### 🐛 Bug Fixes
- Fixed compilation and runtime errors including NullPointerException on capture completion, boundary cleanup issues, and zone deletion during active captures

### ✨ New Features

#### 🔊 Sound System
Play immersive sound effects during capture events to enhance gameplay and alert players to important moments.

**Sound Events:**
- `capture-started`: Deep wither ambient sound when preparation begins
- `capture-phase-started`: Ominous wither spawn when capture phase starts  
- `capture-complete`: Victory level-up sound when capture succeeds
- `capture-failed`: Dramatic ender dragon death for failed captures
- `capture-progress`: Subtle note block pling during active capture
- `point-reset`: Experience orb pickup sound when admin resets a point

**Configuration:**
```yaml
sounds:
  enabled: true  # Set to false to disable all sounds
  capture-started:
    sound: "entity.wither.ambient"
    volume: 1.0
    pitch: 1.2
```

**Usage:**
- Sounds play automatically during capture events
- Only players without disabled notifications will hear sounds
- Each sound can be customized in config.yml with different volume and pitch

#### 👹 Mob Reinforcement System
A dynamic defensive mob system that makes captures progressively more challenging as time passes.

**How It Works:**
- Defensive mobs spawn to protect captured zones from being taken
- Mobs spawn at specific timer marks (:00 and :30 - like 14:30, 14:00, 13:30, etc.)
- Each phase spawns more mobs than the last (Phase 1: 2 mobs, Phase 2: 3 mobs, Phase 11+: 12 mobs max)
- No spawns in the final minute (gives capturing players a break)
- 26 different hostile mob types can spawn including zombies, skeletons, creepers, blazes, witches, evokers, and more

**Smart Targeting:**
- Mobs ONLY attack players from the capturing town
- Defenders from other towns can walk by safely
- Mobs pathfind directly to the nearest capturing player

**Daylight Protection:**
- All mobs are equipped with helmets to prevent sunlight burning
- Can spawn during daytime without instantly dying
- Uses random helmets: leather, iron, golden, or chainmail

**Kill Rewards:**
Every reinforcement mob you kill reduces the capture timer:
- Reduces timer by **1-3 seconds** (random)
- Shows message: "**-2 seconds! Capture timer reduced!**"
- Killing multiple mobs = stacking time reductions
- Successfully fighting defenders can significantly speed up your capture

**Phase System:**
- Phases spawn based on the actual capture timer position, not elapsed real-time
- If you kill lots of mobs and timer jumps from 14:45 to 14:12, the system immediately spawns the 14:30 phase
- Every phase always spawns regardless of how much time was skipped by kill rewards
- Example timeline:
  - Start capture: 15:00 remaining, Phase 1 spawns (2 mobs)
  - 30 seconds later: 14:30 remaining, Phase 2 spawns (3 mobs)
  - You kill 5 mobs: Timer drops from 14:25 to 14:15, Phase 2 spawns immediately
  - 30 seconds later: 14:00 remaining, Phase 3 spawns (4 mobs)
  - Final minute: No more mobs spawn, just survive to complete!

**Configuration:**
```yaml
reinforcements:
  enabled: true  # Disable entire reinforcement system
  mobs-per-wave: 1  # Base mobs per phase (+ phase number)
  wave-interval: 30  # Spawns at :00 and :30 marks
  max-mobs-per-point: 50  # Maximum accumulated mobs
  mob-types:  # 26 different types available
    - "ZOMBIE"
    - "CREEPER"
    - "BLAZE"
    # ... etc
  hat-items:
    - "LEATHER_HELMET"
    - "GOLDEN_HELMET"
    # ... etc
```

**Tips:**
- Bring friends! Killing mobs together speeds up captures
- Stay in the zone while fighting - leaving can cancel capture
- Each mob kill is worth 1-3 seconds - every kill matters
- Later phases get intense - up to 12 different mob types at once
- Last minute is mob-free - if you survive that long, you're almost done

#### 🎨 Visual Improvements
**Updated Color Scheme:**
- Unclaimed zones now use dark red fill (`#8B0000`) instead of bright red
- Zone borders use dark gray (`#404040`) instead of dark red
- More subtle and less obtrusive appearance on the map
- Captured zones maintain their town's color for visual ownership

#### 🔇 Notification Toggle System
A unified toggle to control ALL plugin notifications (boss bars, sounds, messages) in one command.

**Command:**
```
/capturepoint notifications
/cap notifications
/capturepoint silence  # Alias
```

**What Gets Toggled:**
- ✅ Boss bars (capture progress display)
- ✅ Sound effects (all capture-related sounds)
- ✅ Capture messages (phase started, completed, etc.)

**Usage:**
1. Start with notifications ON by default
2. Run `/cap notifications` to disable all TownyCapture notifications
3. Run `/cap notifications` again to re-enable them
4. Toggle persists across captures until server restart

**Example:**
```
Player runs: /capturepoint notifications
Chat: "All TownyCapture notifications disabled."

(Tries to capture a zone - sees nothing, hears nothing, gets no boss bar)

Player runs: /capturepoint notifications  
Chat: "All TownyCapture notifications enabled."

(Now sees boss bars, hears sounds, gets messages)
```

## Summary

This update introduces **Mob Reinforcement System** - a dynamic defense system that spawns hostile mobs to protect zones, rewarding skilled players with timer reductions for kills. The **Sound System** provides immersive audio feedback for all capture events, while the **Notification Toggle** gives players complete control over UI elements. Visual improvements make the map cleaner with darker color schemes. All critical bugs have been fixed for a more stable experience.
