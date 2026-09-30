# Conquest SMP 3.24.8

Homes and teleport warmups read current Conquest ranks and additional Booster benefits on each request. Rank changes during a countdown preserve elapsed time and recalculate the remaining wait. Higher home limits and shorter waits win when benefits overlap.

| Rank | Homes | Home / spawn / RTP wait |
|---|---:|---:|
| Member | 2 | 30 seconds |
| Coal | 4 | 25 seconds |
| Iron | 6 | 20 seconds |
| Redstone | 8 | 15 seconds |
| Diamond | 13 | 10 seconds |
| Netherite | 23 | 5 seconds |
| Booster | 4 | 20 seconds |
| Booster X2 | 7 | 10 seconds |

Existing staff gameplay bypass retains unlimited homes and immediate teleports. Media and Helper retain six homes and the normal 30-second wait, improved by any additional Booster benefits.

Use /sethome <name>, /home <name>, /homes, and /delhome <name>. A demotion prevents adding homes above the current limit but does not delete existing homes. /role info <player> now reports home limits and teleport waits alongside AFK and /string benefits.

/home, /spawn, /worldspawn, and /rtp share a purple countdown. Walking, taking damage, entering combat, dying, logging out, or teleporting elsewhere cancels the pending request. Looking around is allowed. Only one teleport can be pending at a time, including destination loading. RTP remains restricted to the Terralith Overworld and searches for safe ground within its current border after the countdown. Terrain loading can add a short delay.

First-time players spawn at the exact /setworldspawn location, preserving its horizontal facing but with pitch set to zero, so they look forward. Returning players retain their saved location. Bed respawns and forced evacuation from locked dimensions retain their previous behavior.

Verification: automated tests cover command routing through all three warmups, rank and combined benefit changes, home preservation on demotion, movement and damage cancellation, stale request invalidation, and first-join versus returning-player placement.
