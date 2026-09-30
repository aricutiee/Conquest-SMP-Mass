# Conquest SMP Mass

Paper 1.21.11 server plugin, maintained by ari. Requires Java 21. Current release: **3.24.7**.

This release removes the retired fake-player command and its simulated AFK bodies. Shop NPCs, player AFK rewards, owner troll mode and launch-based analytics remain available. See [the update guide](UPDATE-3.24.7.md), [analytics documentation](ANALYTICS.md) and [the feature catalog](CONQUEST-SYSTEMS.md).

## Build

Run `./gradlew build` (Windows: `gradlew.bat build`). The shaded plugin JAR is written to `build/libs`. The build generates the resource pack, runs the automated tests, and tests the packaged SQLite driver.

## Features

Integrated biome races and Dragonborn, staff roles and hierarchy, editable Booster kits, homes, 54-slot ender chests, death chests, combat tags, event rewards, Warlord hunt, Juggernaut competition, loot drops, selected spawn protection, SMP launch controls, dimension controls, resource-pack badges, tab logo and scoreboard.

The latest update adds four floating top-ten leaderboards, completed/active streak records, revised boss armor with Unbreaking IV, a public animated Juggernaut mace reward, and final spear/mace damage caps of 70%/99% of victim maximum health. See [the full server guide](SERVER-GUIDE.md) for player commands, events, races, staff roles and permissions.

## Installation

Back up the server and player data. Stop the server, replace the previous ConquestSMP JAR in `plugins`, and start Paper 1.21.11 with Java 21. Do not install the standalone BiomeRaces plugin alongside this plugin because races are integrated here. Configure generated YAML files in `plugins/ConquestSMP`.

King's Crown remains a separate optional integration. GrimAC, Floodgate, Geyser, and other third-party plugins retain their own distributions and licenses. No production credentials or player databases are included.

## Verification

Automated checks cover races, combat, staff restrictions, rewards, spawn, dragon rules and RTP border/landing rules. A successful build is not a claim that every behavior has been exercised by a Minecraft client. Version 3.24.3 passed 368 automated tests and loaded on the live Paper 1.21.11 server on September 29, 2026. The preceding 3.24.1 inspection report was exercised through the live console. Real-client menus, rendering and gameplay require in-game verification. See [deployment verification](DEPLOYMENT-3.24.3.md).

## Related projects

* [Original standalone BiomeRaces](https://github.com/aricutiee/BiomeRaces)
* [King's Crown](https://github.com/aricutiee/KingsCrown)
* [Debuff and tipped arrow rules](https://github.com/aricutiee/disableDebuffAndTipped)
* [Resource pack](https://github.com/aricutiee/Conquest-SMP-Resource-Pack)
* [Integration profiles](https://github.com/aricutiee/Conquest-SMP-Integrations)

Source attribution in existing packages is retained. Distribution rights for third-party assets and dependencies remain with their respective owners.

Leaderboard rendering fix in 3.17.1: normal occlusion and front-facing heads, preserving all saved placements and styling.
