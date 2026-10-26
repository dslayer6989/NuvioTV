package com.nuvio.tv.livetv

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Wall clock and local-time formatting for the guide. */
internal object LiveTvClock {
    fun nowEpochMs(): Long = System.currentTimeMillis()

    /** Short local time of day, e.g. "8:30 PM" or "20:30" depending on the device locale. */
    fun formatTimeOfDay(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    /** Short local weekday + date, e.g. "Tue, Sep 29". */
    fun formatDay(epochMs: Long): String =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    /** Epoch ms of local midnight for the day containing [epochMs]. */
    fun localDayStart(epochMs: Long): Long =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
}
