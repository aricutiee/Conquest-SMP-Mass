package dev.turtleroles;

import dev.turtleroles.policy.Actor;
import dev.turtleroles.policy.PolicyDecision;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.policy.StaffAction;
import dev.turtleroles.punishment.PunishmentType;
import dev.turtleroles.role.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyServiceTest {
    private final PolicyService policy = new PolicyService(Duration.ofHours(1), Duration.ofDays(7), Duration.ofDays(7));
    private final UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void requestedCapabilitiesMatchForEveryRole() {
        var admin = java.util.EnumSet.of(StaffAction.WARN, StaffAction.TEMP_MUTE, StaffAction.PERM_MUTE,
            StaffAction.TEMP_BAN, StaffAction.PERM_BAN, StaffAction.KICK, StaffAction.INVSEE,
            StaffAction.GAMEMODE, StaffAction.HISTORY, StaffAction.REVERSAL, StaffAction.HEAL,
            StaffAction.FEED, StaffAction.FLY, StaffAction.TELEPORT, StaffAction.CLEAR_INVENTORY,
            StaffAction.GIVE_ITEM, StaffAction.WORLD_CONTROL, StaffAction.ROLE_SET);
        var moderator = java.util.EnumSet.of(StaffAction.WARN, StaffAction.TEMP_MUTE,
            StaffAction.TEMP_BAN, StaffAction.HISTORY, StaffAction.REVERSAL);
        var helper = java.util.EnumSet.of(StaffAction.WARN, StaffAction.TEMP_MUTE, StaffAction.HISTORY);
        for (Role role : Role.values()) {
            for (StaffAction action : StaffAction.values()) {
                boolean expected = switch (role) {
                    case OWNER, CO_OWNER, SR_ADMIN -> true;
                    case ADMIN, SSER -> admin.contains(action);
                    case MODERATOR -> moderator.contains(action);
                    case HELPER -> helper.contains(action);
                    case MEMBER, MEDIA, BOOSTER, BOOSTER_X2 -> false;
                };
                org.junit.jupiter.api.Assertions.assertEquals(expected, policy.hasCapability(role, action), role + " " + action);
            }
        }
    }

    @Test
    void targetedActionsRequireStrictlyLowerRankAcrossMatrix() {
        for (Role actorRole : Role.values()) {
            for (Role targetRole : Role.values()) {
                Actor actor = Actor.player(actorId, "Actor", actorRole);
                PolicyDecision decision = policy.canTarget(actor, targetId, targetRole, StaffAction.WARN, false);
                boolean expected = actorRole == Role.OWNER || actorRole.outranks(targetRole);
                assertTrue(decision.allowed() == expected, actorRole + " targeting " + targetRole + " expected " + expected);
            }
        }
    }

    @Test
    void directOperatorBypassesStoredRoleAndTargetRank() {
        Actor opMember = Actor.player(actorId, "OpMember", Role.MEMBER, true);
        assertTrue(policy.canUseUntargeted(opMember, StaffAction.PERM_BAN).allowed());
        assertTrue(policy.canUseOnTarget(opMember, targetId, Role.OWNER, StaffAction.KICK, false).allowed());
        assertTrue(policy.canGrantRole(opMember, targetId, Role.OWNER, Role.OWNER).allowed());
    }

    @Test
    void selfPunishmentIsDeniedButSelfGamemodeIsAllowedWhenCapable() {
        Actor admin = Actor.player(actorId, "Admin", Role.ADMIN);
        assertFalse(policy.canUseOnTarget(admin, actorId, Role.ADMIN, StaffAction.KICK, false).allowed());
        assertTrue(policy.canUseOnTarget(admin, actorId, Role.ADMIN, StaffAction.GAMEMODE, true).allowed());
    }

    @Test
    void roleGrantChecksTargetCurrentRoleAndRequestedRole() {
        Actor coOwner = Actor.player(actorId, "Co", Role.CO_OWNER);
        assertTrue(policy.canGrantRole(coOwner, targetId, Role.ADMIN, Role.SR_ADMIN).allowed());
        assertFalse(policy.canGrantRole(coOwner, targetId, Role.CO_OWNER, Role.SR_ADMIN).allowed());
        assertFalse(policy.canGrantRole(coOwner, targetId, Role.ADMIN, Role.CO_OWNER).allowed());
        assertTrue(policy.canGrantRole(Actor.player(actorId, "Admin", Role.ADMIN), targetId, Role.MEMBER, Role.HELPER).allowed());
    }

    @Test
    void durationLimitsAreEnforcedAtBoundaries() {
        Actor helper = Actor.player(actorId, "Helper", Role.HELPER);
        Actor moderator = Actor.player(actorId, "Mod", Role.MODERATOR);
        assertTrue(policy.canIssuePunishment(helper, targetId, Role.MEMBER, PunishmentType.TEMP_MUTE, Duration.ofHours(1)).allowed());
        assertFalse(policy.canIssuePunishment(helper, targetId, Role.MEMBER, PunishmentType.TEMP_MUTE, Duration.ofHours(1).plusSeconds(1)).allowed());
        assertTrue(policy.canIssuePunishment(moderator, targetId, Role.MEMBER, PunishmentType.TEMP_BAN, Duration.ofDays(7)).allowed());
        assertFalse(policy.canIssuePunishment(moderator, targetId, Role.MEMBER, PunishmentType.TEMP_BAN, Duration.ofDays(7).plusSeconds(1)).allowed());
        assertFalse(policy.canIssuePunishment(moderator, targetId, Role.MEMBER, PunishmentType.PERM_BAN, null).allowed());
    }

    @Test
    void reversalProtectsHigherIssuerSnapshotAfterDemotion() {
        Actor moderator = Actor.player(actorId, "Mod", Role.MODERATOR);
        assertFalse(policy.canReverse(moderator, targetId, Role.MEMBER, UUID.randomUUID(), Role.HELPER, Role.ADMIN, PunishmentType.TEMP_MUTE).allowed());
        Actor srAdmin = Actor.player(actorId, "Sr", Role.SR_ADMIN);
        assertTrue(policy.canReverse(srAdmin, targetId, Role.MEMBER, UUID.randomUUID(), Role.ADMIN, Role.ADMIN, PunishmentType.TEMP_MUTE).allowed());
    }
}
