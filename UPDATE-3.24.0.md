# Conquest SMP 3.24.0

Author: ari

## Combined staff inspection

`/conquestac` reports GrimAC, AltDetector and ClientPolicy integration status. `/conquestac inspect <real-player-name|UUID>` shows up to 12 recent observations in a single staff dossier. `/conquestac alts <player>` opens the upstream account lookup, and `/conquestac client <online-player>` opens detailed client inspection. Source labels stay separate: ALT means suspected account links, CLIENT means client-announced information, and GRIM means anti-cheat flags. No signal is presented as proof of cheating or a unique physical device.

AltDetector 1.0.0 and ClientPolicy 1.0.0 remain separate upstream plugins. Conquest uses their APIs without bundling their implementations. Alt auto-ban events are cancelled; supplied configuration disables auto-bans and uses ClientPolicy LOG actions only. Grim's existing correction and alert behavior remains. The combined history is local, UUID keyed, retained for 30 days, and does not copy raw IPs. AltDetector maintains its own local IP history. Player records, IPs and live configuration are excluded from GitHub.

## Spawner Shop

Place the new villager with `/npc set spawners`. Edit its name, description and color using `/npc edit spawners title|description|color <value>`. Two empty spawner display items orbit and bob around it. The 54-slot shop follows the live Utilities arrangement: alternating offer slots, purple and black panes in the first five rows, and an amethyst balance icon in the otherwise undecorated bottom row.

| Spawner | Shards |
| --- | ---: |
| Cow, pig, chicken | 250 |
| Zombie, skeleton, spider | 750 |
| Blaze, phantom, magma cube, custom emerald villager, creeper | 1,000 |

Each hover tooltip shows its exact virtual loot ranges, XP and price. These are explicit Looting III-style virtual tables, without random equipment drops. The custom villager generates 1-3 emeralds and 5 XP per cycle. Naturally generated spawners are unchanged.

Purchased spawners produce one simulated mob's loot per spawner every 120 active seconds while their chunk is loaded. No real mobs are created and no offline backlog accrues. Right-click with a matching bought spawner to combine stacks up to 500. Right-click empty-handed to open all 54 loot slots; click a loot stack to withdraw it. Sneak + right-click the placed spawner to collect XP. Storage pauses when an entire batch cannot fit or stored XP reaches its cap. Collect loot and XP, then mine with a Silk Touch pickaxe to recover the whole typed stack. Virtual blocks resist explosions and piston movement to preserve stored items. Spawn protection still applies.

## String cooldowns

Conquest now owns `/string`, retaining the original behavior of filling empty storage slots with stacks of string. The old StringPlugin JAR must be disabled to avoid an alternate namespaced command bypass.

| Rank | Seconds |
| --- | ---: |
| Member, other unlisted non-operator ranks | 60 |
| Coal or Booster | 50 |
| Iron | 45 |
| Redstone | 40 |
| Diamond or Booster X2 | 30 |
| Netherite | 15 |

Combined rank and booster benefits use the shorter cooldown. Stored operator-level roles keep their established gameplay bypass. The cooldown appears in a purple boss bar and survives reconnects and restarts. Full inventories do not start a cooldown.

## Verification

339 automated tests passed, including evidence persistence and isolation, automatic alt-ban cancellation, virtual spawner production timing and metadata, full-storage handling, normal spawner preservation and every requested rank cooldown. Server startup, all three integration connections, combined history and upstream alt lookup were verified live. Real-client GUI appearance, purchases and actual detection still need gameplay verification. See DEPLOYMENT-3.24.0.md.
