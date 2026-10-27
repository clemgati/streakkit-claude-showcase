package dev.clement.streakkit.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** `"Android 36"` or `"iOS 26.5"`. Both UIs print this so you can tell them apart in screenshots. */
expect val platformName: String

/**
 * Today in the device's own timezone.
 *
 * Every function that needs a date takes it as a parameter and defaults to this, so the domain
 * stays clock-free and tests stay deterministic.
 */
fun systemToday(): LocalDate = Clock.System
    .now()
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
