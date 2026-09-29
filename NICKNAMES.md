# Nicknames in Conquest SMP 3.18.0

`/nick <username>` copies an existing Java Minecraft account name and skin for the current session. Booster, Booster X2, Media, Helper and all higher staff roles can use it. Operators also qualify. The existing second booster rank is called Booster X2, not Booster Plus.

`/nick reset` cancels any pending lookup and restores the original name and skin. Leaving the server, stopping the plugin, losing the required rank, or the real owner of the nickname joining also restores the original profile. Nicknames do not persist through logout or restart.

`/nick check` shows all online nicknames and real names to Helper and higher staff, operators and console. `/nick check <name or UUID>` inspects one online player. Rank badges remain the actual account's badges. These commands do not grant the copied account's permissions.

Names must be existing Java accounts with 3 to 16 letters, digits or underscores. Duplicate nicknames and names belonging to online players are rejected. Profile lookups run asynchronously with a 15-second timeout. The default lookup cooldown is 10 seconds, configurable under nicknames.lookup-cooldown-seconds. Combat command restrictions still apply.

The authenticated UUID and all player storage remain unchanged. This includes inventory, homes, races, roles, punishments and statistics. The profile is refreshed using Paper's player profile API. No UUID is borrowed from another account. Third-party client mods that obtain MC Tiers from the real UUID will still display the real account's tiers. Tier copying is not implemented, and no external ranking is changed. A named tier-display integration must be identified before adding support for its display lookup.

Verification: automated tests cover rank eligibility, invalid names, profile application with stable UUID, reset, real-owner join collision, joins during lookup, pending cancellation, lookup failure, staff checks and logout restoration. Live client skin rendering and third-party tier mod behavior require in-game testing.
