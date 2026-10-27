package dev.clement.streakkit.data

import dev.clement.streakkit.domain.Habit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HabitRepositoryTest {
    private val today = LocalDate.parse("2026-10-26")

    private fun repository(store: KeyValueStore = InMemoryKeyValueStore()): HabitRepository {
        var counter = 0
        return HabitRepository(store = store, newId = { "id-${counter++}" })
    }

    /** The single habit under test. Fails loudly if a test accidentally created more than one. */
    private fun HabitRepository.only(): Habit = habits.value.single()

    @Test
    fun `a new repository is empty`() {
        assertTrue(repository().habits.value.isEmpty())
    }

    @Test
    fun `adding a habit exposes it on the flow and writes it through`() {
        val store = InMemoryKeyValueStore()
        val repo = repository(store)

        val habit = repo.add(" Floss ", "🦷", today)

        assertEquals(1, repo.habits.value.size)
        assertEquals("Floss", habit.name, "names are trimmed on the way in")
        assertEquals(1, store.writeCount)
        assertNotNull(store.read(HabitRepository.HABITS_KEY))
    }

    @Test
    fun `state survives a restart because it was persisted`() {
        val store = InMemoryKeyValueStore()
        repository(store).add("Floss", "🦷", today)

        val reopened = repository(store)

        assertEquals(listOf("Floss"), reopened.habits.value.map { it.name })
    }

    @Test
    fun `toggling a check-in twice returns to the original state`() {
        val repo = repository()
        val habit = repo.add("Floss", "🦷", today)

        repo.toggleCheckIn(habit.id, today)
        assertEquals(setOf(today), repo.only().checkIns)

        repo.toggleCheckIn(habit.id, today)
        assertTrue(repo.only().checkIns.isEmpty())
    }

    @Test
    fun `toggling leaves other habits untouched`() {
        val repo = repository()
        val first = repo.add("Floss", "🦷", today)
        repo.add("Run", "🏃", today)

        repo.toggleCheckIn(first.id, today)

        val toggled = repo.habits.value.first { it.id == first.id }
        val untouched = repo.habits.value.first { it.id != first.id }

        assertEquals(setOf(today), toggled.checkIns)
        assertTrue(untouched.checkIns.isEmpty())
    }

    @Test
    fun `deleting removes only the target`() {
        val repo = repository()
        val keep = repo.add("Floss", "🦷", today)
        val drop = repo.add("Run", "🏃", today)

        repo.delete(drop.id)

        assertEquals(listOf(keep.id), repo.habits.value.map { it.id })
    }

    @Test
    fun `a habit stored before weeklyGoal existed still decodes`() {
        // Exactly the payload the previous version wrote: no weeklyGoal key at all. If this
        // breaks, every existing user silently loses their habits on upgrade.
        val legacy =
            """
            [{"id":"id-0","name":"Floss","emoji":"🦷","createdOn":"2026-10-20",
              "checkIns":["2026-10-25","2026-10-26"]}]
            """.trimIndent()
        val store = InMemoryKeyValueStore(mapOf(HabitRepository.HABITS_KEY to legacy))

        val habit = repository(store).only()

        assertEquals("Floss", habit.name)
        assertEquals(2, habit.checkIns.size)
        assertEquals(Habit.DEFAULT_WEEKLY_GOAL, habit.weeklyGoal, "missing field falls back to the default")
    }

    @Test
    fun `a weekly goal outside 1 to 7 is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Habit(id = "x", name = "n", emoji = "e", createdOn = today, weeklyGoal = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            Habit(id = "x", name = "n", emoji = "e", createdOn = today, weeklyGoal = 8)
        }
    }

    @Test
    fun `corrupt stored data degrades to empty instead of throwing`() {
        val store = InMemoryKeyValueStore(mapOf(HabitRepository.HABITS_KEY to "{not json at all"))

        assertTrue(repository(store).habits.value.isEmpty())
    }

    @Test
    fun `seeding is idempotent`() {
        val store = InMemoryKeyValueStore()
        val repo = repository(store)

        repo.seedIfEmpty(today)
        val afterFirst = repo.habits.value.size

        repo.seedIfEmpty(today)

        assertEquals(3, afterFirst)
        assertEquals(afterFirst, repo.habits.value.size)
    }
}
