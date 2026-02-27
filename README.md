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

Local wiki file:
- [Wiki Home](wiki.md)
- [Installation](wiki.md#installation)
- [Command Reference](wiki.md#command-reference)
- [Permission Reference](wiki.md#permission-reference)
- [Global Config Reference](wiki.md#global-configuration-reference-configyml)
- [Per-Zone Config Reference](wiki.md#per-zone-configuration-reference)
- [Setup Guides](wiki.md#setup-guides-with-examples)
- [KOTH Mechanics](wiki.md#koth-mechanics)
- [Shops](wiki.md#shop-system)
- [Discord](wiki.md#discord-webhooks)
- [PlaceholderAPI](wiki.md#placeholderapi)
- [Repair and Migration](wiki.md#repair-and-migration)
- [Troubleshooting](wiki.md#troubleshooting)

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

## Build From Source

```bash
mvn clean package
```

## Support

- Wiki: https://github.com/logichh/capturezones/wiki
- Discord: https://discord.gg/t96nrf7Nav
- Patreon: https://www.patreon.com/cw/logich
- GitHub Sponsors: https://github.com/sponsors/logichh
