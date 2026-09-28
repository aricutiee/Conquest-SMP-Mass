# Conquest SMP: player and staff guide

Version 3.17.5 | Paper 1.21.11 | Author: ari

This guide describes the implemented Conquest rules and the installed integrations. It supersedes older release notes where the rules changed. Timings and loot amounts below are the shipped settings unless identified otherwise. Administrators can change configuration, rewards and third-party permissions later.

## 1. Starting out

Conquest SMP is a survival and PvP server with a Terralith overworld, Amplified Nether terrain with Dungeons and Taverns Nether Fortress Overhaul, Nullscape End terrain, automatic races, public event rewards and staff-run events. Ordinary players use diamond armor or weaker. Netherite tools and weapons are allowed, but netherite armor is reserved for designated bosses, with the senior staff exceptions described below.

New players receive a five-second race roll. Names cycle quickly, then slow down, with a note sound on each change and an Ender Dragon growl at the reveal. The result is saved. Leaving during the roll does not give another chance: the same saved draw resumes. Existing players do not reroll on every login.

Java account verification remains enabled. AuthMe is currently disabled, so there is no AuthMe password registration or password-reset workflow in active use. Bedrock access is provided by Geyser and Floodgate. Whitelist and account access are separate server settings.

The Java server resource pack supplies the Conquest logo, role badges and crown artwork. Accept the server pack to see these assets. Races themselves use vanilla effects and do not require a client mod. Bedrock uses text fallbacks for the Java pack features.

## 2. Everyday commands

| Command | What it does |
| --- | --- |
| `/race` or `/race info` | View your assigned race and its abilities. An unassigned player can begin their saved roll. |
| `/spawn` or `/worldspawn` | Teleport to the saved world spawn, subject to combat and destination rules. |
| `/rtp` | Find a safe random destination inside the current border of `world_terralith`. Run it in that overworld. |
| `/sethome [name]` | Save a home at your position. Omit the name to use `home`. |
| `/home [name]` | Teleport to a saved home if the destination is permitted. |
| `/homes` | List your homes and allowance. |
| `/delhome [name]` | Delete a saved home. |
| `/kit` | Preview and claim the Booster kit if eligible. |
| `/report <player> <reason>` | Send a report to staff. |
| `/role`, `/role info <player>`, `/role list` | View role information. Management buttons depend on your rank. |
| `/events assassins join` | Join an open Assassins enrollment window. |

JustTPA, Back and Slash String are separate installed plugins providing teleport requests, return teleporting and string access. Their own configuration controls their extra behavior and cooldowns. Conquest still blocks ordinary player commands while combat-tagged. `/ec`, `/echest` and `/enderchest` are disabled for ordinary players; use a physical Ender chest outside combat.

Home allowances: Member, Booster and Booster X2 have five; Media, Helper and Moderator have six; SSER has ten. Admin, Senior Admin, Co-owner and Owner bypass the home limit under the current senior staff rule.

## 3. Combat rules and equipment limits

A successful damaging PvP hit tags both players for 45 seconds. Further qualifying PvP damage refreshes the timer. A purple boss bar displays the remaining time. Relogging does not clear the saved deadline.

During combat, ordinary players cannot run commands, glide with elytras, use tridents, open or use Ender chests, or enter the selected spawn safe area. Other ordinary containers remain usable. An Ender chest already open when combat starts is closed. Operators have the specific command-lock exception; the broader senior staff exceptions are listed later.

After actual elytra gliding, mace attacks remain locked for ten seconds after gliding ends. Simply wearing an elytra does not start this lock. Spears remain usable, but every three successful Lunge activations trigger a 15-second recovery. The recovery is shared between hands and spear materials. Normal spear jabs remain usable during Lunge recovery. Counts and deadlines survive death, logout and restart.

Ender Pearls cannot be picked up or used by ordinary players. The pickup message says that pearls are not allowed. Existing pearls are not silently deleted. This ban replaces the older ten-second pearl cooldown.

Weapon damage is capped per attack against players, after armor and other damage reductions:

| Weapon | Maximum damage from one hit |
| --- | --- |
| Spear | 70% of the victim's maximum health |
| Mace | 99% of the victim's maximum health |

