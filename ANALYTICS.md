# Conquest analytics, 3.24.5

Private server-side collection starts at /smp start and pauses at /smp start cancel. If the SMP has already started when installed, collection begins at installation; no history is invented. Prior epochs stay in the raw archive; reports default to the latest epoch.

Records real Bukkit joins and disconnects, minute presence and AFK-zone samples, attributed PvP kills/deaths, event preparation/running exposure, verified Juggernaut/Warlord damage, key-all recipient processing, booster-kit claims and configured event-prize delivery. Supply drops are marked as online exposure. Other custom activities can be labeled with /analytics mark <label>. Uninstrumented rewards and events are not inferred.

Owner-only player commands: /analytics, /analytics export, /analytics mark <label>. Server console can export. Data stays in plugins/ConquestSMP/analytics. The private salt remains on the server. No IPs, names, chat or inventory data is collected. Staff/OP are excluded by the report; NPC metadata and analytics.excluded-uuids exclude simulated users.

Report generator: analytics-report.py (Python plus tzdata on Windows). Produces Obsidian markdown and thirteen purple SVG charts for Today, Last 7 Days, This Week, Last Week, Since Launch and timestamped daily snapshots. Incomplete cohorts show pending. Crash/restart sessions are censored. After-action charts are associations, not causal claims.

Collection is append-only and asynchronously flushed. Queue capacity is 100,000 observations; /analytics reports drops and write errors. Export is a consistent writer-queue snapshot. Inspect storage growth periodically; there is no automatic deletion of history. A four-hour Codex heartbeat retrieves data through the BisectHosting UI and runs the report generator. It requires this PC/Codex automation and signed-in hosting access to be available; collection on the game server continues independently.

Validation: complete Gradle build/test suite passes. A synthetic report fixture verifies staff exclusion, censored sessions, four-hour sessions, pending retention, post-death exits and all thirteen chart outputs. In-game telemetry across multi-day cohorts requires actual elapsed time.
