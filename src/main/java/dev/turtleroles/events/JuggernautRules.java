package dev.turtleroles.events;

import java.util.UUID;

/** Decisions shared by pickup and launch handlers. All times are epoch milliseconds. */
final class JuggernautRules {
    private JuggernautRules() {}


    static boolean canClaim(boolean lootRestricted, UUID activeEvent, UUID rewardEvent, boolean claimed) {
        return !lootRestricted || activeEvent == null || !activeEvent.equals(rewardEvent) || !claimed;
    }

}
