package org.gradle.kotlin.dsl

import com.android.build.api.dsl.SettingsExtension
import org.gradle.api.Action
import org.gradle.api.initialization.Settings

// These two extensions are needed to be able to refer to the settings
// extension by the `android` name in Kotlin Script build files.  See
// https://github.com/gradle/gradle/issues/11210 for more details and to
// track when they can be removed.

val Settings.android: SettingsExtension
    get() = extensions.getByType(SettingsExtension::class.java)

fun Settings.android(configure: Action<SettingsExtension>) =
    extensions.configure(SettingsExtension::class.java, configure)
