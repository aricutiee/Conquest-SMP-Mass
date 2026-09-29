# Conquest SMP 3.20.0 verification

315 automated tests passed with zero failures, errors or skips. The full Gradle build and packaged SQLite check passed. Detailed coverage and client-test limitations are in NPCS-AND-SHARDS.md.

Deployed through the BisectHosting file manager on 2026-09-29. The old JAR was renamed ConquestSMP-3.19.0.jar.disabled while the server was offline. The panel confirmed successful upload of ConquestSMP-3.20.0.jar. The server restarted, reached Online, and logged Enabling ConquestSMP v3.20.0 at console 04:56:47. Races and the integrated modules enabled successfully. A player subsequently joined.

The live console command npc list returned all five service types, each not placed. The afk command returned Select the AFK area in-game, confirming that the unqualified command resolves to the new Conquest implementation. No NPCs or AFK zone were placed at guessed locations, and no balances were modified for testing. Existing labels and regions were retained.

JAR SHA256: 3b185f50837ee24cc29df20f000344c77aa2f42e545612041ca7ed54a369cc87. Implementation source commit 0855164, published to aricutiee/Conquest-SMP-Mass.

Not tested in a Minecraft client: NPC visual layout and right-click menus, shard shop purchases, the paid reroll animation, AFK selection/earning, and multi-client spawn collision behavior. Automated tests cover their underlying state changes as described in the guide. No claim of live visual verification is made.
