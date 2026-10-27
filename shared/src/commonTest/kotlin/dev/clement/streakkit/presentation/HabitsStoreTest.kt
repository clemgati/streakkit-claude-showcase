package dev.clement.streakkit.presentation

import dev.clement.streakkit.data.HabitRepository
import dev.clement.streakkit.data.InMemoryKeyValueStore
import dev.clement.streakkit.remote.Quote
import dev.clement.streakkit.remote.QuoteApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsStoreTest {
    private val today = LocalDate.parse("2026-10-26")

    private class FakeQuoteApi(
        private val result: Result<Quote>,
    ) : QuoteApi {
        var calls = 0
            private set

        override suspend fun randomQuote(): Quote {
            calls++
            return result.getOrThrow()
        }
    }

    private fun store(
        quoteApi: QuoteApi = FakeQuoteApi(Result.success(Quote("Keep going", "Nobody"))),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ): Pair<HabitsStore, HabitRepository> {
        var counter = 0
        val repository =
            HabitRepository(
                store = InMemoryKeyValueStore(),
                newId = { "id-${counter++}" },
            )
        return HabitsStore(
            repository = repository,
            quoteApi = quoteApi,
            today = { today },
            scope = scope,
        ) to repository
    }

    /** The single habit summary under test. Fails loudly if a test created more than one. */
    private fun HabitsStore.only(): HabitSummary = state.value.habits.single()

    @Test
    fun `state starts empty and reports the platform`() = runTest {
        val (sut, _) = store()

        assertTrue(sut.state.value.isEmpty)
        // platformName resolves per target; on the JVM test run it is the Android actual.
        assertTrue(sut.state.value.platform.isNotBlank())
    }

    @Test
    fun `adding a habit produces a summary with a one-day streak once checked in`() = runTest {
        val (sut, _) = store()

        sut.addHabit("Floss", "🦷")
        assertEquals(1, sut.state.value.habits.size)
        assertEquals(0, sut.only().currentStreak)

        sut.toggleToday(sut.only().id)

        val summary = sut.only()
        assertEquals(1, summary.currentStreak)
        assertTrue(summary.checkedInToday)
        assertEquals(1, sut.state.value.checkedInTodayCount)
    }

    @Test
    fun `blank habit names are rejected`() = runTest {
        val (sut, _) = store()

        sut.addHabit("   ", "🦷")

        assertTrue(sut.state.value.isEmpty)
    }

    @Test
    fun `a missing emoji falls back to the default`() = runTest {
        val (sut, _) = store()

        sut.addHabit("Floss", "")

        assertEquals(HabitsStore.DEFAULT_EMOJI, sut.only().emoji)
    }

    @Test
    fun `the week strip always has seven cells with today last`() = runTest {
        val (sut, _) = store()
        sut.addHabit("Floss", "🦷")

        val week =
            sut.only().week

        assertEquals(7, week.size)
        assertTrue(week.last().isToday)
        assertEquals("2026-10-26", week.last().isoDate)
    }

    @Test
    fun `toggling a specific day from the week strip works`() = runTest {
        val (sut, _) = store()
        sut.addHabit("Floss", "🦷")
        val id =
            sut.only().id

        sut.toggleDay(id, "2026-10-25")

        val summary = sut.only()
        assertFalse(summary.checkedInToday)
        assertEquals(1, summary.currentStreak, "yesterday alone is still a live streak")
    }

    @Test
    fun `refreshing the quote publishes it`() = runTest {
        val api = FakeQuoteApi(Result.success(Quote("Keep going", "Nobody")))
        val (sut, _) = store(quoteApi = api)

        sut.refreshQuote()

        assertEquals("Keep going", sut.state.value.quote?.text)
        assertFalse(sut.state.value.isQuoteOffline)
        assertFalse(sut.state.value.isQuoteLoading)
        assertEquals(1, api.calls)
    }

    @Test
    fun `a failing quote call falls back offline instead of surfacing an error`() = runTest {
        val (sut, _) = store(quoteApi = FakeQuoteApi(Result.failure(RuntimeException("no network"))))

        sut.refreshQuote()

        assertTrue(sut.state.value.isQuoteOffline)
        assertTrue(sut.state.value.quote != null, "an offline quote is still a quote")
    }

    @Test
    fun `observeState pushes every update until cancelled`() = runTest {
        val (sut, _) = store()
        val seen = mutableListOf<Int>()

        val subscription = sut.observeState { seen += it.habits.size }
        sut.addHabit("Floss", "🦷")
        sut.addHabit("Run", "🏃")
        subscription.cancel()
        sut.addHabit("Ignored", "🙈")

        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test
    fun `the completion label is rendered once in the shared module`() = runTest {
        // Caught by the PR-review workflow: both apps were formatting this themselves, with
        // roundToInt() on Android and Int(_.rounded()) on iOS. Neither app may compute it now.
        val (sut, _) = store()
        sut.addHabit("Floss", "🦷")
        sut.toggleToday(sut.only().id)

        assertEquals("3% of the last 30 days", sut.only().completionLabel)
    }

    @Test
    fun `percent formatting rounds half away from zero`() {
        // 0.125 * 100 = 12.5 — the boundary where half-to-even would give 12, not 13.
        assertEquals("13", formatPercent(0.125f))
        assertEquals("0", formatPercent(0f))
        assertEquals("100", formatPercent(1f))
    }

    @Test
    fun `deleting a habit removes its summary`() = runTest {
        val (sut, _) = store()
        sut.addHabit("Floss", "🦷")

        sut.deleteHabit(sut.only().id)

        assertTrue(sut.state.value.isEmpty)
    }
}
