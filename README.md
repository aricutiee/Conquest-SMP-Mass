# Conquest SMP Mass

Paper 1.21.11 server plugin, maintained by ari. Requires Java 21.

## Build

Run `./gradlew build` (Windows: `gradlew.bat build`). The shaded plugin JAR is written to `build/libs`. The build generates the resource pack, runs the automated tests, and tests the packaged SQLite driver.

## Features

Integrated biome races and Dragonborn, staff roles and hierarchy, editable Booster kits, homes, 54-slot ender chests, death chests, combat tags, event rewards, Warlord hunt, Juggernaut competition, loot drops, selected spawn protection, SMP launch controls, dimension controls, resource-pack badges, tab logo and scoreboard.

The latest update adds four floating top-ten leaderboards, completed/active streak records, revised boss armor with Unbreaking IV, a public animated Juggernaut mace reward, and final spear/mace damage caps of 70%/99% of victim maximum health. See [the full server guide](SERVER-GUIDE.md) for player commands, events, races, staff roles and permissions.

## Installation

Back up the server and player data. Stop the server, replace the previous ConquestSMP JAR in `plugins`, and start Paper 1.21.11 with Java 21. Do not install the standalone BiomeRaces plugin alongside this plugin because races are integrated here. Configure generated YAML files in `plugins/ConquestSMP`.

King's Crown remains a separate optional integration. GrimAC, Floodgate, Geyser, and other third-party plugins retain their own distributions and licenses. No production credentials or player databases are included.

## Verification

Automated checks cover races, combat, staff restrictions, rewards, spawn, dragon rules and RTP border/landing rules. A successful build is not a claim that every behavior has been exercised by a Minecraft client. Version 3.17.0 passed 279 automated tests and the packaged SQLite check, and was loaded on the live Paper 1.21.11 server on September 28, 2026. Actual player-operated RTP, dragon encounters and visual effects still require in-game verification.

## Related projects

* [Original standalone BiomeRaces](https://github.com/aricutiee/BiomeRaces)
* [King's Crown](https://github.com/aricutiee/KingsCrown)
* [Debuff and tipped arrow rules](https://github.com/aricutiee/disableDebuffAndTipped)
* [Resource pack](https://github.com/aricutiee/Conquest-SMP-Resource-Pack)
* [Integration profiles](https://github.com/aricutiee/Conquest-SMP-Integrations)

Source attribution in existing packages is retained. Distribution rights for third-party assets and dependencies remain with their respective owners.
