package com.flowmind.api.execution;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionStateTest {
    @Test void terminalStatesStayTerminal() {
        assertThat(ExecutionState.COMPLETED.canTransitionTo(ExecutionState.RUNNING)).isFalse();
        assertThat(ExecutionState.CANCELLED.canTransitionTo(ExecutionState.QUEUED)).isFalse();
    }

    @Test void failedExecutionCanBeManuallyRequeued() {
        assertThat(ExecutionState.FAILED.canTransitionTo(ExecutionState.QUEUED)).isTrue();
    }
}

