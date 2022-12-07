package com.example.android.lint.desugaring.library

import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.ZonedDateTime

class Library {
    fun timeTest(time: java.time.LocalTime) {
        time.format(DateTimeFormatter.ISO_LOCAL_DATE)
        println(time.hour)
    }
    fun nowUtc(): ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
}
