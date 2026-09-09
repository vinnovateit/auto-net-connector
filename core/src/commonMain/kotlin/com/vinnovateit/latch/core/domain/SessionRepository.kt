package com.vinnovateit.latch.core.domain

import com.vinnovateit.latch.core.data.PortalSessionEntity
import com.vinnovateit.latch.core.data.StatsDao
import com.vinnovateit.latch.core.model.DataUsage
import com.vinnovateit.latch.core.model.LiveConnectionStatus
import com.vinnovateit.latch.core.model.LiveDataPoint
import com.vinnovateit.latch.core.model.PortalSessionRecord
import com.vinnovateit.latch.core.model.SessionSummary
import com.vinnovateit.latch.core.platform.Logger
import com.vinnovateit.latch.core.platform.Platform
import com.vinnovateit.latch.core.platform.logger
import com.vinnovateit.latch.core.portal.PortalHistoryClient
import com.vinnovateit.latch.core.stats.ThroughputMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "SessionRepository"

// Chart renders 150 points max; keep 200 so stopSession() aggregations are
// accurate while memory stays bounded regardless of session length.
// ponytail: flat cap, upgrade to a ring buffer if alloc pressure shows up.
private const val LIVE_HISTORY_CAP = 200

/**
 * Tracks the live session and persists portal history.
 *
 * Ported from the Android singleton, with the Application context, the
 * WorkManager widget enqueue and the TileService nudge all removed. The
 * bindProcessToNetwork calls are gone too -- pinning an entire desktop JVM's
 * traffic to one NIC for the length of a session would be wrong. Notifying the
 * tray happens through [onSessionChanged] instead.
 */
