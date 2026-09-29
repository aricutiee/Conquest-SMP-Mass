# Conquest SMP 3.23.0

## Rank benefits

| Rank | Homes | AFK shard interval | Nicknames |
|---|---:|---:|---|
| Member | 2 | 30 seconds | No |
| Booster | 4 | 20 seconds | Yes |
| Booster X2 | 7 | 10 seconds | Yes |
| Coal | 4 | 30 seconds | No |
| Iron | 6 | 30 seconds | Yes |
| Redstone | 8 | 30 seconds | Yes |
| Diamond | 13 | 30 seconds | Yes |
| Netherite | 23 | 30 seconds | Yes |

Media retains its 6 homes and nickname access. Helper retains 6 homes. Administrative staff bypass gameplay home limits. Existing homes are retained after a downgrade, but new homes cannot exceed the current limit.

Use `/role set <player> <rank>` for a primary rank, for example `/role set Ari diamond`. Owner or console can additionally use `/role booster <player> 1` or `2` to add Booster benefits without replacing that primary rank. Use `0` to remove the additional benefit; a primary Booster rank must also be changed if you want to remove its benefits. Combined benefits use the highest home limit, not a sum. Booster kit intervals remain 72 hours for one boost and 24 hours for two.

AFK rank changes restart the current shard countdown at the new rate. Leaving the area resets the AFK session; accumulated currency remains saved. Kill rewards are unchanged.

Nicknames cannot copy a currently online account or an already-used nickname. The real account joining resets a conflicting nickname. Player data stays attached to the original identity.

## Spawn and appearance

Players cannot interact with protected spawn blocks, including chests, doors, buttons and pressure plates. Inventory-open checks also protect located containers. Staff bypass remains available, and NPC menus remain usable.

Utilities NPC headings, descriptions, offers and price text use purple small caps. Existing stock, prices and NPC locations are retained. The scoreboard address is CONQUESTSMP.NET in the small-caps custom font, slightly enlarged and retaining the darker purple. Clients without the Java resource pack receive a small-caps fallback at the normal client font size.

## Protected staff access

Moderator, SSER, Admin, Senior Admin and Co-owner have operator-equivalent permissions for supported administrative commands, with equal/higher staff target protection removed. Owner retains actual OP. The other staff do not receive actual OP, to protect owner-only access management.

Owner or console alone can assign or remove operator ranks and assign Owner. Non-owner player commands cannot use permission managers, op/deop, command execution wrappers, formatted command aliases, or unreviewed commands. NBT-bearing commands and multi-target selectors remain restricted to prevent indirect privilege changes. Staff can use normal named-player commands against each other and Owner.

Owner `/deop <player>` also removes a managed staff rank, setting it to Member, so its permissions do not return on login. Restore a staff rank with `/role set`. Existing standalone OP flags do not confer Owner rank and are cleared for non-Owners. Console `/role bootstrap <player>` remains the recovery route for assigning Owner.
