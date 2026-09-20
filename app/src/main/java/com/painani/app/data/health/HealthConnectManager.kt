package com.painani.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import com.painani.app.domain.model.DailyHealth
import com.painani.app.domain.model.Session
import com.painani.app.domain.model.SessionType
import com.painani.app.domain.model.WeightEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** One heart-rate sample from any connected device. */
data class HeartRateSample(val time: Instant, val bpm: Int)

/** A weigh-in recorded by something other than us (watch, scale, Samsung Health). */
data class ExternalWeight(val recordId: String, val time: Instant, val kg: Double, val origin: String)

data class DailyReadout(
    val steps: Long?,
    val restingHr: Int?,
    val sleepLastNight: Duration?,
)

enum class HealthStatus { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, UNSUPPORTED }

/**
 * Thin wrapper over the Health Connect client. Everything here is a plain suspend call;
 * policy (when to sync, what to write) lives in [HealthSync].
 */
class HealthConnectManager(private val context: Context) {

    val status: HealthStatus
        get() = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.UPDATE_REQUIRED
            HealthConnectClient.SDK_UNAVAILABLE -> HealthStatus.NOT_INSTALLED
            else -> HealthStatus.UNSUPPORTED
        }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissionContract = PermissionController.createRequestPermissionResultContract()

    suspend fun hasAllPermissions(): Boolean =
        status == HealthStatus.AVAILABLE && client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    /**
     * Whether this Health Connect build lets us read while the app is not on screen. Older
     * providers (Android 13 and below, or an out-of-date module) reject every background read.
     */
    val supportsBackgroundRead: Boolean
        get() = featureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)

    /** Whether we may read further back than 30 days before permissions were first granted. */
    val supportsHistoryRead: Boolean
        get() = featureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)

    suspend fun hasBackgroundRead(): Boolean =
        supportsBackgroundRead && PERMISSION_BACKGROUND in client.permissionController.getGrantedPermissions()

    suspend fun hasHistoryRead(): Boolean =
        supportsHistoryRead && PERMISSION_HISTORY in client.permissionController.getGrantedPermissions()

    /** Everything worth asking for on this device: the data permissions plus whichever extras it supports. */
    fun requestablePermissions(): Set<String> = buildSet {
        addAll(PERMISSIONS)
        if (supportsBackgroundRead) add(PERMISSION_BACKGROUND)
        if (supportsHistoryRead) add(PERMISSION_HISTORY)
    }

    private fun featureAvailable(feature: Int): Boolean =
        status == HealthStatus.AVAILABLE &&
            runCatching { client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE }
                .getOrDefault(false)

    // --- reads ---------------------------------------------------------------------------

    suspend fun heartRate(from: Instant, to: Instant): List<HeartRateSample> =
        readAll(HeartRateRecord::class, from, to)
            .flatMap { r -> r.samples.map { HeartRateSample(it.time, it.beatsPerMinute.toInt()) } }
            .sortedBy { it.time }

    suspend fun dailyReadout(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): DailyReadout {
        val dayStart = today.atStartOfDay(zone).toInstant()
        val now = Instant.now()

        val steps = runCatching {
            client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, now)))[StepsRecord.COUNT_TOTAL]
        }.getOrNull()

        val restingHr = runCatching {
            client.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(dayStart.minus(Duration.ofDays(2)), now), ascendingOrder = false, pageSize = 1)
            ).records.firstOrNull()?.beatsPerMinute?.toInt()
        }.getOrNull()

        // "Last night": sessions ending between yesterday noon and today noon.
        val sleep = runCatching {
            val from = today.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
            val to = today.atTime(12, 0).atZone(zone).toInstant()
            val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, to))).records
            if (sessions.isEmpty()) null
            else sessions.fold(Duration.ZERO) { acc, s -> acc + Duration.between(s.startTime, s.endTime) }
        }.getOrNull()

        return DailyReadout(steps, restingHr, sleep)
    }

    /**
     * One [DailyHealth] per day in [from, to], inclusive. Steps, distance, calories and exercise
     * time come from aggregates (Health Connect de-duplicates overlapping sources for us); sleep
     * and resting heart rate are read as records and bucketed by day. Days with nothing recorded
     * are left out.
     */
    suspend fun dailySummaries(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DailyHealth> {
        if (to.isBefore(from)) return emptyList()
        val start = from.atStartOfDay(zone)
        val end = to.plusDays(1).atStartOfDay(zone)
        val days = linkedMapOf<LocalDate, DailyHealth>()
        fun day(d: LocalDate) = days.getOrPut(d) { DailyHealth(d) }

        runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start.toLocalDateTime(), end.toLocalDateTime()),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).forEach { bucket ->
                val d = bucket.startTime.toLocalDate()
                val r = bucket.result
                days[d] = day(d).copy(
                    steps = r[StepsRecord.COUNT_TOTAL],
                    distanceMeters = r[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                    activeCalories = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                    exerciseMinutes = r[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()?.toInt(),
                )
            }
        }

        // Sleep: a session belongs to the day it ended on. Read from a day earlier so a night
        // that started before [from] but ended inside the window is still counted.
        runCatching {
            readAll(SleepSessionRecord::class, start.minusDays(1).toInstant(), end.toInstant())
                .groupBy { it.endTime.atZone(zone).toLocalDate() }
                .filterKeys { !it.isBefore(from) && !it.isAfter(to) }
                .forEach { (d, list) ->
                    var total = 0L; var deep = 0L; var light = 0L; var rem = 0L; var awake = 0L
                    list.forEach { s ->
                        total += Duration.between(s.startTime, s.endTime).toMinutes()
                        s.stages.forEach { st ->
                            val m = Duration.between(st.startTime, st.endTime).toMinutes()
                            when (st.stage) {
                                SleepSessionRecord.STAGE_TYPE_DEEP -> deep += m
                                SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> light += m
                                SleepSessionRecord.STAGE_TYPE_REM -> rem += m
                                SleepSessionRecord.STAGE_TYPE_AWAKE,
                                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += m
                            }
                        }
                    }
                    val hasStages = deep + light + rem + awake > 0
                    days[d] = day(d).copy(
                        sleepMinutes = total.toInt(),
                        sleepDeepMinutes = deep.toInt().takeIf { hasStages },
                        sleepLightMinutes = light.toInt().takeIf { hasStages },
                        sleepRemMinutes = rem.toInt().takeIf { hasStages },
                        sleepAwakeMinutes = awake.toInt().takeIf { hasStages },
                        sleepStart = list.minOf { it.startTime },
                        sleepEnd = list.maxOf { it.endTime },
                    )
                }
        }

        runCatching {
            readAll(RestingHeartRateRecord::class, start.toInstant(), end.toInstant())
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .forEach { (d, list) ->
                    val latest = list.maxByOrNull { it.time } ?: return@forEach
                    days[d] = day(d).copy(restingHr = latest.beatsPerMinute.toInt())
                }
        }

        return days.values.filter { !it.isEmpty }.sortedBy { it.date }
    }

    /** Weigh-ins from other apps since [since]. Our own writes are filtered out by data origin. */
    suspend fun externalWeights(since: Instant): List<ExternalWeight> =
        readAll(WeightRecord::class, since, Instant.now())
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
            .map { ExternalWeight(it.metadata.id, it.time, it.weight.inKilograms, it.metadata.dataOrigin.packageName) }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = token))
            out += page.records
            token = page.pageToken
        } while (token != null)
        return out
    }

    // --- writes --------------------------------------------------------------------------

    /**
     * Mirrors a session into Health Connect. clientRecordId makes this an upsert, so re-syncing the
     * same session never duplicates it.
     */
    suspend fun writeSession(session: Session) {
        val start = session.startedAt
        val end = start.plusMillis(session.durationMillis.coerceAtLeast(1_000))
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val clientId = "painani-session-${session.id}"
        val meta = Metadata.manualEntry(clientRecordId = clientId, device = Device(type = Device.TYPE_PHONE))

        val records = mutableListOf<Record>()
        records += ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = offset,
            exerciseType = when (session.type) {
                SessionType.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                SessionType.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            },
            title = when (session.type) {
                SessionType.RUN -> "Run"
                SessionType.STRENGTH -> "Strength"
            },
            notes = session.notes.ifBlank { null },
            metadata = meta,
            exerciseRoute = session.trackPoints.takeIf { it.size >= 2 }?.let { pts ->
                ExerciseRoute(
                    pts.map { p ->
                        ExerciseRoute.Location(
                            time = Instant.ofEpochMilli(p.timeMillis),
                            latitude = p.latitude,
                            longitude = p.longitude,
                            altitude = p.altitudeMeters?.let { Length.meters(it) },
                            horizontalAccuracy = p.accuracyMeters?.let { Length.meters(it.toDouble()) },
                        )
                    }
                )
            },
        )
        session.distanceMeters?.takeIf { it > 0 }?.let { d ->
            records += DistanceRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                distance = Length.meters(d),
                metadata = Metadata.manualEntry(clientRecordId = "$clientId-distance", device = Device(type = Device.TYPE_PHONE)),
            )
        }
        client.insertRecords(records)
    }

    suspend fun writeWeight(entry: WeightEntry) {
        client.insertRecords(
            listOf(
                WeightRecord(
                    time = entry.at,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(entry.at),
                    weight = Mass.kilograms(entry.weightKg),
                    metadata = Metadata.manualEntry(clientRecordId = "painani-weight-${entry.id}"),
                )
            )
        )
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        )

        /** Lets a scheduled sync read while the app is closed. Only on Health Connect builds that support it. */
        const val PERMISSION_BACKGROUND = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

        /** Lets the first sync back-fill more than 30 days of history. */
        const val PERMISSION_HISTORY = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

        /** Play Store page for the Health Connect app on devices where it is not built in. */
        const val INSTALL_URL = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
    }
}
