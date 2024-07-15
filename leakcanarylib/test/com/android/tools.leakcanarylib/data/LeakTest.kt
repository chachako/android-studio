/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.leakcanarylib.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LeakTest {

    @Test
    fun `parseSingleApplicationLeak - complete leak information found and can be parsed`() {
        val applicationLeakText = """
            2200 bytes retained by leaking objects
            Signature: 41c3c2258578581a1b0c9f78b59966266ed118b9
            ┬───
            │ GC Root: Input or output parameters in native code
            │
            ├─ dalvik.system.PathClassLoader instance
            │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
            │    ↓ ClassLoader.runtimeInternalObjects
            ├─ java.lang.Object[] array
            │    Leaking: NO (InternalLeakCanary↓ is not leaking)
            │    ↓ Object[747]
            ├─ leakcanary.internal.InternalLeakCanary class
            │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
            │    ↓ static InternalLeakCanary.resumedActivity
            ├─ com.amaze.filemanager.ui.activities.MainActivity instance
            │    Leaking: NO (AppBarLayout↓ is not leaking and Activity#mDestroyed is false)
            │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    mApplication instance of com.amaze.filemanager.application.AppConfig
            │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
            │    ↓ MainActivity.appBarLayout
            ├─ com.google.android.material.appbar.AppBarLayout instance
            │    Leaking: NO (AppsListFragment↓ is not leaking and View attached)
            │    View is part of a window view hierarchy
            │    View.mAttachInfo is not null (view attached)
            │    View.mID = R.id.lin
            │    View.mWindowAttachCount = 1
            │    mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    ↓ AppBarLayout.listeners
            ├─ java.util.ArrayList instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ ArrayList[2]
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment${'$'}ExternalSyntheticLambda0 instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ AppsListFragment${'$'}ExternalSyntheticLambda0.f${'$'}0
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment instance
            │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
            │    ↓ AppsListFragment.rootView
            │                       ~~~~~~~~
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.AppsListFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = 63dee33b-9463-414f-89e0-4cb58974e0db
            ​     watchDurationMillis = 234158
            ​     retainedDurationMillis = 229157
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false

            """.trimIndent()

        val applicationLeaks = Leak.fromString(applicationLeakText, LeakType.APPLICATION_LEAKS)
        assertEquals(applicationLeaks[0].toString().trim(), applicationLeakText.trim())

        val fromApplicationLeak = Leak.fromString(applicationLeaks[0].toString().trim(), LeakType.APPLICATION_LEAKS)
        assertEquals(fromApplicationLeak, applicationLeaks)
    }

    @Test
    fun `parseMultipleApplicationLeak - complete leak information found and can be parsed`() {
        val applicationLeakText = """
            2200 bytes retained by leaking objects
            Signature: 41c3c2258578581a1b0c9f78b59966266ed118b9
            ┬───
            │ GC Root: Input or output parameters in native code
            │
            ├─ dalvik.system.PathClassLoader instance
            │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
            │    ↓ ClassLoader.runtimeInternalObjects
            ├─ java.lang.Object[] array
            │    Leaking: NO (InternalLeakCanary↓ is not leaking)
            │    ↓ Object[747]
            ├─ leakcanary.internal.InternalLeakCanary class
            │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
            │    ↓ static InternalLeakCanary.resumedActivity
            ├─ com.amaze.filemanager.ui.activities.MainActivity instance
            │    Leaking: NO (AppBarLayout↓ is not leaking and Activity#mDestroyed is false)
            │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    mApplication instance of com.amaze.filemanager.application.AppConfig
            │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
            │    ↓ MainActivity.appBarLayout
            ├─ com.google.android.material.appbar.AppBarLayout instance
            │    Leaking: NO (AppsListFragment↓ is not leaking and View attached)
            │    View is part of a window view hierarchy
            │    View.mAttachInfo is not null (view attached)
            │    View.mID = R.id.lin
            │    View.mWindowAttachCount = 1
            │    mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    ↓ AppBarLayout.listeners
            ├─ java.util.ArrayList instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ ArrayList[2]
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment${'$'}ExternalSyntheticLambda0 instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ AppsListFragment${'$'}ExternalSyntheticLambda0.f${'$'}0
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment instance
            │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
            │    ↓ AppsListFragment.rootView
            │                       ~~~~~~~~
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.AppsListFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = 63dee33b-9463-414f-89e0-4cb58974e0db
            ​     watchDurationMillis = 234158
            ​     retainedDurationMillis = 229157
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false

            8928 bytes retained by leaking objects
            Displaying only 1 leak trace out of 4 with the same signature
            Signature: 2d8918c3076020f19fa7ab4b6ca5cb4423772b20
            ┬───
            │ GC Root: Input or output parameters in native code
            │
            ├─ dalvik.system.PathClassLoader instance
            │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
            │    ↓ ClassLoader.runtimeInternalObjects
            ├─ java.lang.Object[] array
            │    Leaking: NO (InternalLeakCanary↓ is not leaking)
            │    ↓ Object[747]
            ├─ leakcanary.internal.InternalLeakCanary class
            │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
            │    ↓ static InternalLeakCanary.resumedActivity
            ├─ com.amaze.filemanager.ui.activities.MainActivity instance
            │    Leaking: NO (TabFragment↓ is not leaking and Activity#mDestroyed is false)
            │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    mApplication instance of com.amaze.filemanager.application.AppConfig
            │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
            │    ↓ ComponentActivity.mOnConfigurationChangedListeners
            ├─ java.util.concurrent.CopyOnWriteArrayList instance
            │    Leaking: NO (TabFragment↓ is not leaking)
            │    ↓ CopyOnWriteArrayList[2]
            ├─ androidx.fragment.app.FragmentManager${'$'}ExternalSyntheticLambda0 instance
            │    Leaking: NO (TabFragment↓ is not leaking)
            │    ↓ FragmentManager${'$'}ExternalSyntheticLambda0.f${'$'}0
            ├─ androidx.fragment.app.FragmentManagerImpl instance
            │    Leaking: NO (TabFragment↓ is not leaking)
            │    ↓ FragmentManager.mParent
            ├─ com.amaze.filemanager.ui.fragments.TabFragment instance
            │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
            │    ↓ TabFragment.rootView
            │                  ~~~~~~~~
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = d8a25ea4-cdd7-4a2a-b459-afe3956b109b
            ​     watchDurationMillis = 242280
            ​     retainedDurationMillis = 237276
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            """.trimIndent()

        val applicationLeaks = Leak.fromString(applicationLeakText, LeakType.APPLICATION_LEAKS)

        assertEquals(applicationLeaks.size, 2)
        assertEquals(
            applicationLeaks[0].toString().trim() + "\n\n"
                    + applicationLeaks[1].toString().trim(), applicationLeakText.trim()
        )
    }

    @Test
    fun `parseMultipleLibraryLeak - complete leak information found and can be parsed`() {
        val libraryLeakText = """
            Leak pattern: native global variable referencing com.example.library.LeakingClass
            Description: LeakingClass holds a static reference to LeakedObject
            2200 bytes retained by leaking objects
            Signature: 41c3c2258578581a1b0c9f78b59966266ed118b9
            ┬───
            │ GC Root: Input or output parameters in native code
            │
            ├─ dalvik.system.PathClassLoader instance
            │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
            │    ↓ ClassLoader.runtimeInternalObjects
            ├─ java.lang.Object[] array
            │    Leaking: NO (InternalLeakCanary↓ is not leaking)
            │    ↓ Object[747]
            ├─ leakcanary.internal.InternalLeakCanary class
            │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
            │    ↓ static InternalLeakCanary.resumedActivity
            ├─ com.amaze.filemanager.ui.activities.MainActivity instance
            │    Leaking: NO (AppBarLayout↓ is not leaking and Activity#mDestroyed is false)
            │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    mApplication instance of com.amaze.filemanager.application.AppConfig
            │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
            │    ↓ MainActivity.appBarLayout
            ├─ com.google.android.material.appbar.AppBarLayout instance
            │    Leaking: NO (AppsListFragment↓ is not leaking and View attached)
            │    View is part of a window view hierarchy
            │    View.mAttachInfo is not null (view attached)
            │    View.mID = R.id.lin
            │    View.mWindowAttachCount = 1
            │    mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    ↓ AppBarLayout.listeners
            ├─ java.util.ArrayList instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ ArrayList[2]
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment${'$'}ExternalSyntheticLambda0 instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ AppsListFragment${'$'}ExternalSyntheticLambda0.f${'$'}0
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment instance
            │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
            │    ↓ AppsListFragment.rootView
            │                       ~~~~~~~~
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.AppsListFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = 63dee33b-9463-414f-89e0-4cb58974e0db
            ​     watchDurationMillis = 234158
            ​     retainedDurationMillis = 229157
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false

            Leak pattern: native global variable referencing com.example.library.LeakingClass
            Description: LeakingClass holds a static reference to LeakedObject
            8928 bytes retained by leaking objects
            Displaying only 1 leak trace out of 4 with the same signature
            Signature: 2d8918c3076020f19fa7ab4b6ca5cb4423772b20
            ┬───
            │ GC Root: Input or output parameters in native code
            │
            ├─ dalvik.system.PathClassLoader instance
            │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
            │    ↓ ClassLoader.runtimeInternalObjects
            ├─ java.lang.Object[] array
            │    Leaking: NO (InternalLeakCanary↓ is not leaking)
            │    ↓ Object[747]
            ├─ leakcanary.internal.InternalLeakCanary class
            │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
            │    ↓ static InternalLeakCanary.resumedActivity
            ├─ com.amaze.filemanager.ui.activities.MainActivity instance
            │    Leaking: NO (TabFragment↓ is not leaking and Activity#mDestroyed is false)
            │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    mApplication instance of com.amaze.filemanager.application.AppConfig
            │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
            │    ↓ ComponentActivity.mOnConfigurationChangedListeners
            ├─ java.util.concurrent.CopyOnWriteArrayList instance
            │    Leaking: NO (TabFragment↓ is not leaking)
            │    ↓ CopyOnWriteArrayList[2]
            ├─ androidx.fragment.app.FragmentManager${'$'}ExternalSyntheticLambda0 instance
            │    Leaking: NO (TabFragment↓ is not leaking)
            │    ↓ FragmentManager${'$'}ExternalSyntheticLambda0.f${'$'}0
            ├─ androidx.fragment.app.FragmentManagerImpl instance
            │    Leaking: NO (TabFragment↓ is not leaking)
            │    ↓ FragmentManager.mParent
            ├─ com.amaze.filemanager.ui.fragments.TabFragment instance
            │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
            │    ↓ TabFragment.rootView
            │                  ~~~~~~~~
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = d8a25ea4-cdd7-4a2a-b459-afe3956b109b
            ​     watchDurationMillis = 242280
            ​     retainedDurationMillis = 237276
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            """.trimIndent()

        val libraryLeaks = Leak.fromString(libraryLeakText, LeakType.LIBRARY_LEAKS)
        assertEquals(libraryLeaks.size, 2)

        val libraryLeakString = libraryLeaks[0].toString().trim() +
                "\n\n" + libraryLeaks[1].toString().trim()

        val expectedLibraryLeakString = libraryLeakText.lines()
            .filterNot { it.contains("Leak pattern:") || it.contains("Description:") }
            .joinToString("\n").trim()
        assertEquals(libraryLeakString, expectedLibraryLeakString)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseSingleApplicationLeak - complete leak information found and parse failed`() {
        val applicationLeakText = """
            2200 bytes retained by leaking objects
            ┬───
            │
            │
            ├─ dalvik.system.PathClassLoader instance
            │    Leaking: NO (InternalLeakCanary↓ is not leaking and A ClassLoader is never leaking)
            │    ↓ ClassLoader.runtimeInternalObjects
            ├─ java.lang.Object[] array
            │    Leaking: NO (InternalLeakCanary↓ is not leaking)
            │    ↓ Object[747]
            ├─ leakcanary.internal.InternalLeakCanary class
            │    Leaking: NO (MainActivity↓ is not leaking and a class is never leaking)
            │    ↓ static InternalLeakCanary.resumedActivity
            ├─ com.amaze.filemanager.ui.activities.MainActivity instance
            │    Leaking: NO (AppBarLayout↓ is not leaking and Activity#mDestroyed is false)
            │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    mApplication instance of com.amaze.filemanager.application.AppConfig
            │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
            │    ↓ MainActivity.appBarLayout
            ├─ com.google.android.material.appbar.AppBarLayout instance
            │    Leaking: NO (AppsListFragment↓ is not leaking and View attached)
            │    View is part of a window view hierarchy
            │    View.mAttachInfo is not null (view attached)
            │    View.mID = R.id.lin
            │    View.mWindowAttachCount = 1
            │    mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    ↓ AppBarLayout.listeners
            ├─ java.util.ArrayList instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ ArrayList[2]
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment${'$'}ExternalSyntheticLambda0 instance
            │    Leaking: NO (AppsListFragment↓ is not leaking)
            │    ↓ AppsListFragment${'$'}ExternalSyntheticLambda0.f${'$'}0
            ├─ com.amaze.filemanager.ui.fragments.AppsListFragment instance
            │    Leaking: NO (Fragment.mLifecycleRegistry.state is CREATED)
            │    ↓ AppsListFragment.rootView
            │                       ~~~~~~~~
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.AppsListFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = 63dee33b-9463-414f-89e0-4cb58974e0db
            ​     watchDurationMillis = 234158
            ​     retainedDurationMillis = 229157
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false

            """.trimIndent()

        // Parsing failed since gc root not found.
        Leak.fromString(applicationLeakText, LeakType.APPLICATION_LEAKS)
    }
}
