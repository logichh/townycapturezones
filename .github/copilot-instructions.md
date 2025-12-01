# AI Coding Agent Guidelines for TownyCapture

Purpose-built notes to make AI agents immediately productive in this repo.

## Big Picture
- **Goal**: Add capturable points to Towny servers with rewards, protections, visuals, and optional map integration.
- **Entrypoint**: `com.logichh.townycapture.TownyCapture` (declared in `src/main/resources/plugin.yml`). Initializes config/messages, registers commands and listeners, and schedules maintenance.
- **Core modules**:
  - `CapturePoint`: Immutable-ish point metadata (name, location, type, payout, time windows, controller state).
  - `CaptureSession`: Active capture state; progress, participants, timers; exposes start/stop/progress APIs.
  - `CaptureCommands` + `CaptureCommandTabCompleter`: `/capturepoint` (alias `/cap`) routing; admin subcommands mirror `plugin.yml` children and require confirmations for destructive ops.
  - `CaptureEvents`: Aggregator for gameplay hooks with dedicated listeners:
    - `CaptureDeathListener`: Adjusts session state on player death.
    - `BlockProtectionListener` + `ZoneProtectionListener`: Prevents breaking/placing within protected zones during sessions.
    - `CommandBlockListener`: Denies command blocks affecting captures.
    - `ReinforcementListener`: Spawns defenders with rate limiting.
    - `NewDayListener`: Weekly reset + first-capture bonus orchestration.
  - `UpdateZones`: Periodic cleanup and maintenance tasks (boundary visuals, stale sessions, weekly neutralization triggers).
  - `CapturePointInfoWindow`: Dynmap infowindow rendering using placeholders and `Messages`.
  - `Messages`: Loads `lang/*.json` and provides localized strings and labels.
  - `AreaStyle`: Particle boundary style; ON/REDUCED/OFF modes; referenced by visuals tasks.

## Data & Flows
- **Config-driven**: `src/main/resources/config.yml` is the source of truth for reward type (hourly/static/dynamic), visuals, windows, reinforcements, and weekly reset.
- **Session lifecycle**: Command → validate permissions and time window → create/start `CaptureSession` → listeners enforce rules and may pause/stop → on success update `CapturePoint` controller and apply rewards.
- **Rewards**: Honor `rewards.reward-type` and ranges; dynamic hourly ranges use `hourly-dynamic.min/max`. First-capture bonus governed by `weekly-reset.first-capture-bonus.*`.
- **Integrations**: Soft depends (`Dynmap`, `WorldGuard`, `PlaceholderAPI`, `Dynmap-Towny`). Always guard with null/PluginManager checks before accessing their APIs.
- **Dynmap infowindow**: Template + placeholders: `%control_status%`, `%controlling_town%`, `%name%`, `%type%`, `%reward%`, `%active_status%`, `%label_*%`, `%item_payout%`. Localization provided by `lang/en.json` via `Messages`.

## Conventions & Patterns
- **Permissions**: Namespaced `capturepoints.*` with granular children under `capturepoints.admin.*` as in `plugin.yml`. Implement command checks mirroring these children.
- **Destructive ops**: Require confirmations (e.g., `/cap deletezone <name> [CONFIRM]`). Keep UX parity with README.
- **Time windows**: Respect point-specific windows via `CapturePoint` and config; disallow captures outside configured times.
- **Weekly reset**: Neutralize points and apply first-capture bonus ranges using `NewDayListener` + `UpdateZones`; read `weekly-reset.*` from config.
- **Visuals**: `AreaStyle` ON/REDUCED/OFF determined by config; schedule repeating tasks for boundary particles and cleanup; avoid heavy work on main thread.
- **Rate limiting**: `reinforcements.spawn-rate.max-per-tick` caps defender spawns; event handlers must avoid tight loops.
- **Localization stability**: Keep `%label_*%` keys stable; add new keys to `lang/*.json` without breaking existing placeholders.

## Developer Workflows
- **Build (Maven)**:
  - `mvn clean package` → JAR in `target/`.
  - Requires Java 17+, Spigot/Paper 1.20+, Towny at runtime.
- **Local test (Windows, PowerShell)**:
  - Copy the built JAR to a local Paper server:
    ```powershell
    $jar = "g:\.capturezones\townycapturezones-v.1.0.3\target\TownyCapture-*.jar"
    Copy-Item $jar "C:\Servers\Paper\plugins\"
    ```
  - Start server; verify `TownyCapture` enabled; install soft-deps when testing integrations.
- **Configs**:
  - Server configs live under `plugins/TownyCapture/`; keep `src/main/resources/config.yml` defaults synchronized when adding options.
- **Debugging**:
  - Use Bukkit logger via `TownyCapture`; add concise debug messages gated by a config debug flag; keep event handlers lightweight.

## Event Flow & Scheduling
- **Registration**: `TownyCapture` registers commands (`CaptureCommands`), tab completer, and listeners on `onEnable()`; schedules `UpdateZones` repeating tasks for visuals and cleanup.
- **Listeners**: Protection listeners short-circuit actions within active capture zones; death events update `CaptureSession` participants; reinforcement spawns are scheduled and rate limited.
- **Weekly reset**: `NewDayListener` triggers neutralization and first-capture bonus reset at configured day/time; `UpdateZones` assists with clean boundaries.

## Extending & Changing Features
- **Commands**: Update `CaptureCommands` and `plugin.yml` (aliases/usage/permissions). Add tab logic in `CaptureCommandTabCompleter` to mirror new subcommands and enforce child permissions.
- **Mechanics**: Extend `CapturePoint`/`CaptureSession` for new capture rules; ensure protection listeners reflect new constraints; keep rewards aligned with config switches.
- **Dynmap**: Modify `CapturePointInfoWindow` and add/adjust placeholders; update `lang/*.json` keys consistently.
- **Config**: Add defaults in `src/main/resources/config.yml`, load in `TownyCapture` init, and document in README.
- **Resets**: Adjust `NewDayListener` and `UpdateZones` when changing weekly reset logic or visuals cleanup cadence.

## Key Files & References
- `src/main/java/com/logichh/townycapture/TownyCapture.java` – plugin bootstrap, registration, scheduling.
- `src/main/java/com/logichh/townycapture/CaptureCommands.java` – command routing; mirrors `plugin.yml`.
- `src/main/java/com/logichh/townycapture/CaptureCommandTabCompleter.java` – tab completion aligned with permissions.
- `src/main/java/com/logichh/townycapture/CapturePoint.java` / `CaptureSession.java` – domain + active session.
- `src/main/java/com/logichh/townycapture/CaptureEvents.java` and listeners – gameplay + protection.
- `src/main/java/com/logichh/townycapture/UpdateZones.java` – visuals cleanup + maintenance.
- `src/main/java/com/logichh/townycapture/Messages.java` – i18n loader.
- `src/main/java/com/logichh/townycapture/CapturePointInfoWindow.java` – Dynmap template rendering.
- `src/main/java/com/logichh/townycapture/AreaStyle.java` – boundary style.
- `src/main/resources/plugin.yml` – main class, commands, permissions, dependencies.
- `src/main/resources/config.yml` – behavior switches and defaults.
- `src/main/resources/lang/en.json` – labels and infowindow text.

Questions or gaps? If you need deeper pointers (e.g., exact scheduler intervals or specific event ordering inside listeners), ask and I’ll annotate with code-line references.