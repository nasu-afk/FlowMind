package com.flowmind.api.execution;

import java.util.EnumSet;
import java.util.Set;

public enum ExecutionState {
    CREATED, QUEUED, RUNNING, WAITING, PAUSED, COMPLETED, FAILED, CANCELLED, TIMED_OUT;

    public boolean canTransitionTo(ExecutionState target) {
        return allowedTargets().contains(target);
    }

    private Set<ExecutionState> allowedTargets() {
        return switch (this) {
            case CREATED -> EnumSet.of(QUEUED, CANCELLED);
            case QUEUED -> EnumSet.of(RUNNING, CANCELLED, TIMED_OUT);
            case RUNNING -> EnumSet.of(WAITING, PAUSED, COMPLETED, FAILED, CANCELLED, TIMED_OUT);
            case WAITING -> EnumSet.of(QUEUED, PAUSED, FAILED, CANCELLED, TIMED_OUT);
            case PAUSED -> EnumSet.of(QUEUED, CANCELLED);
            case FAILED -> EnumSet.of(QUEUED);
            case COMPLETED, CANCELLED, TIMED_OUT -> EnumSet.noneOf(ExecutionState.class);
        };
    }
}

