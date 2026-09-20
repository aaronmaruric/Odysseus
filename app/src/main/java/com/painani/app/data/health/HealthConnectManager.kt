package com.painani.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import com.painani.app.domain.model.Session
import com.painani.app.domain.model.SessionType
import com.painani.app.domain.model.WeightEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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

    // --- reads ---------------------------------------------------------------------------

    suspend fun heartRate(from: Instant, to: Instant): List<HeartRateSample> {
        val out = mutableListOf<HeartRateSample>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(from, to), pageToken = token)
            )
            page.records.forEach { r -> r.samples.forEach { out += HeartRateSample(it.time, it.beatsPerMinute.toInt()) } }
            token = page.pageToken
        } while (token != null)
        return out.sortedBy { it.time }
    }

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

    /** Weigh-ins from other apps since [since]. Our own writes are filtered out by data origin. */
    suspend fun externalWeights(since: Instant): List<ExternalWeight> {
        val out = mutableListOf<ExternalWeight>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(since), pageToken = token)
            )
            page.records
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .forEach { out += ExternalWeight(it.metadata.id, it.time, it.weight.inKilograms, it.metadata.dataOrigin.packageName) }
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

        val records = mutableListOf<androidx.health.connect.client.records.Record>()
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
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        )

        /** Play Store page for the Health Connect app on devices where it is not built in. */
        const val INSTALL_URL = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"

        @Suppress("unused")
        private val utc = ZoneOffset.UTC
    }
}
