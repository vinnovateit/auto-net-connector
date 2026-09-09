package com.vinnovateit.latch.core.model

data class PortalSessionRecord(
    val location: String,
    val macAddress: String,
    val loginTime: Long,
    val logoutTime: Long,
    val durationFormatted: String,
    val durationMillis: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val isManual: Boolean = false,
)
