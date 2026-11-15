package com.example.ddlreminder

import com.example.ddlreminder.reminder.ReminderIds
import com.example.ddlreminder.reminder.ReminderType
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReminderIdsTest {

    @Test
    fun idDependsOnBothTaskAndReminderNode() {
        val first24 = ReminderIds.forReminder(42L, ReminderType.TWENTY_FOUR_HOURS)
        val first12 = ReminderIds.forReminder(42L, ReminderType.TWELVE_HOURS)
        val other24 = ReminderIds.forReminder(43L, ReminderType.TWENTY_FOUR_HOURS)

        assertNotEquals(first24, first12)
        assertNotEquals(first24, other24)
    }
}
