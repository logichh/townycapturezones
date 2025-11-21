# TownyCapture - Change Log

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
/cp notifications
/capturepoint silence  # Alias
```

**What Gets Toggled:**
- ✅ Boss bars (capture progress display)
- ✅ Sound effects (all capture-related sounds)
- ✅ Capture messages (phase started, completed, etc.)

**Usage:**
1. Start with notifications ON by default
2. Run `/cp notifications` to disable all TownyCapture notifications
3. Run `/cp notifications` again to re-enable them
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

