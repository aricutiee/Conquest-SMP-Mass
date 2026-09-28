package dev.turtleroles.policy;

import dev.turtleroles.punishment.PunishmentType;
import dev.turtleroles.role.Role;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

public final class PolicyService {
    private final Duration helperMuteLimit;
    private final Duration moderatorMuteLimit;
    private final Duration moderatorBanLimit;

    public PolicyService(Duration helperMuteLimit, Duration moderatorMuteLimit, Duration moderatorBanLimit) {
        this.helperMuteLimit = helperMuteLimit;
        this.moderatorMuteLimit = moderatorMuteLimit;
        this.moderatorBanLimit = moderatorBanLimit;
    }

    public PolicyDecision canTarget(Actor actor, UUID targetUuid, Role targetRole, StaffAction action, boolean selfServiceAllowed) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(targetRole, "targetRole");
        if (actor.console() || actor.ownerOverride()) {
            return PolicyDecision.allow();
        }
        if (actor.uuid() != null && actor.uuid().equals(targetUuid)) {
            return selfServiceAllowed ? PolicyDecision.allow() : PolicyDecision.deny("You cannot use " + action.name().toLowerCase() + " on yourself.");
        }
        if (!actor.role().outranks(targetRole)) {
            return PolicyDecision.deny("You can only affect players below your role.");
        }
        return PolicyDecision.allow();
    }

    public PolicyDecision canUseUntargeted(Actor actor, StaffAction action) {
        if (actor.console() || actor.ownerOverride()) {
            return PolicyDecision.allow();
        }
        return hasCapability(actor.role(), action)
            ? PolicyDecision.allow()
            : PolicyDecision.deny("Your role cannot use " + action.name().toLowerCase().replace('_', ' ') + ".");
    }

    public PolicyDecision canUseOnTarget(Actor actor, UUID targetUuid, Role targetRole, StaffAction action, boolean selfServiceAllowed) {
        PolicyDecision capability = canUseUntargeted(actor, action);
        if (!capability.allowed()) {
            return capability;
        }
        return canTarget(actor, targetUuid, targetRole, action, selfServiceAllowed);
    }

    public PolicyDecision canGrantRole(Actor actor, UUID targetUuid, Role currentTargetRole, Role requestedRole) {
        if (actor.console() || actor.ownerOverride()) {
            return PolicyDecision.allow();
        }
        if (!actor.role().canManageRoles()) {
            return PolicyDecision.deny("Your role cannot assign roles.");
        }
        if (actor.uuid() != null && actor.uuid().equals(targetUuid)) {
            return PolicyDecision.deny("You cannot promote or demote yourself.");
        }
        if (!actor.role().outranks(currentTargetRole)) {
            return PolicyDecision.deny("You cannot change a player at your role or above.");
        }
        if (!actor.role().outranks(requestedRole)) {
            return PolicyDecision.deny("You cannot grant your own role or a higher role.");
        }
        if (requestedRole == Role.OWNER) {
            return PolicyDecision.deny("Only an operator or the console can assign OWNER.");
        }
        return PolicyDecision.allow();
    }

    public PolicyDecision canIssuePunishment(Actor actor, UUID targetUuid, Role targetRole, PunishmentType type, Duration duration) {
        StaffAction action = switch (type) {
            case WARNING -> StaffAction.WARN;
            case TEMP_MUTE -> StaffAction.TEMP_MUTE;
            case PERM_MUTE -> StaffAction.PERM_MUTE;
            case TEMP_BAN -> StaffAction.TEMP_BAN;
            case PERM_BAN -> StaffAction.PERM_BAN;
            case KICK -> StaffAction.KICK;
        };
        PolicyDecision base = canUseOnTarget(actor, targetUuid, targetRole, action, false);
        if (!base.allowed()) {
            return base;
        }
        if (duration != null) {
            Duration max = maxDuration(actor.role(), type);
            if (max != null && duration.compareTo(max) > 0) {
                return PolicyDecision.deny("That duration exceeds your role limit of " + max.toSeconds() + " seconds.");
            }
        }
        return PolicyDecision.allow();
    }

    public PolicyDecision canReverse(Actor actor, UUID targetUuid, Role targetRole, UUID issuerUuid, Role issuerCurrentRole, Role issuerSnapshotRole, PunishmentType type) {
        StaffAction action = reversalActionFor(type);
        PolicyDecision capability = canUseUntargeted(actor, action);
        if (!capability.allowed()) {
            return capability;
        }
        PolicyDecision target = canTarget(actor, targetUuid, targetRole, StaffAction.REVERSAL, false);
        if (!target.allowed()) {
            return target;
        }
        if (actor.console() || actor.ownerOverride()) {
            return PolicyDecision.allow();
        }
        Role protectedIssuerRole = issuerCurrentRole.weight() >= issuerSnapshotRole.weight() ? issuerCurrentRole : issuerSnapshotRole;
        if (actor.uuid() != null && actor.uuid().equals(issuerUuid)) {
            return hasCapability(actor.role(), action)
                ? PolicyDecision.allow()
                : PolicyDecision.deny("You no longer have permission to reverse this case.");
        }
        return actor.role().outranks(protectedIssuerRole)
            ? PolicyDecision.allow()
            : PolicyDecision.deny("You cannot reverse a case issued by equal or higher staff.");
    }

    public boolean hasCapability(Role role, StaffAction action) {
        if (role == Role.OWNER) {
            return true;
        }
        EnumSet<StaffAction> actions = switch (role) {
            case CO_OWNER, SR_ADMIN -> EnumSet.allOf(StaffAction.class);
            case ADMIN, SSER -> EnumSet.of(
                StaffAction.WARN, StaffAction.TEMP_MUTE, StaffAction.PERM_MUTE, StaffAction.TEMP_BAN,
                StaffAction.PERM_BAN, StaffAction.KICK, StaffAction.INVSEE, StaffAction.GAMEMODE,
                StaffAction.HISTORY, StaffAction.REVERSAL, StaffAction.HEAL, StaffAction.FEED,
                StaffAction.FLY, StaffAction.TELEPORT, StaffAction.CLEAR_INVENTORY,
                StaffAction.GIVE_ITEM, StaffAction.WORLD_CONTROL, StaffAction.ROLE_SET
            );
            case MODERATOR -> EnumSet.of(StaffAction.WARN, StaffAction.TEMP_MUTE, StaffAction.TEMP_BAN, StaffAction.HISTORY, StaffAction.REVERSAL);
            case HELPER -> EnumSet.of(StaffAction.WARN, StaffAction.TEMP_MUTE, StaffAction.HISTORY);
            case MEMBER, MEDIA, BOOSTER, BOOSTER_X2 -> EnumSet.noneOf(StaffAction.class);
            case OWNER -> throw new IllegalStateException("Handled above");
        };
        if (role == Role.CO_OWNER || role == Role.SR_ADMIN) {
            actions.remove(StaffAction.ROLE_SET);
        }
        if ((role == Role.CO_OWNER || role == Role.SR_ADMIN) && action == StaffAction.ROLE_SET) {
            return true;
        }
        return actions.contains(action);
    }

    public Duration maxDuration(Role role, PunishmentType type) {
        if (type == PunishmentType.TEMP_MUTE) {
            if (role == Role.HELPER) {
                return helperMuteLimit;
            }
            if (role == Role.MODERATOR) {
                return moderatorMuteLimit;
            }
        }
        if (type == PunishmentType.TEMP_BAN && role == Role.MODERATOR) {
            return moderatorBanLimit;
        }
        return null;
    }

    private StaffAction reversalActionFor(PunishmentType type) {
        return switch (type) {
            case WARNING -> StaffAction.WARN;
            case TEMP_MUTE, PERM_MUTE -> type == PunishmentType.PERM_MUTE ? StaffAction.PERM_MUTE : StaffAction.TEMP_MUTE;
            case TEMP_BAN, PERM_BAN -> type == PunishmentType.PERM_BAN ? StaffAction.PERM_BAN : StaffAction.TEMP_BAN;
            case KICK -> StaffAction.KICK;
        };
    }
}
