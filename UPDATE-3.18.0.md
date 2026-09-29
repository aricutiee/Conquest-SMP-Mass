# Conquest SMP 3.18.0

Adds rank-gated `/nick <username>`, `/nick reset` and staff `/nick check [player]`. Copies an existing Java account name and skin while retaining the original UUID and saved progress. Resets an active disguise when the actual account joins, and rejects occupied usernames and concurrent duplicate aliases. Async lookups cannot apply after reset, logout, combat entry, rank loss or shutdown. Names reset on logout and shutdown.

MC Tiers client-mod UUID impersonation is not included. The real UUID remains unchanged. No server-side tier plugin was identified. See NICKNAMES.md for behavior and verification limits.
