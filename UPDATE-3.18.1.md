# Conquest SMP 3.18.1

Nickname compatibility for TierTagger (MCTiers) and Tiers (PvPTiers). Other Java viewers receive the copied profile UUID and entity UUID, as well as its name and skin. Server authentication, storage UUID, permissions and signed chat sender remain real. The authentic chat profile stays unlisted; signing keys are never copied to the disguise.

Requires the existing PacketEvents 2.14.0 installation for UUID presentation. Name and skin still work without it. The optional switch nicknames.client-tier-identity defaults to true. Resource pack unchanged.

Own-client self views and Bedrock recipients do not receive copied UUIDs. Actual rendering depends on the client mod and its ranking service. External rankings are not changed. Real-owner login clears the disguise before player-list synchronization. Reset and logout restore the original presentation.

Verification: build, packaged SQLite check and all 297 automated tests pass. Includes packet-entry transformations, hidden authentic chat-session preservation, alias removal ordering, observer isolation, self/Bedrock exclusions and lifecycle refresh ordering. This is not a claim of live client-mod verification. See NICKNAMES.md.
