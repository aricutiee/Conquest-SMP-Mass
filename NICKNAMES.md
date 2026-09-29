# Nicknames in Conquest SMP 3.18.1

`/nick <username>` copies an existing Java Minecraft account name and skin for the current session. Booster, Booster X2, Media, Helper and all higher staff roles can use it. Operators also qualify. The existing second booster rank is called Booster X2, not Booster Plus.

`/nick reset` cancels any pending lookup and restores the original name and skin. Leaving the server, stopping the plugin, losing the required rank, or the real owner of the nickname joining also restores the original profile. Nicknames do not persist through logout or restart.

`/nick check` shows all online nicknames and real names to Helper and higher staff, operators and console. `/nick check <name or UUID>` inspects one online player. Rank badges remain the actual account's badges. These commands do not grant the copied account's permissions.

Names must be existing Java accounts with 3 to 16 letters, digits or underscores. Duplicate nicknames and names belonging to online players are rejected. Profile lookups run asynchronously with a 15-second timeout. The default lookup cooldown is 10 seconds, configurable under nicknames.lookup-cooldown-seconds. Combat command restrictions still apply.

The authenticated UUID and all player storage remain unchanged. This includes inventory, homes, races, roles, punishments and statistics. Paper refreshes the cosmetic name and skin. The optional PacketEvents 2.14.0 bridge also sends the copied account's UUID in the visible profile and player entity sent to other Java viewers. For example, `/nick ItzRealMe` lets their tier mods look up ItzRealMe, provided that account has rankings on the selected tier service. No external ranking is changed and no rank is invented for unranked accounts.

PvPTiers' Tiers mod looks up the profile name. MCTiers' TierTagger looks up the profile UUID, which is why the packet bridge is necessary. The real profile remains available but unlisted for chat signature validation. Signed chat packets, their real sender UUID and signing keys are not rewritten. The copied visible profile never receives the real account's signing key. No authentication or secure-chat setting is changed.

The local player's own entity and Bedrock recipients keep their normal visible UUIDs. Your own mod's self-view or preview can therefore still show your actual tiers. The copied tiers are intended for other Java players viewing the disguise. Mod settings, API availability, caches and future mod updates can affect display. Without PacketEvents, or with `nicknames.client-tier-identity: false`, name and skin still work but UUID-based tier copying is disabled. Restart after changing that switch.

Real-owner collisions are checked at login before initial player-list synchronization, with a join check as a fallback. Both names and copied UUIDs are checked. Old client identities are removed before replacement, reset or shutdown. The bridge remembers each connection's last visible UUID so queued removals do not accidentally remove a newer disguise.

Verification: 297 automated tests passed, including profile mapping, stable server UUID, authentic chat-session retention, no signing key on the disguise, self/Bedrock exclusions, queued removal ordering, resets, unrelated players, rank gates, lookup cancellation and collisions. These are automated Java tests, not a live client-mod test. Live TierTagger/Tiers rendering, signed chat and two-client login/reset behavior still require in-game testing.

In-game check: from a Booster account run `/nick ItzRealMe`. Have a second Java player with the desired mod inspect tab and the name above the player, then send chat. Check `/nick check ItzRealMe` as staff. Run `/nick reset` and verify the original name, skin and tiers return. Inventory, homes and role should remain those of the real account throughout.

Source references inspected: [TierTagger UUID lookup](https://github.com/mctiers-dev/TierTagger/blob/6f49034c9f800f1da330efa90e5810b2c0430d5c/common/src/main/java/net/uku3lig/tiertagger/mixin/MixinPlayer.java), [TierTagger tab lookup](https://github.com/mctiers-dev/TierTagger/blob/6f49034c9f800f1da330efa90e5810b2c0430d5c/common/src/main/java/net/uku3lig/tiertagger/mixin/MixinPlayerTabOverlay.java), [PvPTiers name lookup](https://github.com/PvPTiers/Tiers/blob/c3520c42f0feb048c4b3cd75a5d231a9f0090b87/src/client/java/com/tiers/mixin/client/ModifyTabClientMixin.java).
