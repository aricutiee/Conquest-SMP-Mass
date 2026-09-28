package me.jade.ariServerUtil.util;

import me.jade.ariServerUtil.model.ReportStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReportTransitionsTest {
    @Test
    void allowsOpenAndAssignedTransitionsButLocksTerminalStates() {
        assertTrue(ReportTransitions.canTransition(ReportStatus.OPEN, ReportStatus.ASSIGNED));
        assertTrue(ReportTransitions.canTransition(ReportStatus.ASSIGNED, ReportStatus.RESOLVED));
        assertTrue(ReportTransitions.canTransition(ReportStatus.ASSIGNED, ReportStatus.OPEN));
        assertFalse(ReportTransitions.canTransition(ReportStatus.RESOLVED, ReportStatus.OPEN));
        assertFalse(ReportTransitions.canTransition(ReportStatus.REJECTED, ReportStatus.ASSIGNED));
    }
}