For a player with ten maximum hearts, that is seven hearts or 9.9 hearts. These are caps, not guaranteed damage. A weaker hit stays weaker. The cap uses maximum health, so a second hit can kill an injured player. Cancelled or fully blocked attacks do not become damaging. Extra maximum hearts affect the cap.

Only earned Juggernaut maces are ordinary-player maces. Normal mace crafting and use are restricted. Each completed Juggernaut event can create one mace; this is not one mace for the entire server forever.

Only designated Juggernaut and Warlord players can normally craft, upgrade or wear netherite armor. Unauthorized crafting displays: “You are not allowed to craft netherite armor.” Existing armor is returned to inventory rather than deleted when it is removed from an unauthorized wearer. Netherite weapons and tools remain available. Won Warlord armor can be carried, but winning it does not make an ordinary player eligible to wear netherite.

End-crystal and respawn-anchor explosions do not directly damage players. Their terrain effects can still apply outside protected areas. Chorus fruit is not globally banned by Conquest.

Conquest does not automatically kill, ban or kick a player merely for disconnecting during its combat tag. Third-party punishment policies are separate.

## 4. Races

Each base race has one passive, one defense and one offensive ability. Home biomes are themes, not geographic locks. Abilities work wherever their actual conditions are met.

| Race | Passive | Defense | Offense |
| --- | --- | --- | --- |
| Bogborn | Water Breathing while the head is underwater | In water, reduce a melee/projectile hit by 20%; 25-second cooldown | Poison I for two seconds |
| Rootbound | Speed I on mud, mangrove roots or muddy mangrove roots | On qualifying soil/roots, reduce a hit by 10% and its knockback by 60%; 25-second cooldown | Slowness I for 1.5 seconds |
| Petalfolk | 20% less fall damage | After surviving a damaging melee/projectile hit, Absorption I for four seconds; 30-second cooldown | Restore half a heart, up to maximum health |
| Hollow-Eyed | Night Vision at eye light level seven or lower, with smoothing near boundaries | In low light, reduce a melee/projectile hit by 20%; 25-second cooldown | Darkness for one second |
| Dwarf | Haste I below Y=40 | On stone/deepslate, prevent up to four hearts of actual fall damage; 30-second cooldown | Add half a heart before armor |

Offense activates on the third qualifying melee hit against the same target. Attacks must be direct main-hand hits with at least 90% charge against players or hostile mobs, allowed by protection and actually damaging. Misses, fully blocked attacks, cancelled damage, projectiles, sweep secondary hits, pets, passive animals and armor stands do not count.

Changing targets or waiting eight seconds resets the base chain. Activation starts a 12-second offensive cooldown; hits during it do not build another chain. Defense timers are separate. Death, relogging and egg swapping do not erase saved cooldowns. Relevant cooldowns appear in boss bars; chain progress can use the action bar.

All five races have equal default odds. Players cannot freely change a saved race. Staff use `/race set <player> <race>` or `/race reset <player>`. Valid names are `bogborn`, `rootbound`, `petalfolk`, `hollow-eyed` and `dwarf`. `/roll` is an operator preview and does not change the saved race. `/race reload` reloads race settings.

## 5. Dragonborn and the Dragon Egg

Holding a Dragon Egg in either hand starts the private transformation sequence. Keeping it elsewhere in inventory or placing it in the world does not transform you. Releasing it before the sequence ends cancels the transformation.

Dragonborn temporarily suspends the base race and grants four additional maximum hearts, Strength I, Speed I and Fire Resistance. Transforming does not refill health. Releasing the egg restores the base race and removes Dragonborn contributions. Health is clamped only when it exceeds the new maximum.

Rising Fury uses the same charged, damaging melee eligibility, but its own same-target chain with a four-second timeout and no 12-second offensive cooldown:

1. Hit one: normal damage, including Strength.
2. Hit two: normal damage, including Strength.
3. Hit three: normal damage plus two damage points before armor.
4. Hit four: eight damage points total before armor.
5. Hit five: ten damage points total before armor.
6. Hit six: twelve damage points total before armor.
7. Later hits: twice the hit number in total damage points before armor.

