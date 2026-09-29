# Conquest SMP 3.20.1

Fixes NPC placement reporting success without spawning a villager until restart. Locations are now stored as traversable Bukkit configuration sections immediately, rather than opaque maps. Save-failure rollback retains the previous location section.

A regression test reproduces the missing placement on 3.20.0 and passes with the fix. It checks immediate location access, saved reload and removal. Full build: 316 tests passed, zero failures/errors/skips. Existing saved locations remain compatible.
