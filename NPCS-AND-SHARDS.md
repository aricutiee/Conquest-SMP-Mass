# NPCs, shards and AFK rewards

Version 3.21.0, Paper 1.21.11.

## Place your NPCs

Stand where each villager should appear and use one of these commands:

* `/npc set utilities`
* `/npc set discord`
* `/npc set races`
* `/npc set rtp`
* `/npc set reroll`

There is one saved placement of each kind. Running set again moves that NPC. `/npc list` shows placements and `/npc remove <kind>` removes one. Villagers have bright small-caps labels, decorative floating items and turn toward nearby players. They cannot be damaged or moved normally. NPC locations persist across restarts. Unloaded chunks are not force-loaded.

The Discord NPC privately sends the clickable invite https://discord.gg/hn23SeVzh3 with purple gradient small-caps text. The Races Guide opens an information menu with hover descriptions of the five races and an explanation of Dragonborn. RTP runs the existing `/rtp` command with its normal restrictions. NPC interactions and purchases are unavailable in combat.

## Random race rerolls

The Reroll NPC opens a confirmation menu. Confirming costs 250 shards and draws one random base race using the configured race weights. Players cannot select the outcome; the same race can be drawn again. It plays the existing full five-second animation and applies the saved result. This is not a preview. Dragonborn cannot be purchased or selected. Holding the Dragon Egg blocks a purchase.

Payment and the pending random result are saved together before the animation. Disconnecting, dying or restarting during the roll resumes the same paid result without another debit or draw. Existing combat cooldown expiry times remain intact.

## Shards and AFK zone

`/shards` displays your balance, also shown under Other on the normal sidebar. Administrators can use `/shards give <known player or UUID> <amount>`.

Use `/afk`, left-click one corner block, then right-click the opposite corner. The box includes both selected heights, unlike the infinite-height spawn region. This area is separate from spawn and adds no protection. `/afk cancel` cancels selection and `/afk off` disables rewards.

An online, alive, authenticated, non-spectator player in the zone earns one shard every ten seconds. Leaving or teleporting resets that interval. There are no offline rewards or catch-up payments.

A credited player kill earns 25 shards. For each attacker/victim pair, only the first three kills in a 72-hour window pay. That window starts with the first rewarded kill and is saved across restarts. Self-kills do not pay. Killing other players uses their separate pair limits.

Balances and kill windows are saved in race-players.yml. The first upgrade from an older balance-free file creates race-players.yml.pre-shards as a backup. AFK selection is saved in afk-zone.yml.

## Edit NPCs and shop offers

* `/npc edit <kind> title <words>`
* `/npc edit <kind> description <words>`
* `/npc edit <kind> color <color or hex>`
* `/npc edit discord invite <Discord HTTPS invite>`
* `/npc edit utilities` opens the 45-slot stock editor. Arrange item templates and close it to save.
* `/npc edit utilities price <slot 1-45> <shards>` sets the price for the complete stack in that slot. Zero disables purchasing it.

The starting supplies are packed ice, wind charges, obsidian and chorus fruit, all unavailable until you set prices. Prices are attached to slots, so review prices after moving stock. Only one administrator can edit stock at a time. Offers are unlimited stock templates, not a finite warehouse. Customers need room for the entire displayed stack; insufficient space or shards causes no charge. Changed offers refresh before purchase. Player inventory is saved after a purchase, but a machine failure during the separate currency and inventory saves is not a fully transactional operation.

The permission `conquest.npc.admin` defaults to operators. Admin, Senior Admin, Co-owner and Owner also have management access. Configuration is saved in npcs.yml.

## Spawn and sky labels

Players inside the existing selected spawn area cannot push one another. Collision settings and previous scoreboard team memberships are restored when leaving; players already marked noncollidable stay noncollidable.

Sky-word commands use their actual text, not word1. For example `/setword size Welcome Home 50`, `/setword edit Welcome Home red The Arena`, or `/setword move Welcome Home`. The allowed scale is 1 through 50. Old internal IDs still work for compatibility.

## Verification

The complete Gradle build and automated suite pass. New checks cover shard persistence and rollback, paid-reroll recovery and cooldown retention, repeat-victim limits and expiry, AFK timing and height bounds, full-inventory shop rejection, copied scoreboard collision-team restoration, and sky-word text selection and size 50. NPC appearance, menus, in-game animation and multi-client pushing still require a Minecraft client check; they are not claimed as visually verified.


## AFK display and visuals, 3.21.0

While eligible inside the AFK zone, a purple boss bar says AFK and counts down to the next shard. A purple action-bar line above the hotbar shows shards earned in this session and elapsed AFK time. Each reward is one shard every ten seconds of elapsed time. Leaving, teleporting, dying, disconnecting, disabling/replacing the zone or stopping the plugin ends the session and removes its display. Spectators and unauthenticated players do not earn. A delayed server tick awards at most one shard, with no catch-up burst. Only successfully saved rewards count toward session earnings.

NPC decorative items orbit at a radius of 0.85 blocks, centered 1.5 blocks above their feet, with gentle 0.15-block vertical bobbing. Client interpolation smooths each half-second update. Existing locations, types and icons remain unchanged.

Sidebar labels and numbers now match /setword purple (#B477FF). The Conquest SMP heading uses a vertical bitmap gradient, light purple at the top and dark purple at the bottom. This requires accepting the updated server pack; clients without it keep the readable text fallback. The Tab player list is unchanged.
