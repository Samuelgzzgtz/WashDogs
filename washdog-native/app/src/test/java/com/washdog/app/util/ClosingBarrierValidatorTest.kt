package com.washdog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClosingBarrierValidatorTest {
    @Test fun `valid appointment returns 0`() =
        assertEquals(0, ClosingBarrierValidator.minutesExceeded("10:00", 120, 0, "19:00"))
    @Test fun `appointment ending exactly at close returns 0`() =
        assertEquals(0, ClosingBarrierValidator.minutesExceeded("17:00", 120, 0, "19:00"))
    @Test fun `appointment exceeding by 30 min returns 30`() =
        assertEquals(30, ClosingBarrierValidator.minutesExceeded("18:00", 90, 0, "19:00"))
    @Test fun `travel buffer included in calculation`() =
        assertEquals(10, ClosingBarrierValidator.minutesExceeded("17:30", 60, 40, "19:00"))
    @Test fun `blank start time returns 0`() =
        assertEquals(0, ClosingBarrierValidator.minutesExceeded("", 120, 0, "19:00"))
    @Test fun `service and buffer together exceeding by 1h returns 60`() =
        assertEquals(60, ClosingBarrierValidator.minutesExceeded("18:00", 60, 60, "19:00"))
}
