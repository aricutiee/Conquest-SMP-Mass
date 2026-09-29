# Deployment 3.24.3

Deployed September 29, 2026 through the hosting panel. The server stopped cleanly, the prior JAR was retained as ConquestSMP-3.24.2.jar.disabled, and the new upload succeeded. Paper completed startup and the panel returned Online.

368 automated tests passed, with no failures, errors or skipped tests. Coverage includes every clickable catalog entry, plugin-command aliases, stored Owner eligibility, simulated target requests, combat denial and real-player precedence. The packaged SQLite smoke test passed.

No simulated players are created automatically on a first installation. Player-client tab and AFK body rendering still require an in-game check. Console diagnostics remain available; fakeplayer management is intentionally refused to console because it requires an in-game Owner.

SHA-256: 0eb4c890b591e3ede896e27b2c3e9b469c396d9cb56c83df815dfd3c03da5731
