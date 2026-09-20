package com.painani.app.domain.model

/**
 * Starter list shown in the exercise picker before the user has logged anything.
 * Anything the user types that is not here is saved as a new exercise and appears next time.
 */
object ExerciseLibrary {
    val defaults: List<String> = listOf(
        // Lower body
        "Squat", "Front Squat", "Hack Squat", "Leg Press", "Bulgarian Split Squat", "Lunge",
        "Deadlift", "Romanian Deadlift", "Sumo Deadlift", "Good Morning", "Hip Thrust",
        "Leg Curl", "Leg Extension", "Calf Raise", "Kettlebell Swing",
        // Push
        "Bench Press", "Incline Bench Press", "Dumbbell Bench Press", "Overhead Press",
        "Dumbbell Shoulder Press", "Push-up", "Dip", "Pec Deck", "Cable Fly",
        // Pull
        "Pull-up", "Chin-up", "Lat Pulldown", "Barbell Row", "Dumbbell Row", "Seated Row",
        "Face Pull", "Shrug", "Back Extension",
        // Arms and shoulders
        "Bicep Curl", "Hammer Curl", "Tricep Pushdown", "Skull Crusher", "Lateral Raise", "Rear Delt Fly",
        // Core and carries
        "Plank", "Hanging Leg Raise", "Cable Crunch", "Ab Wheel", "Farmer's Carry",
    )
}
