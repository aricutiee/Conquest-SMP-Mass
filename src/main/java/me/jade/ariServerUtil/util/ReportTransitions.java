package me.jade.ariServerUtil.util;

import me.jade.ariServerUtil.model.ReportStatus;

public final class ReportTransitions {
    private ReportTransitions() {
    }

    public static boolean canTransition(ReportStatus from, ReportStatus to) {
        if (from == to) {
            return true;
        }
        return switch (from) {
            case OPEN -> to == ReportStatus.ASSIGNED || to == ReportStatus.RESOLVED || to == ReportStatus.REJECTED;
            case ASSIGNED -> to == ReportStatus.RESOLVED || to == ReportStatus.REJECTED || to == ReportStatus.OPEN;
            case RESOLVED, REJECTED -> false;
        };
    }
}
