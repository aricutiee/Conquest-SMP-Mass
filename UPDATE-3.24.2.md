# Conquest SMP 3.24.2

## Staff history

Run `/staff logs` in game. Current Conquest staff from Helper through Owner appear as player heads, including offline staff. Each head shows their real stored username, current rank, all-time action count, past-seven-day count and today's count. Click a head to choose All logs, Past 7 days or Today, then browse pages of recorded warnings, temporary/permanent mutes, bans and kicks.

Entries include the target, reason, timestamp, expiry when present and whether the action was revoked. Revoked actions remain counted as actions performed. Counts combine the Conquest moderation-command database and the integrated Utilities moderation database. They do not reconstruct unrecorded actions issued through unrelated third-party or vanilla namespaced commands. System/console actors and inventory clearing are excluded. No historical databases are changed.

Staff ranks and operators may view this read-only menu. Members cannot open it. Names remain real usernames even when a player uses /nick. Reads run asynchronously over independent read-only SQLite connections. Menus prevent item extraction, and recheck access before opening or navigating. Today uses Africa/Casablanca midnight; `staff-logs.timezone` in config.yml can select another valid IANA time zone. Past seven days is a rolling 168-hour window.

## Totems and enchanted golden apples

Players can carry at most two accessible Totems of Undying across their inventory, offhand and cursor. Picking up or transferring another from a container is blocked. Existing excess totems and externally granted overflow drop at the player's feet with a short pickup delay; they are not deleted. Totems inside stored shulker boxes are not recursively counted until taken out.

Players may carry any number of enchanted golden apples. Successfully eating one starts a real-time 60-second cooldown, displayed in a purple boss bar. Attempts during the cooldown are cancelled without consuming the apple. Ordinary golden apples are unchanged. The cooldown is saved in player data and survives reconnects and server restarts.

The existing Moderator, SSER, Admin, Senior Admin, Co-owner and Owner gameplay bypass remains in effect for both restrictions.

This release also includes all changes described in UPDATE-3.24.1.md: concise Conquest AC reports, ModDetector log integration without new join restrictions, branded player plugin listings, Happy Ghast attributes and combat dismounts, and mutual public-chat truces with a tip on one third of fresh combat entries.
