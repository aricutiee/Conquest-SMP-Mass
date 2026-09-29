# Conquest SMP 3.18.0 verification

289 automated tests passed with zero failures, errors or skipped tests. Packaged SQLite smoke test passed. New tests cover rank gates, name validation, copied profiles retaining real UUID, reset, real-owner join, lookup/join race, pending cancellation, failed lookups, staff checks, logout and duplicate aliases.

Deployed through BisectHosting and restarted. Live console reported ConquestSMP version 3.18.0, author ari. The plain `nick check` command returned the Conquest response: No players are using a nickname. Server reached Online at console 03:34:47 on 2026-09-29. Prior JAR retained as ConquestSMP-3.17.5.jar.disabled.

JAR SHA256: 4cfafb377b68b2dbf32c6b25a7eca3e0884f8525cf98d7c593b1ff6f33ad30e7.
Source commit: 517b28c in aricutiee/Conquest-SMP-Mass.

Not live-tested: client skin appearance, two-player collision reset, Bedrock rendering, MC Tiers client mods. Real UUID stays unchanged; MC Tiers copying is not implemented pending identification of the display mod/integration. No inventory or permission migration is performed.
