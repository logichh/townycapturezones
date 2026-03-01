![Preview](https://i.ibb.co/Dg6mThbQ/minecraft-title-minecraft.png)
# CaptureZones

CaptureZones adds configurable capture-zone gameplay to Minecraft servers:

- Circle and cuboid zones
- Per-zone rules and rewards
- Optional KOTH mode
- Reinforcements, shops, statistics, holograms, map integrations
- Towny-aware or standalone ownership modes

This README is intentionally short. Full documentation lives in the wiki.

## Quick Start

1. Put the plugin jar in `plugins/`.
2. Start server once.
3. Edit `plugins/CaptureZones/config.yml`.
4. Use `/cap help` in game.

## Full Documentation

Wiki:
- [Wiki Home](https://github.com/logichh/townycapturezones/wiki)
- [Installation](https://github.com/logichh/townycapturezones/wiki#installation)
- [Command Reference](https://github.com/logichh/townycapturezones/wiki#command-reference)
- [Permission Reference](https://github.com/logichh/townycapturezones/wiki#command-reference)
- [Global Config Reference](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Per-Zone Config Reference](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Setup Guides](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [KOTH Mechanics](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Shops](https://github.com/logichh/townycapturezones/wiki#global-configuration-reference-configyml)
- [Discord](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)
- [PlaceholderAPI](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)
- [Repair and Migration](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)
- [Troubleshooting](https://github.com/logichh/townycapturezones/wiki#discord-webhooks)

GitHub wiki:
- https://github.com/logichh/capturezones/wiki

## Requirements

- Java 17+
- Paper/Spigot 1.20+

Optional integrations:
- Towny
- Vault + economy plugin
- Dynmap
- BlueMap
- PlaceholderAPI
- MythicMobs
- WorldGuard

## Addon API (For External Web Panel Plugins)

CaptureZones now registers a Bukkit service for addon plugins:

- Service interface: `com.logichh.capturezones.api.CaptureZonesApi`
- Result type: `com.logichh.capturezones.api.CaptureZonesActionResult`
- API version constant: `CaptureZonesApi.API_VERSION`

### Resolve the service from another plugin

```java
RegisteredServiceProvider<CaptureZonesApi> rsp =
    Bukkit.getServicesManager().getRegistration(CaptureZonesApi.class);
CaptureZonesApi api = rsp == null ? null : rsp.getProvider();
```

Your addon should declare a dependency on CaptureZones in its own `plugin.yml`:

```yaml
depend: [CaptureZones]
```

### What the API exposes

- Full snapshots: overview, zones, active captures, KOTH, shops, statistics, configs, data files.
- Mutations/actions: zone lifecycle, capture controls, KOTH controls, shop controls, stats controls, config writes/reloads.
- Capability discovery via `getCapabilities()` so addons can feature-gate safely.

## Build From Source

```bash
mvn clean package
```

## Support

- Wiki: https://github.com/logichh/capturezones/wiki
- Discord: https://discord.gg/t96nrf7Nav
- Patreon: https://www.patreon.com/cw/logich
- GitHub Sponsors: https://github.com/sponsors/logichh

![Preview](https://bstats.org/signatures/bukkit/Capture%20Points.svg)
