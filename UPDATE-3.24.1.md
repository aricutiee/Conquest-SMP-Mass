# Conquest SMP 3.24.1

Author: ari

## Simple Conquest AC reports

Use `/conquestac <player>` or `/conquestac inspect <player>`. Staff see one verdict, an alt-account result, recent cheating flags and suspicious mod names. Results distinguish REVIEW NEEDED, NOTHING SUSPICIOUS DETECTED and CHECK INCOMPLETE. Absence of a flag is not proof that a player has no cheats or alternate accounts. Suspicious minimap/policy matches do not automatically become a cheating verdict.

Click Details or use `/conquestac details <player>` for saved observations and ModDetector's latest suspicious signals. The old `alts` and `client` subcommands now open the same simple report. Real account names and UUIDs are accepted. Reports remain staff-only and recheck permission before displaying asynchronous results.

ClientPolicy provides current announced client information. ModDetector 1.0.1's existing local detection log is read asynchronously, with no new probes, commands, punishments or join restrictions. Findings from before a player's current login are dated as earlier signals. CHEAT, SUSPICIOUS and TRAP categories are shown as suspicious signals, not proven installed cheats. Ordinary UTILITY and launcher entries are not labelled suspicious by ModDetector's results. ClientPolicy's configured denied-mod matches are also shown.

The reader examines at most the last 4 MiB, ignores unfinished lines and explicitly reports partial coverage or an unavailable log. Old logs and detections are not uploaded to GitHub. Alt and client summary evidence cannot be displaced by a burst of Grim flags. Grim summaries use the most recent flag within 24 hours. All existing correction rules and report-only enforcement settings remain unchanged.

## Branded plugin command

Every in-game player, including operators, receives the following overview from `/plugins`, `/pl` and their namespaced forms:

Conquest SMP Systems

Conquest SMP | Conquest AC | Conquest Integrations

The entries describe Conquest systems rather than enumerating installed third-party plugins. Plugin-command completions are filtered. The hosting console retains the actual diagnostic plugin list. This is presentation control, not a guarantee that clients cannot infer installed server software through other protocols or commands.

## Happy Ghasts and public-chat truces

Happy Ghasts gain 5x normal maximum health and 3x flying speed. The modifiers use stable keys so chunk loads and restarts cannot multiply them repeatedly. Current health percentage is preserved when applying the health increase. Existing loaded Happy Ghasts, newly spawned ones and newly loaded entities are covered.

Combat-tagged players cannot mount a Happy Ghast. Starting combat dismounts a rider immediately; the combat ticker also enforces the restriction. Existing stored staff-role gameplay bypasses remain consistent with other combat restrictions.

Both active opponents can agree to a truce by each sending a standalone public message: `my bad`, `mb`, `bro`, `mb og`, or `og`. Case, repeated spaces and ending punctuation are accepted. Muted, cancelled chat and private messages do not count. A phrase inside a longer sentence does not count. New damage cancels pending offers from both participants. Offers are not retained after logout or restart.

A mutual agreement removes only that pair's combat link. Any other active opponent or untracked pre-upgrade timer remains. Opponent timers persist alongside the existing combat timer. Roughly one out of three fresh combat entries shows the purple truce tip; subsequent hits while already tagged do not repeat it.