From hit four onward, this replaces the ordinary weapon amount. It does not add all previous attacks together. Hit four can intentionally be weaker than a powerful third hit. Armor and valid defenses still apply, including the spear/mace cap where relevant. Target changes, target death, a four-second gap, ending Dragonborn, player death or logout reset the chain.

## 6. Storage, rewards and death chests

Physical Ender chests provide 54 personal slots. The additional rows persist with player data. Vanilla-only inspection plugins may see only the original 27 slots.

Event rewards such as the crown, Dragon Egg, earned mace and tagged Warlord relics cannot be deposited into ordinary containers, Ender chests, bundles or bookshelves by ordinary players. Hotbar swaps, drags and hopper routes are also guarded. Players may carry, equip where permitted, drop and pick up the items. Existing event/death chests allow reward withdrawals. The restriction does not make all chests inaccessible.

Protected ground rewards do not naturally despawn or burn in fire/lava and are protected from normal environmental destruction. Throwing one into the void destroys it and announces the loss. There is no automatic replacement; the event must be repeated. Custom item names cannot be changed with an anvil by ordinary players.

On an ordinary death, dropped items go into a nearby public double death chest. Anyone can loot it. It is protected against breaking, explosions, pistons and hopper draining. It disappears when empty. After ten minutes, remaining contents are dropped on the ground and normal items follow Minecraft's normal loaded-tick despawn rules. Protected event rewards retain their special protection. If no safe chest location can be created, ordinary death drops are used rather than deleting the inventory. Keep-inventory deaths do not duplicate items.

## 7. Events and administrator controls

`/events` opens the event menu. `/event end` or `/events stop` ends the current event and restores temporary boss loadouts. Only one competitive event can be active at once. Loot drops and the manual locator-bar window have their own controls.

An event scoreboard takes priority over the normal Conquest sidebar while active. The normal sidebar returns afterward. An event that has been won can still need `/event end` to release remaining event pickup restrictions.

**Egg Hunt**

Start in game with `/events start egg`, then enter a duration in the private chat prompt, such as `5 minutes` or `1h 30m`. Type `cancel` to abort. The prompt expires after 60 seconds. The server's hunt world was configured for Terralith. Eggs appear around eligible exploring players on loaded outdoor terrain. Normal eggs give five points; rare eggs give twenty, with a default one-in-eight rarity. The event tracks scores and awards its configured prize. Set a prize through `/events reward egg` before running a rewarded hunt.

**Assassins**

Use `/events start assassins`. Players join with `/events assassins join`. The default enrollment window is 60 seconds and at least three participants are required. Every participant has one target and one hunter. The action bar identifies the target and their location/dimension. Attacking your own hunter eliminates you from the event while more than two players remain. The final two can fight each other. Eliminations update the target chain, and the last participant wins the configured prize. The default disconnect grace is 60 seconds.

**Juggernaut**

Use `/events start mace`, then `/juggernaut <player>` to designate the boss. Original inventory is backed up for restoration. The fight displays a top-ten damage ranking.

The Juggernaut wears Protection VI head equipment and boots, Protection V chestplate and leggings, all with Unbreaking IV. Its sword is Sharpness V. Its loadout includes the event mace and configured supplies.

On defeat, only the mace is released as Juggernaut loot. An enlarged visual rises for five seconds, shakes for two, then one real mace drops publicly at the defeat position. Anyone eligible to pick it up can claim it. This replaces the former automatic award to the highest damage dealer. The drop and holder are exposed by the existing glow system: a three-minute deadline starts when the mace is released, and passing it between players does not restart that deadline.

**Warlord's Purge**

Use `/warlord start <player>`, or start the event through `/events` and designate with `/juggernaut warlord <player>`. End a previous event first. Warlord has no mace. Helmet and boots have Protection VI, chestplate and leggings Protection V, all with Unbreaking IV. The sword has Sharpness VI.

Seven relics are involved: helmet, chestplate, leggings, boots, pickaxe, Heavy Hitter Axe and Warlord's Ratio sword. The pickaxe is unbreakable, Efficiency V and Fortune III. Crouching while holding it toggles 3x3 mining; crouching again turns that mode off. Extra blocks use normal protection checks and skip containers and unbreakable blocks.

