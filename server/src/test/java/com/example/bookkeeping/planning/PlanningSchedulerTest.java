package com.example.bookkeeping.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PlanningSchedulerTest {
    @Test
    void recurringDayClampsToShortMonths() {
        assertEquals(LocalDate.of(2026, 2, 28), PlanningScheduler.scheduledDay(YearMonth.of(2026, 2), 31));
        assertEquals(LocalDate.of(2028, 2, 29), PlanningScheduler.scheduledDay(YearMonth.of(2028, 2), 31));
        assertEquals(LocalDate.of(2026, 10, 31), PlanningScheduler.scheduledDay(YearMonth.of(2026, 10), 31));
    }

    @Test
    void recurringBillWaitsForDueDateAndNeverBackfillsBeforeCreation() {
        LocalDate due = LocalDate.of(2026, 10, 28);
        assertFalse(PlanningScheduler.dueToGenerate(due, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27)));
        assertTrue(PlanningScheduler.dueToGenerate(due, LocalDate.of(2026, 10, 1), due));
        assertFalse(PlanningScheduler.dueToGenerate(due, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)));
    }
}
