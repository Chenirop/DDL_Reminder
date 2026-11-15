package com.example.ddlreminder

import com.example.ddlreminder.data.TaskEntity
import com.example.ddlreminder.util.TaskOrdering
import com.example.ddlreminder.util.TaskRetention
import com.example.ddlreminder.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskOrderingTest {

    private val now = 10 * TimeFormat.DAY_MS

    private fun task(
        id: Long,
        ddl: Long?,
        completed: Boolean = false,
        completedAt: Long? = null,
        createdAt: Long = id,
        sortOrder: Int? = null,
        restoredAt: Long? = null
    ) = TaskEntity(
        id = id,
        listId = 1L,
        title = "task-$id",
        ddl = ddl,
        isCompleted = completed,
        completedAt = completedAt,
        createdAt = createdAt,
        updatedAt = createdAt,
        sortOrder = sortOrder,
        restoredAt = restoredAt
    )

    @Test
    fun sortsDdlAscendingAndNullDdlLast() {
        val tasks = listOf(
            task(1, null),
            task(2, now + 3 * TimeFormat.HOUR_MS),
            task(3, now + TimeFormat.HOUR_MS),
            task(4, now + 2 * TimeFormat.HOUR_MS)
        )

        val sorted = TaskOrdering.visibleAndSorted(tasks, now)

        assertEquals(listOf(3L, 4L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun completedTasksGoAfterUnfinished() {
        val tasks = listOf(
            task(1, now + TimeFormat.HOUR_MS, completed = true, completedAt = now - 1000),
            task(2, now + 2 * TimeFormat.HOUR_MS)
        )

        val sorted = TaskOrdering.visibleAndSorted(tasks, now)

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun completedTaskDisappearsAfterTwentyFourHours() {
        val justInside = task(
            1,
            null,
            completed = true,
            completedAt = now - TimeFormat.DAY_MS + 1
        )
        val atBoundary = task(
            2,
            null,
            completed = true,
            completedAt = now - TimeFormat.DAY_MS
        )
        val tooOld = task(
            3,
            null,
            completed = true,
            completedAt = now - TimeFormat.DAY_MS - 1
        )

        val sorted = TaskOrdering.visibleAndSorted(
            listOf(justInside, atBoundary, tooOld),
            now
        )

        assertEquals(listOf(1L), sorted.map { it.id })
    }

    @Test
    fun manualSortOrderWinsOverDdlOrder() {
        val tasks = listOf(
            task(1, now + TimeFormat.HOUR_MS, sortOrder = 2),
            task(2, now + 2 * TimeFormat.HOUR_MS, sortOrder = 0),
            task(3, now + 3 * TimeFormat.HOUR_MS, sortOrder = 1)
        )

        val sorted = TaskOrdering.visibleAndSorted(tasks, now)

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun withoutManualOrderDdlOrderIsUsed() {
        val tasks = listOf(
            task(1, now + 3 * TimeFormat.HOUR_MS),
            task(2, now + TimeFormat.HOUR_MS)
        )

        val sorted = TaskOrdering.visibleAndSorted(tasks, now)

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun overdueIncompleteTasksGoFirst() {
        val tasks = listOf(
            task(1, now + TimeFormat.HOUR_MS),
            task(2, now - TimeFormat.HOUR_MS),
            task(3, now + 2 * TimeFormat.HOUR_MS),
            task(4, now - 2 * TimeFormat.HOUR_MS)
        )

        val sorted = TaskOrdering.visibleAndSorted(tasks, now)

        assertEquals(listOf(4L, 2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun overdueIncompleteTaskDisappearsAfterFortyEightHours() {
        val tasks = listOf(
            task(1, now - TimeFormat.DAY_MS),
            task(2, now - 2 * TimeFormat.DAY_MS),
            task(3, now - 2 * TimeFormat.DAY_MS - 1),
            task(4, now + TimeFormat.HOUR_MS)
        )

        val sorted = TaskOrdering.visibleAndSorted(tasks, now)

        assertEquals(listOf(1L, 4L), sorted.map { it.id })
    }

    @Test
    fun manualOrderOverridesOverduePinning() {
        val tasks = listOf(
            task(1, now - TimeFormat.HOUR_MS, sortOrder = 1),
            task(2, now + TimeFormat.HOUR_MS, sortOrder = 0)
        )

        val sorted = TaskOrdering.visibleAndSorted(tasks, now)

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun restoredOverdueTaskGetsTwelveHourGrace() {
        val restored = task(
            1,
            now - 3 * TimeFormat.DAY_MS,
            restoredAt = now - 2 * TimeFormat.HOUR_MS
        )

        val sorted = TaskOrdering.visibleAndSorted(listOf(restored), now)

        assertEquals(listOf(1L), sorted.map { it.id })
    }

    @Test
    fun restoredOverdueTaskDisappearsAfterGrace() {
        val restored = task(
            1,
            now - 3 * TimeFormat.DAY_MS,
            restoredAt = now - 13 * TimeFormat.HOUR_MS
        )

        val sorted = TaskOrdering.visibleAndSorted(listOf(restored), now)

        assertTrue(sorted.isEmpty())
    }

    @Test
    fun restoredInsideTwoDaysKeepsRestoredWindow() {
        val restored = task(
            1,
            now - 30 * TimeFormat.HOUR_MS,
            restoredAt = now - TimeFormat.HOUR_MS
        )

        val sorted = TaskOrdering.visibleAndSorted(listOf(restored), now)

        assertEquals(listOf(1L), sorted.map { it.id })
        assertEquals(
            now - TimeFormat.HOUR_MS + TimeFormat.DAY_MS * 2,
            TaskRetention.removalDeadline(restored, TimeFormat.HOUR_MS * 12)
        )
    }
}
