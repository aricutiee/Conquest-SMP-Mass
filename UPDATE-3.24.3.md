# Conquest SMP 3.24.3

The public `/plugins` and `/pl` catalog now contains 51 Conquest-branded features. Each name is clickable, with a hover summary, a detail view, Author: Ari and a return link. Eight entries per page keep the chat readable. The catalog includes custom gameplay, events, integrations and every main /util tool. Namespaced player plugin-list commands use the same catalog; the server console retains its actual diagnostic list. See CONQUEST-SYSTEMS.md for all names and descriptions.

## Owner-only simulated players

This feature is omitted from the public catalog. Only the stored Owner role can manage it, not merely an operator, wildcard permission holder or the console.

* `/fakeplayers 30`: set the simulated tab-profile count to 30.
* `/fakeplayers afk`: place approximately one third on separate safe, loaded ground positions inside the saved AFK area.
* `/fakeplayers afk off`: remove the world bodies while retaining tab profiles.
* `/fakeplayers clear`: remove every simulated profile and body.
* `/fakeplayers status`: see the current counts.
* `/fake players ...`: alternative spaced form.

Names are randomized Minecraft-style usernames. NameMC's list could not be accessed, so no scraped username pool is claimed. An optional `names` list in fakeplayers.yml supplies custom Java-format names after restart. UUIDs provide default Minecraft skins. Displayed pings vary from 10 to 180 ms and fluctuate slightly every 30 seconds. These numbers are cosmetic, not measured connections or location information.

The default count ceiling is 500. `fakeplayers.max-count` in config.yml can raise it to 10,000, subject to client/server performance. Vanilla tab rendering may show only a subset of large lists. This is not unlimited capacity. Profiles persist across restarts and are removed if a real player with that name joins. Client-side AFK bodies appear within 80 blocks in the correct world and cannot earn shards, receive items, take damage or consume login slots. Real online player counts are unchanged. Bedrock rendering requires an in-game Geyser check.

Messages and teleport requests directed to a simulated name are acknowledged to the sender and discarded. The simulated profiles never reply or accept. Combat restrictions still block requests. Real players and other command targets continue to their existing handlers. “TP assist” was interpreted as the existing /tpaccept, not a new command.

Configuration and player-menu display were tested automatically; actual client rendering requires an in-game check.
