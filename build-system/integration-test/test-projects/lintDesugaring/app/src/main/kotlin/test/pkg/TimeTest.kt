package test.pkg

import com.example.android.lint.desugaring.library.Library
import java.time.format.DateTimeFormatter

fun timeTest(time: java.time.LocalTime) {
    time.format(DateTimeFormatter.ISO_LOCAL_DATE)
    println(time.hour)
}

fun test(library: Library) {
    println(library.nowUtc())
}
