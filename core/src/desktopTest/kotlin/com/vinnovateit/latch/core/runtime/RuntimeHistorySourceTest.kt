package com.vinnovateit.latch.core.runtime

import com.vinnovateit.latch.core.data.PortalSessionEntity
import com.vinnovateit.latch.core.platform.UserNotifier
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * `latch-cli history` used to read the local session table, which stopped being
 * written when portal history became the source of truth, so it returned an
 * empty list forever.
 */
class RuntimeHistorySourceTest {

    @Test
    fun `history is served from portal sessions`() = runBlocking {
        val directory = createTempDirectory("latch-history-").toFile()
        val previous = System.getProperty("latch.dataDir")
        System.setProperty("latch.dataDir", directory.absolutePath)
        try {
            val runtime = DesktopEngineRuntime.create(HistoryNoOpNotifier, echoLogsToStdout = false)
            runtime.database.statsDao().replacePortalSessions(
                listOf(
                    PortalSessionEntity(
                        location = "VIT-Vellore",
                        macAddress = "9e:85:16:b5:22:eb",
                        loginTime = 1_700_000_000_000L,
                        logoutTime = 1_700_000_600_000L,
                        durationFormatted = "10 min 0 sec",
                        durationMillis = 600_000L,
                        uploadBytes = 15_000L,
                        downloadBytes = 116_000L,
                        totalBytes = 131_000L,
                    )
                )
            )

            val service = RuntimeCommandService(OwnerKind.DESKTOP, runtime)
            val response = service.execute(
                InstanceRequest(
                    version = INSTANCE_PROTOCOL_VERSION,
                    token = "validated-by-coordinator",
                    requestId = "request-1",
                    command = RuntimeCommand.HISTORY,
                )
            )

            val sessions = response.data.getValue("sessions")
            assertTrue(response.ok, "history should succeed")
            assertTrue(
                sessions.contains("\"start\":1700000000000"),
                "portal login time should be reported, got: $sessions",
            )
            assertTrue(
                sessions.contains("\"rx\":116000"),
                "portal download bytes should be reported, got: $sessions",
            )
            runtime.close()
        } finally {
            if (previous == null) System.clearProperty("latch.dataDir") else System.setProperty("latch.dataDir", previous)
            directory.deleteRecursively()
        }
    }
}

private object HistoryNoOpNotifier : UserNotifier {
    override fun showOngoing(title: String, text: String) = Unit
    override fun notifyTransient(title: String, text: String, isError: Boolean) = Unit
    override fun hideOngoing() = Unit
}