class SessionRepository(
    private val statsDao: StatsDao,
    private val throughput: ThroughputMonitor,
    private val portalClient: PortalHistoryClient? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val logger: Logger = Platform.logger,
) {
    private var sessionUpdateJob: Job? = null

    /** Fired when a session starts or ends, so the tray can refresh. */
    var onSessionChanged: (() -> Unit)? = null

    private val _liveStatus = MutableStateFlow<LiveConnectionStatus?>(null)
    val liveStatus = _liveStatus.asStateFlow()

    private val _sessionSummaries = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessionSummaries = _sessionSummaries.asStateFlow()

    private val _lastSession = MutableStateFlow<SessionSummary?>(null)
    val lastSession = _lastSession.asStateFlow()

    private val _portalHistory = MutableStateFlow<List<PortalSessionRecord>>(emptyList())
    val portalHistory: StateFlow<List<PortalSessionRecord>> = _portalHistory.asStateFlow()

    private val _isHistoryLoaded = MutableStateFlow(false)
    val isHistoryLoaded: StateFlow<Boolean> = _isHistoryLoaded.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun initialize() {
        scope.launch {
            statsDao.getAllSessions()
                .map { rows ->
                    rows.map { row ->
                        SessionSummary(
                            startTimestamp = row.startTime,
                            endTimestamp = row.endTime,
                            totalData = DataUsage(rxBytes = row.rxBytes, txBytes = row.txBytes),
                            history = emptyList(),
                            maxRxBps = row.maxRxBps,
                            maxTxBps = row.maxTxBps,
                        )
                    }
                }
                .collect { summaries ->
                    _sessionSummaries.value = summaries
                    _lastSession.value = summaries.firstOrNull()
                }
        }

        scope.launch {
            statsDao.getAllPortalSessions()
                .map { entities ->
                    entities.map { entity ->
                        PortalSessionRecord(
                            location = entity.location,
                            macAddress = entity.macAddress,
                            loginTime = entity.loginTime,
                            logoutTime = entity.logoutTime,
                            durationFormatted = entity.durationFormatted,
                            durationMillis = entity.durationMillis,
                            uploadBytes = entity.uploadBytes,
                            downloadBytes = entity.downloadBytes,
                            totalBytes = entity.totalBytes,
                            isManual = entity.isManual,
                        )
                    }
                }
                .collect { records ->
                    _portalHistory.value = records
                    _isHistoryLoaded.value = true
                }
        }
    }

    fun startSession() {
        if (sessionUpdateJob?.isActive == true || _liveStatus.value != null) return

        val startTime = System.currentTimeMillis()
        _liveStatus.value = LiveConnectionStatus(
            startTimeMillis = startTime,
            liveData = listOf(LiveDataPoint(startTime, DataUsage(0, 0))),
        )
        throughput.start()

        sessionUpdateJob = scope.launch {
            throughput.dataUsageFlow.collect { usage ->
                val current = _liveStatus.value ?: return@collect
                val next = current.liveData + LiveDataPoint(System.currentTimeMillis(), usage)
                _liveStatus.value = current.copy(
                    liveData = if (next.size > LIVE_HISTORY_CAP) next.drop(1) else next,
                    totalRxBytes = current.totalRxBytes + usage.rxBytes,
                    totalTxBytes = current.totalTxBytes + usage.txBytes,
                    maxRxBps = maxOf(current.maxRxBps, usage.rxBps),
                    maxTxBps = maxOf(current.maxTxBps, usage.txBps),
                )
            }
        }
        onSessionChanged?.invoke()
    }

    fun stopSession() {
        finishActiveSession()
    }

    /** Completes persistence before returning, for orderly process shutdown. */
    suspend fun stopSessionAndAwait() {
        finishActiveSession()
    }

    private fun finishActiveSession() {
        if (_liveStatus.value == null) return

        sessionUpdateJob?.cancel()
        sessionUpdateJob = null
        throughput.stop()
        _liveStatus.value = null
        onSessionChanged?.invoke()
    }

    private var lastSyncTimeMillis: Long = 0L

    suspend fun syncPortalHistory(
        userId: String,
        password: String,
        host: String = PortalHistoryClient.DEFAULT_PORTAL_HOST,
        force: Boolean = false,
    ): Result<Unit> {
        val client = portalClient ?: return Result.failure(IllegalStateException("Portal client not configured"))
        if (_isSyncing.value) return Result.success(Unit)
        val now = System.currentTimeMillis()
        if (!force && (now - lastSyncTimeMillis < 30_000L) && _portalHistory.value.isNotEmpty()) {
            return Result.success(Unit)
        }
        lastSyncTimeMillis = now
        logger.d(TAG, "syncPortalHistory starting...")
        _isSyncing.value = true
        return try {
            val result = client.fetchHistory(userId, password, host = host)
            if (result.isSuccess) {
                val incoming = result.getOrThrow().filter {
                    it.loginTime > 0 && (it.uploadBytes > 0L || it.downloadBytes > 0L)
                }
                logger.d(TAG, "incoming records: ${incoming.size}")
                val todayKey = com.vinnovateit.latch.core.stats.formatDate(now, "yyyy-MM-dd")
                val existing = _portalHistory.value

                if (incoming.isEmpty() && existing.isNotEmpty()) {
                    logger.d(TAG, "incoming empty but existing data present; preserving existing records")
                    return Result.success(Unit)
                }

                // Lock already-validated past dates: do not overwrite them
                val validatedPastDates = existing
                    .filter { !it.isManual && it.loginTime > 0 && com.vinnovateit.latch.core.stats.formatDate(it.loginTime, "yyyy-MM-dd") != todayKey && (it.uploadBytes > 0L || it.downloadBytes > 0L) }
                    .map { com.vinnovateit.latch.core.stats.formatDate(it.loginTime, "yyyy-MM-dd") }
                    .toSet()
                logger.d(TAG, "locked past dates: ${validatedPastDates.size}")

                val existingLockedRecords = existing.filter {
                    val date = com.vinnovateit.latch.core.stats.formatDate(it.loginTime, "yyyy-MM-dd")
                    !it.isManual && date != todayKey && date in validatedPastDates
                }
                val newAcceptedRecords = incoming.filter {
                    val date = com.vinnovateit.latch.core.stats.formatDate(it.loginTime, "yyyy-MM-dd")
                    date == todayKey || date !in validatedPastDates
                }

                // Reconcile manual records: match against incoming server records
                val existingManualRecords = existing.filter { it.isManual }
                val pendingManualRecords = existingManualRecords.filter { man ->
                    incoming.none { inc -> matchesSession(man, inc) }
                }
                logger.d(TAG, "manual sessions before sync: ${existingManualRecords.size}, remaining pending after reconciliation: ${pendingManualRecords.size}")

                val merged = (existingLockedRecords + newAcceptedRecords + pendingManualRecords)
                    .distinctBy { Triple(it.loginTime, it.durationMillis, it.totalBytes) }
                    .sortedByDescending { it.loginTime }
                logger.d(TAG, "merged records: ${merged.size}")

                val entities = merged.map {
                    PortalSessionEntity(
                        location = it.location,
                        macAddress = it.macAddress,
                        loginTime = it.loginTime,
                        logoutTime = it.logoutTime,
                        durationFormatted = it.durationFormatted,
                        durationMillis = it.durationMillis,
                        uploadBytes = it.uploadBytes,
                        downloadBytes = it.downloadBytes,
                        totalBytes = it.totalBytes,
                        isManual = it.isManual,
                    )
                }
                statsDao.replacePortalSessions(entities)
                logger.d(TAG, "Persisted ${entities.size} portal session records to database")
                _portalHistory.value = merged
                Result.success(Unit)
            } else {
                val ex = result.exceptionOrNull() ?: Exception("Unknown portal sync error")
                logger.e(TAG, "syncPortalHistory failed: ${ex.message}", ex)
                Result.failure(ex)
            }
        } catch (e: Throwable) {
            logger.e(TAG, "syncPortalHistory error: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    fun recordManualSession(record: PortalSessionRecord) {
        val manualRecord = record.copy(isManual = true)
        val entity = PortalSessionEntity(
            location = manualRecord.location,
            macAddress = manualRecord.macAddress,
            loginTime = manualRecord.loginTime,
            logoutTime = manualRecord.logoutTime,
            durationFormatted = manualRecord.durationFormatted,
            durationMillis = manualRecord.durationMillis,
            uploadBytes = manualRecord.uploadBytes,
            downloadBytes = manualRecord.downloadBytes,
            totalBytes = manualRecord.totalBytes,
            isManual = true,
        )
        val current = _portalHistory.value
        val updated = (listOf(manualRecord) + current)
            .distinctBy { "${it.loginTime}_${it.durationMillis}_${it.totalBytes}" }
            .sortedByDescending { it.loginTime }
        _portalHistory.value = updated
        _isHistoryLoaded.value = true

        scope.launch {
            statsDao.insertAllPortalSessions(listOf(entity))
            logger.d(TAG, "Persisted manual portal session: ${manualRecord.durationFormatted}, ${manualRecord.totalBytes} bytes")
        }
    }

    private fun matchesSession(manual: PortalSessionRecord, incoming: PortalSessionRecord): Boolean {
        val durationDiff = kotlin.math.abs(manual.durationMillis - incoming.durationMillis)
        val durationMatches = durationDiff <= 10_000L || manual.durationFormatted == incoming.durationFormatted
        if (!durationMatches) return false

        val logoutDiff = kotlin.math.abs(manual.logoutTime - incoming.logoutTime)
        val loginDiff = kotlin.math.abs(manual.loginTime - incoming.loginTime)
        val timeMatches = logoutDiff <= 180_000L || loginDiff <= 180_000L

        val bytesDiff = kotlin.math.abs(manual.totalBytes - incoming.totalBytes)
        val bytesMatches = bytesDiff <= 10_240L

        return timeMatches || (bytesMatches && durationMatches)
    }

    fun clearHistory() {
        scope.launch {
            statsDao.clearAllSessions()
            statsDao.clearAllPortalSessions()
            _portalHistory.value = emptyList()
        }
    }

    fun close() {
        scope.cancel()
    }
}
