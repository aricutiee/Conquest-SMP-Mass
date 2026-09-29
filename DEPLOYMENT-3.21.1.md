# Deployment 3.21.1

Deployed to BisectHosting on 2026-09-29, with previous running version 3.20.1 retained disabled. The staged 3.21.0 JAR was disabled before first startup to include the subsequent TNT request in 3.21.1. The server reached Online at console 05:26:03 and the version command confirmed ConquestSMP 3.21.1 at 05:26:29.

320 automated tests pass with zero failures, errors or skips. Full compilation, generated resource pack and packaged database smoke checks passed. Tests cover ten-second AFK timing and session resets/totals, no catch-up payouts, orbital bounds, sidebar colors and gradient endpoints, TNT minecart player immunity, ordinary TNT reduction, cancelled damage and unaffected mob damage.

No destructive explosion test was run on the live server. AFK HUD, updated sidebar and orbiting items were not viewed through a Minecraft client; their appearance remains an in-game check. Existing NPC and AFK configuration files were preserved.

JAR SHA256: 725208d5778118185ca5547283f3e5c9314d72e8a46bb089a70e1d6a148eefa1.
Embedded resource pack SHA1: dc5b52a4ccd0e820f4e8ac26b7efdb7aa980bdb1.
Source commits: 11ff6d7 and 308f60c, published to aricutiee/Conquest-SMP-Mass.
