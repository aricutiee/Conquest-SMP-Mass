# Conquest SMP 3.17.4

Added admin-only /juggernaut animation and /warlord animation with tab completion. Both commands run at the issuing player. Warlord reuses its existing preview. Juggernaut uses the actual rise-and-shake sequence followed by a non-collectible display fall and cleanup. No event state is changed and no reward is issued. Juggernaut previews are limited to one concurrent sequence per issuing player and cleaned up on shutdown.

Leaderboards now have a 120-block configured maximum, with an automatic one-time migration from the previous 24-block setting. Display view range is increased, while the per-viewer distance gate removes boards beyond 120 blocks on the next refresh. Live server display tracking is 128 blocks, broadcast percentage 100, and view distance 10 chunks. Client settings may still shorten visibility.
