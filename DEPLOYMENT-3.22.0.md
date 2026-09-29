# Deployment 3.22.0

Deployed to BisectHosting on 2026-09-29. Server restarted and reached Online. Live version command confirmed ConquestSMP 3.22.0 at console 13:13:30, and smp returned Usage: /smp start [cancel] at 13:13:40. Previous 3.21.2 JAR retained disabled. Launch state, player claims and live world border were not changed during verification.

322 automated tests passed, including durable launch cancellation, fresh 24-hour unlock after restarting the launch, retained claim cooldowns, and border enabling/restoration. Full build and packaged database test passed. Live cancellation and shrinking the world border were left for the administrator's chosen timing.

SHA256: bb1bf1ca86b8ba49446f6ae71ff2cf267f08890c1684a9b682f17ce64e250e0a. Source commit c01a4e8.
