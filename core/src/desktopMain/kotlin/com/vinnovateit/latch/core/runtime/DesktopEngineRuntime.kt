package com.vinnovateit.latch.core.runtime

import com.vinnovateit.latch.core.data.LatchDatabase
import com.vinnovateit.latch.core.data.buildDatabase
import com.vinnovateit.latch.core.domain.SessionRepository
import com.vinnovateit.latch.core.engine.LatchCommand
import com.vinnovateit.latch.core.engine.LatchEngine
import com.vinnovateit.latch.core.platform.Platform
import com.vinnovateit.latch.core.platform.UserNotifier
import com.vinnovateit.latch.core.portal.PortalHistoryClient
import com.vinnovateit.latch.core.settings.SettingsManager
import com.vinnovateit.latch.core.stats.ThroughputMonitor
import com.vinnovateit.latch.desktop.platform.DesktopPlatformServices
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val ENGINE_SHUTDOWN_TIMEOUT_MS = 5_000L

class DesktopEngineRuntime private constructor(
    val platform: DesktopPlatformServices,
    val database: LatchDatabase,
    val sessions: SessionRepository,
    val engine: LatchEngine,
) {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()

    /** Owned by the runtime so [close] can stop work that outlives a command. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        check(!closed.get()) { "Runtime is closed." }
        if (started.compareAndSet(false, true)) engine.start()
    }

    suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (started.get()) engine.submitAndAwait(LatchCommand.Shutdown, ENGINE_SHUTDOWN_TIMEOUT_MS)
        // Both of these own coroutines that read and write the database, so they
        // have to stop before it closes underneath them.
        backgroundScope.cancel()
        sessions.close()
        // No-op for stores that already write synchronously; the contract
        // exists for any store that defers, since a one-shot CLI exits
        // milliseconds after writing a setting.
        platform.settingsStore.flush()
        database.close()
    }

    companion object {
        /**
         * @param syncHistoryOnStart whether to pull portal history in the
         *   background. Only long-lived owners should: a one-shot CLI command
         *   would otherwise perform a full portal login for `latch-cli status`.
         */
        suspend fun create(
            notifier: UserNotifier,
            echoLogsToStdout: Boolean,
            syncHistoryOnStart: Boolean = false,
        ): DesktopEngineRuntime {
            val platform = DesktopPlatformServices(echoLogsToStdout, notifier)
            Platform.install(platform)
            SettingsManager.initialize(platform.settingsStore)
            val database = buildDatabase()
            val portalClient = PortalHistoryClient(platform.httpTransport)
            val sessions = SessionRepository(database.statsDao(), ThroughputMonitor(platform.counters), portalClient = portalClient)
            sessions.initialize()
            val runtime = DesktopEngineRuntime(
                platform = platform,
                database = database,
                sessions = sessions,
                engine = LatchEngine(platform, sessions),
            )
            if (syncHistoryOnStart) {
                val userId = platform.credentials.userId()
                val password = platform.credentials.password()
                if (!userId.isNullOrBlank() && !password.isNullOrBlank()) {
                    runtime.backgroundScope.launch {
                        sessions.syncPortalHistory(userId, password)
                    }
                }
            }
            return runtime
        }
    }
}
