# Conquest SMP 3.18.1 deployment verification

Deployed through the BisectHosting file manager and restarted on 2026-09-29. Startup completed at console 04:02:00 in 17.960 seconds. The console confirmed ConquestSMP 3.18.1, author ari, and logged: Nickname tier identity bridge enabled (PacketEvents). PacketEvents 2.14.0 was enabled before ConquestSMP.

The previous JAR is preserved as ConquestSMP-3.18.0.jar.disabled. Resource pack and player databases were not replaced.

Build successful. All 297 automated tests passed, zero failures, errors or skipped tests. Packaged SQLite smoke test passed. PacketEvents classes are not bundled in the plugin JAR; the installed plugin provides them.

JAR SHA256: 0df237df774b95ba20a255ef4943fbdb13456984d6c380dde01a4cb5b4bbed07.
Implementation commit: 2dca95e in aricutiee/Conquest-SMP-Mass.

Not live-tested: the TierTagger/Tiers client rendering, signed chat after a disguise, observer reconnects, two-player owner-login collision and Bedrock visuals. Automated checks exercise packet-entry mapping and lifecycle behavior, not an actual Minecraft client. The intended copied ranks are for other Java viewers. Self-view keeps the real UUID. See NICKNAMES.md for the in-game checklist.
