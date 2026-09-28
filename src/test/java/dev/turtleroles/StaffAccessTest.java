package dev.turtleroles;

import dev.turtleroles.policy.*;
import dev.turtleroles.role.Role;
import dev.turtleroles.service.StaffAccess;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StaffAccessTest {
    private final PolicyService policy = new PolicyService(Duration.ofHours(1), Duration.ofDays(7), Duration.ofDays(7));
    @Test void grantsAreExplicitAndExcludeEscalationPaths() {
        for (Role role : new Role[]{Role.SSER, Role.CO_OWNER, Role.SR_ADMIN, Role.ADMIN}) {
            var nodes = StaffAccess.permissions(role);
            assertTrue(nodes.contains("serverutil.players"));
            assertTrue(nodes.contains("shocksmp.events.admin"));
            assertTrue(nodes.stream().noneMatch(node -> node.startsWith("shocks.")));
            assertFalse(nodes.stream().anyMatch(n -> n.contains("*") || n.startsWith("luckperms")
                    || n.startsWith("minecraft") || n.startsWith("bukkit") || n.startsWith("essentials")));
            assertFalse(nodes.contains("serverutil.punishments.overrideprotected"));
            assertFalse(nodes.contains("serverutil.restart"));
            assertFalse(nodes.contains("serverutil.players.inventory.edit"));
        }
        assertTrue(StaffAccess.permissions(Role.MEMBER).isEmpty());
        assertTrue(StaffAccess.permissions(Role.MODERATOR).isEmpty());
    }
    @Test void everyAdministrativeActionRespectsHigherAndEqualRanks() {
        for (Role role : new Role[]{Role.SSER, Role.CO_OWNER, Role.SR_ADMIN, Role.ADMIN}) {
            var actor = Actor.player(UUID.randomUUID(), "Staff", role, false);
            for (Role target : Role.values()) for (StaffAction action : StaffAction.values()) {
                boolean actual = policy.canUseOnTarget(actor, UUID.randomUUID(), target, action, false).allowed();
                assertEquals(role.outranks(target) && policy.hasCapability(role, action), actual, role+" "+target+" "+action);
            }
        }
    }
    @Test void promotionsCannotReachOwnRankAndCannotModifyHigherRank() {
        for (Role role : new Role[]{Role.SSER, Role.CO_OWNER, Role.SR_ADMIN, Role.ADMIN}) {
            UUID self = UUID.randomUUID(), target = UUID.randomUUID();
            var actor = Actor.player(self, "Staff", role, false);
            assertFalse(policy.canGrantRole(actor, self, role, Role.MEMBER).allowed());
            assertFalse(policy.canGrantRole(actor, target, Role.MEMBER, role).allowed());
            assertFalse(policy.canGrantRole(actor, target, Role.OWNER, Role.MEMBER).allowed());
            assertTrue(policy.canGrantRole(actor, target, Role.MEMBER, Role.HELPER).allowed());
        }
    }
}
