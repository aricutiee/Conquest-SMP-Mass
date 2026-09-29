# Conquest SMP 3.20.1 deployment

Deployed and restarted on 2026-09-29. The live version command confirmed 3.20.1. Server reached Online at console 05:09:21. Old JAR retained as ConquestSMP-3.20.0.jar.disabled.

Saved reroll NPC placement was retained at overworld X 284.137, Y 71, Z 82.085. At 05:09:57, a read-only entity query confirmed an Armorer villager at those exact saved coordinates. This verifies actual entity creation on the live server, not just a placement message. Client rendering and right-click menus were not directly observed.

316 automated tests passed. The new regression test failed before the patch and passes after it, checking that placement is readable immediately without restart, survives reload and can be removed. Full build and packaged SQLite smoke test passed.

SHA256: 9f5786ccafb487dfe0a4ba2a5fc2eb6bc0c7b700e685c1bdc5e14bc4690ed197. Source commit d1fb0f1.
