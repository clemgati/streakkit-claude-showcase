package dev.clement.streakkit.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * One tracked habit and the set of days it was completed on.
 *
 * Check-ins are a [Set] rather than a list on purpose: checking in twice on the same day is not a
 * thing that can happen, and making that structurally impossible removes a class of bug from
 * [StreakCalculator].
 */
@Serializable
data class Habit(
    val id: String,
    val name: String,
    val emoji: String,
    @Serializable(with = IsoLocalDateSerializer::class)
    val createdOn: LocalDate,
    val checkIns: Set<
        @Serializable(with = IsoLocalDateSerializer::class)
        LocalDate,
    > = emptySet(),
) {
    fun isCheckedIn(date: LocalDate): Boolean = date in checkIns

    /** Returns a copy with [date] added if absent, or removed if present. */
    fun toggling(date: LocalDate): Habit = copy(checkIns = if (date in checkIns) checkIns - date else checkIns + date)
}

/**
 * Dates are persisted as ISO-8601 strings with an explicit serializer rather than relying on
 * whichever default `kotlinx-datetime` ships. Storage format is a compatibility promise, so it
 * should not move when a dependency is bumped.
 */
object IsoLocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.clement.streakkit.LocalDate", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: LocalDate,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