On defeat, visual gear rises, hovers and scatters in shooting-star trails with eerie audio and white cloud effects. The title announces THE HUNT BEGINS. Seven real rewards are placed in separate public chests on safe terrain 500 to 1,500 blocks from saved world spawn. They can be discovered before coordinates are announced.

Open `/warlordevent` or `/warlord event` to reveal individual relic coordinates. `/warlordevent retry` retries pending chest placements. The hunt chests block deposits and destruction, allow withdrawals and disappear when empty. Complete the previous hunt before starting another.

`/warlord end` and `/warlordevent preview` preview the animation at your location. Despite its name, `/warlord end` is a preview shortcut, not the command to end the real event. Use `/event end` for that.

**King's Crown**

KingsCrown remains a separate integrated plugin. Wearing its crown grants three extra maximum hearts and Speed I. Conquest displays the current KING badge and supports its model in the server pack. `/crown` is operator-only. The crown and race are independent.

The legacy Crown Race entry can be opened with `/events start crown`, and configured manual prizes use `/events reward crown`. Do not assume a Juggernaut now drops its borrowed crown: the new Juggernaut rule releases only its mace, with original borrowed equipment restored through the loadout system. `/events winner <player>` is for eligible manual races, not the automatic Juggernaut/Warlord rewards.

Capture Points remains an unavailable menu entry awaiting gameplay setup. It is not a playable event.

## 8. Four-hour loot drops and PvP Hour

Loot drops run every four hours when their automatic schedule is enabled. Use `/util` > Loot Drops or `/events` > Loot Drops. Commands are `/events loot start`, `/events loot status` and `/events loot auto on|off`. A manual drop resets the next automatic deadline to four hours later.

Each public chest contains all of these, randomized within the ranges:

| Item | Amount |
| --- | --- |
| Enchanted golden apples | 1 to 2 |
| Netherite ingots | 1 to 2 |
| Netherite upgrade template | 1 |
| Nether wart | 8 to 32 |
| Golden apples | 4 to 16 |
| Emeralds | 2 to 64 |
| Diamonds | 16 to 64 |
| Unenchanted diamond tool, weapon or armor piece | 1 random item |

The chest spawns in a circular band 500 to 1,500 blocks from saved world spawn, inside the border. Chat announces its coordinates, accompanied by a title and dragon growl. It is an ordinary public chest and can be looted or broken. Unlike death chests, it has no ten-minute expiry. Missed schedules do not create a burst of catch-up chests after downtime.

PvP Hour is manual-only: `/events pvp start`, `/events pvp stop`, `/events pvp status`. It enables the locator bar for up to one hour. It does not mean PvP damage is disabled outside that hour. Grace and spawn protection are separate systems.

## 9. Spawn, dimensions and server launch

`/setworldspawn` or `/setspawn` sets the teleport/fallback respawn point. It does not create protection. Valid beds remain the player's respawn destination when valid; `/spawn` still leads to server spawn.

`/spawn area` begins selection. Left-click one corner block and right-click the opposite corner. The saved region covers the horizontal rectangle at every altitude, including above and below building limits. `/spawn area cancel` cancels a pending selection. A new selection replaces the old protected region.

Ordinary players cannot build, grief or PvP inside this area. Attacks across its boundary are blocked in either direction, including projectile and potion routes. Combat-tagged players cannot enter by walking, flying, wind charges, vehicles or teleports. Near the boundary they see a private red-glass barrier, and attempted entry pushes them outside with a Knockback II-style impulse. These are visual blocks, not permanent terrain.

Before launch, `/spawn border` temporarily confines the overworld to the selected spawn region's enclosing square. `/spawn border off` restores the previous border. Minecraft borders are square, so rectangular selections leave extra border space outside the protected rectangle.

`/smp start` records the one-time launch, announces THE CONQUEST SMP HAS STARTED with crossed swords and a dragon growl, restores the normal border and begins the 24-hour Booster-kit unlock delay. Repeating it or restarting the server does not reset that timestamp. Do not use it just to test the title if you intend to launch later.

