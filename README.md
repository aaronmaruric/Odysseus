# Odysseus

A personal training log for Android: GPS runs with splits, strength workouts with sets, and a
calendar that shows both at a glance.

Licensed under **GPLv3** (see `LICENSE`). Parts of the tracking and calendar code are intended to be
ported from [RunnerUp](https://github.com/jonasoreland/runnerup),
[Fossify Calendar](https://github.com/FossifyOrg/Calendar) and
[Flexify](https://github.com/brandonp2412/Flexify), all GPLv3.

## Building

Requires Android Studio (Ladybug or newer). Open the project folder; Studio installs the SDK and
JDK it needs. From the command line, once `local.properties` points at an SDK:

```
./gradlew assembleDebug
./gradlew test
```

## Layout

```
app/src/main/java/com/odysseus/app/
├── domain/          Pure Kotlin — models + repository interface. No Android imports.
│   ├── model/       Session, Split, Exercise, ExerciseSet
│   └── repository/  SessionRepository
├── data/            Room implementation of the domain repository.
│   ├── local/       Entities, DAO, database
│   └── repository/  RoomSessionRepository (+ entity<->domain mappers)
├── ics/             Pure Kotlin iCalendar parser (folding, TZID, DURATION, RRULE expansion)
├── tracking/        GPS engine: RunTracker interface, LocationManager implementation, foreground service
├── ui/
│   ├── calendar/    Month grid with run/strength markers; day detail with splits/sets tables
│   ├── run/         Run logging (manual entry until GPS is ported)
│   ├── strength/    Workout logging (exercise / reps / weight rows)
│   ├── navigation/  Bottom-nav host and routes
│   └── theme/       Material 3 theme
├── OdysseusApp.kt   Application + hand-rolled DI container
└── MainActivity.kt
```

The `domain` package is deliberately platform-free so it can move into a shared Kotlin
Multiplatform module if an iOS build is ever wanted.

## Roadmap

- [x] Live GPS tracking with km splits (LocationManager, foreground service, wake lock)
- [x] Import .ics calendars (training plans, races) and show them alongside sessions
- [ ] Exercise picker + rest timer on the strength screen (Flexify reference)
- [ ] Week and agenda calendar views (Fossify reference)
- [ ] Per-exercise and per-distance progress charts
- [ ] GPX/TCX export
