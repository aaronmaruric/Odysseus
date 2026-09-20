package com.odysseus.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period

enum class Gender { MALE, FEMALE, OTHER, UNSPECIFIED }

/** Static-ish facts about the person. Weight is tracked separately as a dated log. */
data class UserProfile(
    val name: String = "",
    val gender: Gender = Gender.UNSPECIFIED,
    val heightCm: Double? = null,
    val birthDate: LocalDate? = null,
) {
    fun age(today: LocalDate = LocalDate.now()): Int? = birthDate?.let { Period.between(it, today).years }
}

/** One weigh-in. */
data class WeightEntry(
    val id: Long = 0,
    val at: Instant,
    val weightKg: Double,
    val note: String = "",
)

/** Body mass index from the latest weight and profile height; null when either is missing. */
fun bmi(weightKg: Double?, heightCm: Double?): Double? {
    if (weightKg == null || heightCm == null || heightCm <= 0) return null
    val m = heightCm / 100
    return weightKg / (m * m)
}
