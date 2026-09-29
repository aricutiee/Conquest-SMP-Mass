# Conquest SMP 3.19.0 verification

301 automated tests passed, zero failures/errors/skips. Full build and packaged SQLite check passed. New tests cover small-caps output, named/hex colors, authorization, invalid sizes, persisted changes, reload and removal.

Deployed to BisectHosting and restarted on 2026-09-29. Server reached Online at console 04:15:52 with startup time 16.836 seconds. Console version command confirmed ConquestSMP 3.19.0, author ari. The setword list command returned No floating words have been created, confirming the new command was registered and active.

Previous JAR retained as ConquestSMP-3.18.1.jar.disabled. JAR SHA256: 130046df41343b2349b9f3f303d501a175e442de600ca99c47fa045863f90fb2. Source commit a83f239.

Not live-tested: visual appearance, player placement, camera-facing rendering or chunk unload/reload with a real Minecraft client. No demonstration labels were placed on the user's server. Resource pack unchanged.
