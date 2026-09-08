package com.vinnovateit.latch.core.platform

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LatchFileLogger(
    val logFile: File,
    private val maxSizeBytes: Long = 5 * 1024 * 1024L
) : Logger {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val parent = logFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (logFile.exists() && logFile.length() > maxSizeBytes) {
                val backup = if (parent != null) File(parent, "${logFile.name}.old") else File("${logFile.name}.old")
                if (backup.exists()) backup.delete()
                logFile.renameTo(backup)
            }
            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            FileWriter(logFile, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println("$timestamp [$level] [$tag] $message")
                    throwable?.let { t ->
                        t.printStackTrace(pw)
                    }
                }
            }
        } catch (ignored: Throwable) {}
    }

    override fun d(tag: String, message: String) = log("DEBUG", tag, message)
    override fun w(tag: String, message: String) = log("WARN", tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) = log("ERROR", tag, message, throwable)
}

class CompositeLogger(private val loggers: List<Logger>) : Logger {
    override fun d(tag: String, message: String) = loggers.forEach { it.d(tag, message) }
    override fun w(tag: String, message: String) = loggers.forEach { it.w(tag, message) }
    override fun e(tag: String, message: String, throwable: Throwable?) = loggers.forEach { it.e(tag, message, throwable) }
}

val Platform.logger: Logger
    get() = if (Platform.isInstalled) Platform.services.logger else NoOpLogger
