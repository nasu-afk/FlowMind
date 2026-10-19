package com.flowmind.api.trigger;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ScheduleCalculatorTest {
    private final ScheduleCalculator calculator=new ScheduleCalculator();
    @Test void computesInConfiguredTimezone(){Instant next=calculator.nextCron("0 0 9 * * MON-FRI","Asia/Kolkata",Instant.parse("2026-10-16T02:00:00Z"));assertThat(next).isEqualTo(Instant.parse("2026-10-16T03:30:00Z"));}
    @Test void boundsCatchUp(){assertThat(calculator.runsToDispatch(Instant.EPOCH,Instant.now(),ScheduleCalculator.MissedRunPolicy.CATCH_UP_BOUNDED,500)).isEqualTo(100);}
    @Test void plansOnlyBoundedMissedOccurrences(){var plan=calculator.plan("0 * * * * *","UTC",Instant.parse("2026-10-16T02:00:00Z"),Instant.parse("2026-10-16T02:10:30Z"),ScheduleCalculator.MissedRunPolicy.CATCH_UP_BOUNDED,3);assertThat(plan.runs()).containsExactly(Instant.parse("2026-10-16T02:00:00Z"),Instant.parse("2026-10-16T02:01:00Z"),Instant.parse("2026-10-16T02:02:00Z"));assertThat(plan.nextRunAt()).isEqualTo(Instant.parse("2026-10-16T02:03:00Z"));}
    @Test void advancesSkippedBacklogToFuture(){var plan=calculator.plan("0 * * * * *","UTC",Instant.parse("2026-10-16T02:00:00Z"),Instant.parse("2026-10-16T02:10:30Z"),ScheduleCalculator.MissedRunPolicy.SKIP,10);assertThat(plan.runs()).containsExactly(Instant.parse("2026-10-16T02:00:00Z"));assertThat(plan.nextRunAt()).isEqualTo(Instant.parse("2026-10-16T02:11:00Z"));}
}
