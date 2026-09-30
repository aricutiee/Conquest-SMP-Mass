# Conquest SMP 3.24.7

Races now have distinct class identities. Core ability amounts and durations are generally 50% stronger, with a 10-second offensive proc cooldown instead of 12 seconds. This is a balance redesign, not a blanket multiplier on every effect.

| Race | Class | Abilities |
|---|---|---|
| Bogborn | Offense | 10% extra melee damage before armor, Poison I for 3 seconds on the third charged hit, underwater breathing, 30% water guard every 20 seconds |
| Rootbound | Defense | Permanent 10% melee/projectile reduction, an additional 15% guard and 75% knockback reduction every 20 seconds on any terrain, Slowness I for 2.25 seconds on the third charged hit |
| Petalfolk | Healing | 50% stronger food regeneration, 0.75-heart third-hit heal, Absorption I for 6 seconds after surviving a hit every 24 seconds, 30% less fall damage |
| Hollow-Eyed | Movement | Permanent Speed I, low-light Night Vision, Darkness for 1.5 seconds on the third charged hit, 30% low-light guard every 20 seconds |
| Dwarf | Passive utility | Permanent Haste I, up to 6 hearts of fall prevention on stone/deepslate every 24 seconds, 0.75-heart third-hit bonus before armor |

Offensive procs still require three charged melee hits against the same eligible target, with an 8-second chain timeout. Existing weapon damage caps remain. Rootbound's two reductions multiply, giving 23.5% total reduction on a guard hit. Dragonborn remains an egg-based override with the existing power level, and now works from any player inventory slot. An egg inside a container or bundle does not count.

The first boot backs up `races.yml` to `races.yml.pre-classes` and migrates the relevant balance settings once. Saved races, shards, rolls, and existing cooldowns remain.

## Commands

* `/race set <player> <bogborn|rootbound|petalfolk|hollow-eyed|dwarf>` works for operators or the existing race admin permission. Dragonborn stays egg-based.
* `/shards set <player> <amount>` sets a known player's balance, including zero.
* `/shards give <player> <amount>` adds shards.
* `/shards giveall <amount>` or `/shards give all <amount>` grants shards to authenticated online players. These commands require existing shard administration access, including operators.
* `/role info <player>` now shows the effective string cooldown and AFK shard interval.

## Rank timers

| Rank | /string cooldown | AFK seconds per shard |
|---|---:|---:|
| Member | 60 | 30 |
| Coal | 50 | 25 |
| Iron | 45 | 20 |
| Redstone | 40 | 15 |
| Diamond | 30 | 10 |
| Netherite | 15 | 5 |
| Booster | 50 | 20 |
| Booster X2 | 30 | 10 |

Additional Booster benefits use the faster rate. Existing staff string exemptions remain. A rank change recalculates an active string cooldown from its original use time; AFK rank changes retain elapsed progress toward the next shard. No lag catch-up rewards are generated.

## Event fixes and badges

* The active Juggernaut bypasses the spear lunge recovery gate. Other players and the Warlord retain their existing rules.
* A mace's glow follows actual inventory possession. Dropping or transferring it stops its previous holder's effect. The original three-minute deadline remains shared across transfers. External glow effects are preserved.
* Coal badges are dark gray, Iron white with dark text, Redstone light red, Diamond light blue, and Netherite near-black. Each has its matching Minecraft item texture on the left.
* Item texture source: [Mojang's Bedrock samples](https://github.com/Mojang/bedrock-samples/tree/main/resource_pack/textures/items). The bundled pack updates automatically when self-hosting is enabled.

The build tests gameplay calculations, egg inventory activation, mace transfer and expiration, external glow preservation, shard persistence and command access, rank timer changes, and packaged database loading. Live visual balance still benefits from playtesting.
