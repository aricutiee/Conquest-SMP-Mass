package dev.turtleroles.service;

import dev.turtleroles.policy.Actor;
import dev.turtleroles.policy.PolicyDecision;
import dev.turtleroles.policy.PolicyService;
import dev.turtleroles.punishment.PunishmentCase;
import dev.turtleroles.punishment.PunishmentType;
import dev.turtleroles.role.Role;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.storage.PunishmentRepository;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class PunishmentService {
    private final PolicyService policy;
    private final RoleService roles;
    private final PlayerRepository players;
    private final PunishmentRepository punishments;

    public PunishmentService(PolicyService policy, RoleService roles, PlayerRepository players, PunishmentRepository punishments) {
        this.policy = policy;
        this.roles = roles;
        this.players = players;
        this.punishments = punishments;
    }

    public PunishmentCase issue(Actor actor, PlayerRecord target, PunishmentType type, Duration duration, String reason) throws SQLException {
        PolicyDecision decision = policy.canIssuePunishment(actor, target.uuid(), roles.effectiveRoleOf(target.uuid()), type, duration);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        Instant expiresAt = duration == null ? null : Instant.now().plus(duration);
        return punishments.create(type, target, actor, reason, expiresAt);
    }

    public void revoke(Actor actor, long caseId, String reason) throws SQLException {
        PunishmentCase punishment = punishments.get(caseId).orElseThrow(() -> new IllegalArgumentException("Unknown case id."));
        PlayerRecord target = players.findByUuid(punishment.targetUuid()).orElseThrow(() -> new IllegalArgumentException("Unknown target."));
        Role issuerCurrent = punishment.issuerUuid() == null
            ? Role.OWNER
            : players.findByUuid(punishment.issuerUuid())
                .map(issuer -> roles.effectiveRoleOf(issuer.uuid()))
                .orElse(punishment.issuerRoleSnapshot());
        PolicyDecision decision = policy.canReverse(
            actor,
            target.uuid(),
            roles.effectiveRoleOf(target.uuid()),
            punishment.issuerUuid(),
            issuerCurrent,
            punishment.issuerRoleSnapshot(),
            punishment.type()
        );
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        punishments.revoke(caseId, actor, reason);
    }

    public Optional<PunishmentCase> activeMute(PlayerRecord target) throws SQLException {
        return punishments.activeMute(target.uuid(), Instant.now());
    }

    public Optional<PunishmentCase> activeBan(PlayerRecord target) throws SQLException {
        return punishments.activeBan(target.uuid(), Instant.now());
    }

    public List<PunishmentCase> history(PlayerRecord target, int pageSize, int page) throws SQLException {
        return punishments.listFor(target.uuid(), pageSize, Math.max(0, page - 1) * pageSize);
    }

    public Optional<PunishmentCase> get(long id) throws SQLException {
        return punishments.get(id);
    }
}