Normal borders are 15,000 blocks wide, approximately -7,500 to +7,500 from a zero-centered border, in each standard dimension. This supersedes the earlier 30,000-wide request. The configured maximum player count is 499; this is not a performance guarantee.

Use `/util` > Dimension Access or `/dimensions` to open/lock the Nether or End. Locking sends ordinary players in that dimension to overworld spawn and blocks returning through portals, homes and teleports. Changes persist. Opening or locking produces a red title and dragon growl. Senior staff retain their dimension bypass.

## 10. The Ender Dragon

A normal Ender Dragon has 1,000 health points, or 500 hearts, under the five-times-health rule. The bonus is a separate persistent contribution, so reloading does not stack it or refill an injured dragon.

The name uses small caps with black, reddish-purple and purple colors. The native boss bar is solid purple because Minecraft does not support a true gradient boss-bar fill. While perched, only direct player melee damages it. Arrows can work while it flies. Beds, end crystals, anchors and other explosions cannot damage it. These encounter rules also apply to staff.

## 11. Roles, hierarchy and access

Highest to lowest:

1. Owner (`owner`)
2. Co-owner (`co_owner`)
3. Senior Admin (`sr_admin`)
4. Admin (`admin`)
5. SSER (`sser`, alias `ssr`)
6. Moderator (`moderator`)
7. Helper (`helper`)
8. Media (`media`)
9. Booster X2 (`booster_x2`)
10. Booster (`booster`)
11. Member (`member`)

These are saved Minecraft roles, not automatic Discord role synchronization. Assign with `/role set <player> <role> [reason]`. Roles appear in chat/tab; Media is bright pink, Booster tiers purple, and SSER shares Owner's blue/pink/white palette. There is one saved primary role per player.

| Role | Built-in access |
| --- | --- |
| Member, Booster, Booster X2, Media | No staff powers from the role. Player commands and the applicable perks remain available. |
| Helper | Warn, temporary mute up to one hour, and history. Must target lower ranks. |
| Moderator | Warn, temporary mute up to seven days, temporary ban up to seven days, history and permitted case reversals. Must target lower ranks. |
| SSER | Managed operator access, ScreenShare, moderation and management of lower ranks. No Admin-level broad gameplay exemption. |
| Admin | Managed operator tools and moderation, lower-rank management, plus senior gameplay bypass. |
| Senior Admin, Co-owner | Broad staff tools and senior gameplay bypass, still subject to rank targeting. |
| Owner / console | Highest administrative authority and hierarchy override. |

Managed SSER/Admin/Senior Admin/Co-owner operators retain their stored rank. They cannot use protected direct commands or supported menus against equal or higher staff, grant their own rank or higher, or promote themselves. Unknown commands, broad selectors, command wrappers, permission changes and OP grants are restricted by the command guard. ScreenShare menus also filter protected targets. This is not a guarantee that every future third-party plugin can be safely sandboxed.

Reversing a punishment normally requires appropriate capability and being above its issuer's protected rank. Staff can reverse their own cases when they still retain that capability. A manually assigned external OP outside the managed roles receives the existing owner-level override, so use `/role set` for staff assignment rather than casually giving raw OP.

Admin, Senior Admin, Co-owner and Owner bypass Conquest's personal spawn, combat, pearl, spear-recovery, mace-flight, Ender-storage, reward-deposit, custom-renaming, netherite, dimension, home-limit and Booster-claim restrictions. SSER does not receive this broad bypass, even though it is an operator. Operators still have the narrower combat-command exception and kit editing access.

Bypasses do not remove hierarchy checks, race balance, weapon damage caps, dragon encounter rules, public death-chest protection, inventory capacity or another plugin's cancellation. They do not disable GrimAC or vanilla world-border collision.

## 12. Booster kits

Booster can claim once every 72 hours. Booster X2 can claim once every 24 hours. Additional boosts do not create a faster tier. The owner assigns these roles manually; no Discord boost checking or automatic removal is performed.

