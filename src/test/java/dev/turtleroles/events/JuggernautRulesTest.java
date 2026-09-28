package dev.turtleroles.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JuggernautRulesTest {

    @Test void aClaimPersistsThroughVictoryUntilAdministrativeEnd() {
        UUID event = UUID.randomUUID();
        assertTrue(JuggernautRules.canClaim(true, event, event, false));
        assertFalse(JuggernautRules.canClaim(true, event, event, true));
        assertFalse(JuggernautRules.canClaim(true, event, event, true)); // won stage
        assertTrue(JuggernautRules.canClaim(false, event, event, true)); // /event end
        assertTrue(JuggernautRules.canClaim(true, event, UUID.randomUUID(), true));
    }

}