Claims unlock 24 hours after `/smp start`. Both tiers use the same kit contents. `/kit` opens a preview, and claiming gives the contents directly, not a shulker box. Everything must fit in normal inventory. Otherwise nothing is delivered and the timer is not consumed; the message explains that there is not enough space.

Operators edit with `/kit edit`; place the template contents into its 27 slots and close it to save. Only one editor can be active. An empty template cannot be claimed. A shared last-claim timestamp survives rank changes and restarts. Upgrading uses 24 hours since the previous claim; downgrading uses 72. The four senior staff roles bypass eligibility and timing, but still need room for the contents.

## 13. What `/util` contains

`/util` and `/utils` open the integrated administration menu. Each button has its own permission; access to the menu does not by itself grant every action. Managed senior staff and SSER have extensive access, with target hierarchy checks.

1. Punishments: warnings, temporary/permanent mutes and bans, inventory clears, reasons and confirmations.
2. Player Management: online-player selection, heal/feed, clear effects, gamemode, inventory inspection/editing, Ender inspection, freezing, history, notes and reports.
3. Reports: review player reports and resolve them.
4. Inventory Rollbacks: restore saved inventory snapshots. This creates/restores items, so coordinate with death-chest recovery to avoid accidental duplication.
5. Announcements: send configured title announcements.
6. Grace Period: temporarily protect players from PvP under the grace rules.
7. Vanish and Staff Mode: moderation visibility and staff tools.
8. Key All: distribute copies of deposited items.
9. Server Lockdown: restrict admission while staff work.
10. Chat Controls: clear, lock, slow mode, filtering and staff chat.
11. Restart Manager: manage restart countdowns.
12. Performance Dashboard: view metrics and preview supported cleanup actions.
13. Audit Logs: review staff actions and export records.
14. Dimension Access: lock/open Nether and End.
15. Loot Drops: manual spawn, automatic schedule toggle and status.

`/report <player> <reason>` is public. `/reports`, `/staffmode`, `/staffchat [message]` and `/sc [message]` require their corresponding permissions. `/util reload` reloads utility configuration. Some staff actions prompt privately for a reason or duration; follow the prompt rather than typing a public chat message.

Built-in moderation routes include `/warn`, `/warnings`, `/unwarn`, `/tempmute`, `/mute`, `/unmute`, `/tempban`, `/ban`, `/unban`, `/kick`, `/history`, `/case`, `/gamemode` and read-only `/invsee`. Use `/tr help` for argument help. `/tr doctor` checks role/storage/pack setup; `/tr pack resend` offers the pack again.

Important permission groups: `serverutil.*` contains separate utility permissions; `shocksmp.events.admin`, `.kit` and `.reward` control events; `biomeraces.admin.set`, `.reset` and `.reload` control races; `conquest.world.admin` controls world tools; `conquest.leaderboard.admin` controls placed boards; `conquestsmp.launch` controls launch. The old `shocksmp` names remain deliberately for compatibility and do not restore Shark powers. Most admin command nodes default to operator. Helper and Moderator role capabilities do not automatically grant every ServerUtil node.

## 14. Sidebar, tab and floating leaderboards

The regular Conquest sidebar shows kills, deaths, current streak, ping and playtime, with Combat and Other sections. It uses the Conquest purple style. Playtime progresses through seconds, minutes, hours and days. Event sidebars take priority during scored events.

The tab list includes the Conquest logo after the Java pack loads, role/King presentation and purple status text. The server-list description uses small caps with scrambled side characters. Rendering can vary with client fonts and available width.

Place a floating board at your position using one of these:

* `/leaderboard set kills`
* `/leaderboard set deaths`
* `/leaderboard set streaks`
* `/leaderboard set playtime`

Each type has one saved location; setting it again moves it. `/leaderboard remove <type>` removes it. The footer is near the administrator's feet and the rows extend upward. The viewing distance is 120 blocks maximum, checked once per second. Server display tracking must be at least 120 blocks and chunk view distance at least eight chunks; client settings may reduce visibility. Text faces each viewer and is hidden by intervening players and blocks; the board does not follow a player around the world.

Boards show ten ranked entries and each viewer's own stat below. Kills, deaths and playtime use the same native records as the sidebar. Offline records import gradually after startup. Flat skin portraits sit beside player names and refresh when players join.

Streaks show the highest completed run in gray and a current active run in purple. The same player can occupy two rows: for example, a completed 50 and an active 30. If 30 ends, it disappears and 50 remains. A completed 60 replaces the old completed 50. Every ten kills produces a milestone announcement. Old completed streaks cannot be recovered if the prior version already reset them before record tracking existed.

## 15. Other integrations and remaining restrictions

GrimAC handles supported movement and reach checks. The configured policy blocks automatic punishment commands while allowing correction/setbacks. `/conquestac` shows integration status. A setback is not an automatic ban. False positives are still possible, especially with latency or custom movement, and staff should investigate reports.

Mod Detector reports detectable client signals without the configured automatic kick behavior. `/moddetector check <player>` and `/moddetector list` provide staff information. It cannot guarantee a complete list of hidden client mods. XMMForceFairPlay requests Xaero's fair-play mode, disabling supported entity radar/cave features; modified or unrelated minimaps may ignore it.

DisableDebuffAndTipped 1.1.0 is enabled. Other harmful potion items become water bottles and tipped/spectral arrows become ordinary arrows. Turtle Master variants and Weaving potions are exempt from the potion restrictions. Weaving has an eight-minute base duration; distant splash hits can be shorter and lingering applications use vanilla scaling. `/potionlimiter` is its admin menu. Race-applied debuffs are not potion bottles.

ShieldStunPatch is installed. Clumps, SkinsRestorer, JustTPA, Back, Slash String, Geyser/Floodgate and the existing support plugins remain separate integrations. The presence of ShieldStunPatch does not mean its exact timing was manually tested during this release.

Conquest checks official Geyser/Floodgate updates periodically, verifies downloads and stages them for the next normal restart. It does not force an immediate restart for these updates. Protocol compatibility depends on upstream support.

WorldGuard, anti-xray, anti-seed-cracking, CoreProtect and existing support plugins may impose their own rules. CustomRecipes retains custom recipes for golden apples, anvils and cobwebs. This guide is not a claim that every third-party JAR has been exhaustively audited. Retired Shark powers/crystals, Shark Anti-Cheat Bridge, JustCombat and AuthMe remain disabled.

## 16. Owner setup and maintenance checklist

1. Set the teleport point with `/setworldspawn`.
2. Select protection with `/spawn area`; test both sides of its boundary with a non-admin account.
3. Before launch, optionally enable `/spawn border`.
4. Prepare the shared Booster template with `/kit edit`.
5. Assign staff and Booster roles with `/role set`.
6. Place the four leaderboard types where players should view them.
7. Review `/events` rewards before starting Egg Hunt or Assassins.
8. Use `/warlord end` for the animation preview without creating real loot.
9. Run `/smp start` only at the actual launch.
10. Back up worlds, player files and plugin data together before updates or rollbacks.

Race balance lives in `races.yml`; combat and spear timers in `combat.yml`; boss gear/events in `events.yml`; loot in `loot-drops.yml`; general settings and weapon caps in `config.yml`; kit contents in `booster-kit.yml`. Preserve databases, player data, home/spawn records, leaderboard data, death-chest records and reward/hunt records. Do not delete saved state to change presentation settings.

Use the supported reload command where provided. For general code or configuration changes without a specific reload path, stop and restart normally. Keep one active Conquest JAR. Do not install standalone BiomeRaces alongside the combined plugin.

The 3.17.0 build passed 279 automated tests with no failures or skips, plus its packaged SQLite check. Live checks are recorded separately. Automated checks and successful startup do not constitute a Minecraft-client playtest of visuals, two-player combat or all third-party interactions.

## Death animation previews

Admins can use `/juggernaut animation` and `/warlord animation` in game to preview the death sequences at their location. These do not kill anyone, end an event, start a hunt or issue rewards. Nearby players can see the visual effects.

Warlord music: The Dread by Kevin MacLeod (incompetech.com), licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). The first 13 seconds are used with fades. Java players receive it through the server pack; Bedrock uses a vanilla fallback.
