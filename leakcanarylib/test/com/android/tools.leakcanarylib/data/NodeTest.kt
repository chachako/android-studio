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

import com.android.tools.leakcanarylib.data.Node.Companion.ZERO_WIDTH_SPACE
import junit.framework.TestCase.assertEquals
import org.junit.Test

class NodeTest {

    @Test
    fun `test parse instance field reference with multiple origin object lines`() {
        val inputString = """
            ├─ com.amaze.filemanager.ui.activities.MainActivity instance
            │    Leaking: NO (LinearLayout↓ is not leaking and Activity#mDestroyed is false)
            │    mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    mApplication instance of com.amaze.filemanager.application.AppConfig
            │    mBase instance of androidx.appcompat.view.ContextThemeWrapper
            │    ↓ MainActivity.indicator_layout
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "com.amaze.filemanager.ui.activities.MainActivity",
            notes = listOf(
                "mainActivity instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false",
                "mApplication instance of com.amaze.filemanager.application.AppConfig",
                "mBase instance of androidx.appcompat.view.ContextThemeWrapper"
            ),
            leakingStatus = LeakingStatus.NO,
            leakingStatusReason = "LinearLayout↓ is not leaking and Activity#mDestroyed is false",
            retainedByteSize = null,
            retainedObjectCount = null,
            referencingField = ReferencingField(
                className = "MainActivity",
                referenceName = "indicator_layout",
                type = ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                isLikelyCause = false
            )
        )

        val outputInstance = Node.fromString(inputString)
        assertEquals(expected, outputInstance)
    }

    @Test
    fun `test parse instance field reference with simple origin object`() {
        val inputString = """
            ├─ android.com.java.profilertester.MainLooperThread instance
            │    Leaking: NO (PathClassLoader↓ is not leaking)
            │    Thread name: 'Thread-2'
            │    ↓ Thread.contextClassLoader
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "android.com.java.profilertester.MainLooperThread",
            notes = listOf("Thread name: 'Thread-2'"),
            leakingStatus = LeakingStatus.NO,
            leakingStatusReason = "PathClassLoader↓ is not leaking",
            retainedByteSize = null,
            retainedObjectCount = null,
            referencingField = ReferencingField(
                type = ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                className = "Thread",
                referenceName = "contextClassLoader",
                isLikelyCause = false
            )
        )

        val outputInstance = Node.fromString(inputString)
        assertEquals(expected, outputInstance)
    }

    @Test
    fun `test parse static field reference`() {
        val inputString = """
            ├─ leakcanary.internal.InternalLeakCanary class
            │    Leaking: NO (FragmentHostActivity↓ is not leaking and a class is never leaking)
            │    ↓ static InternalLeakCanary.resumedActivity
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.CLASS,
            className = "leakcanary.internal.InternalLeakCanary",
            notes = emptyList(),
            leakingStatus = LeakingStatus.NO,
            leakingStatusReason = "FragmentHostActivity↓ is not leaking and a class is never leaking",
            retainedByteSize = null,
            retainedObjectCount = null,
            referencingField = ReferencingField(
                type = ReferencingField.ReferencingFieldType.STATIC_FIELD,
                className = "InternalLeakCanary",
                referenceName = "resumedActivity",
                isLikelyCause = false
            )
        )

        val outputInstance = Node.fromString(inputString)
        assertEquals(expected, outputInstance)
    }

    @Test
    fun `test parse instance field reference with suspect marker`() {
        val inputString = """
            ├─ com.amaze.filemanager.ui.views.Indicator instance
            │    Leaking: NO (View attached)
            │    View is part of a window view hierarchy
            │    View.mAttachInfo is not null (view attached)
            │    View.mID = R.id.indicator
            │    View.mWindowAttachCount = 1
            │    mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
            │    ↓ Indicator.viewPager
            │                ~~~~~~~~~
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "com.amaze.filemanager.ui.views.Indicator",
            notes = listOf(
                "View is part of a window view hierarchy",
                "View.mAttachInfo is not null (view attached)",
                "View.mID = R.id.indicator",
                "View.mWindowAttachCount = 1",
                "mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false"
            ),
            leakingStatus = LeakingStatus.NO,
            leakingStatusReason = "View attached",
            retainedByteSize = null,
            retainedObjectCount = null,
            referencingField = ReferencingField(
                type = ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                className = "Indicator",
                referenceName = "viewPager",
                isLikelyCause = true
            )
        )

        val outputInstance = Node.fromString(inputString)
        assertEquals(expected, outputInstance)
    }

    @Test
    fun `test parse array entry reference`() {
        val inputString = """
            ├─ android.view.View[] array
            │    Leaking: NO (Indicator↓ is not leaking)
            │    ↓ View[0]
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.ARRAY,
            className = "android.view.View[]",
            notes = emptyList(),
            leakingStatus = LeakingStatus.NO,
            leakingStatusReason = "Indicator↓ is not leaking",
            retainedByteSize = null,
            retainedObjectCount = null,
            referencingField = ReferencingField(
                type = ReferencingField.ReferencingFieldType.ARRAY_ENTRY,
                className = "View",
                referenceName = "0",
                isLikelyCause = false
            )
        )

        val outputInstance = Node.fromString(inputString)
        assertEquals(expected, outputInstance)
    }

    @Test
    fun `test parse java local reference`() {
        val inputString = """
            ├─ com.example.LeakingActivity instance
            │    Leaking: YES (Activity)
            │    ↓ java.util.HashMap<String, MyObject>.values().iterator()  <Java Local>
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "com.example.LeakingActivity",
            notes = emptyList(),
            leakingStatus = LeakingStatus.YES,
            leakingStatusReason = "Activity",
            retainedByteSize = null,
            retainedObjectCount = null,
            referencingField = ReferencingField(
                type = ReferencingField.ReferencingFieldType.LOCAL,
                className = "java.util.HashMap<String, MyObject>.values().iterator()",
                referenceName = "<Java Local>",
                isLikelyCause = false
            )
        )

        val outputInstance = Node.fromString(inputString)
        assertEquals(expected, outputInstance)
    }

    @Test
    fun `test parse valid leak trace object from string`() {
        val inputString = """
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = fc9ece2c-b4e6-489a-b0a9-c36942c65878
            ​     watchDurationMillis = 87899
            ​     retainedDurationMillis = 82899
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "androidx.constraintlayout.widget.ConstraintLayout",
            notes = listOf(
                "key = fc9ece2c-b4e6-489a-b0a9-c36942c65878",
                "watchDurationMillis = 87899",
                "retainedDurationMillis = 82899",
                "View not part of a window view hierarchy",
                "View.mAttachInfo is null (view detached)",
                "View.mWindowAttachCount = 1",
                "mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false"
            ),
            leakingStatus = LeakingStatus.YES,
            leakingStatusReason = "ObjectWatcher was watching this because " +
                    "com.amaze.filemanager.ui.fragments.TabFragment received " +
                    "Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks)",
            retainedByteSize = 2200,
            retainedObjectCount = 43,
            referencingField = null
        )

        val actual = Node.fromString(inputString)
        assertEquals(expected, actual)
        val toStringOfActual = actual.toString(
            firstLinePrefix = "╰→ ",
            additionalLinesPrefix = "$ZERO_WIDTH_SPACE     ",
            showLeakingStatus = true
        )
        assertEquals(inputString, toStringOfActual)
        val convertBackFromString = Node.fromString(toStringOfActual)
        assertEquals(actual, convertBackFromString)
    }

    @Test
    fun `test leak trace object to string`() {
        val input = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "androidx.constraintlayout.widget.ConstraintLayout",
            notes = listOf(
                "key = fc9ece2c-b4e6-489a-b0a9-c36942c65878",
                "watchDurationMillis = 87899",
                "retainedDurationMillis = 82899",
                "View not part of a window view hierarchy",
                "View.mAttachInfo is null (view detached)",
                "View.mWindowAttachCount = 1",
                "mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false"
            ),
            leakingStatus = LeakingStatus.YES,
            leakingStatusReason = "ObjectWatcher was watching this because " +
                    "com.amaze.filemanager.ui.fragments.TabFragment received " +
                    "Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks)",
            retainedByteSize = 2200,
            retainedObjectCount = 43,
            referencingField = null
        )

        val expected = """
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = fc9ece2c-b4e6-489a-b0a9-c36942c65878
            ​     watchDurationMillis = 87899
            ​     retainedDurationMillis = 82899
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        """.trimIndent()

        val actual = input.toString(
            firstLinePrefix = "╰→ ",
            additionalLinesPrefix = "$ZERO_WIDTH_SPACE     ",
            showLeakingStatus = true
        )

        assertEquals(expected, actual)
        val convertBack = Node.fromString(expected)
        assertEquals(input, convertBack)
    }

    @Test
    fun `test parse leak trace object with no labels`() {
        val inputString = """
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "androidx.constraintlayout.widget.ConstraintLayout",
            notes = listOf(),
            leakingStatus = LeakingStatus.YES,
            leakingStatusReason = "ObjectWatcher was watching this because " +
                    "com.amaze.filemanager.ui.fragments.TabFragment received " +
                    "Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks)",
            retainedByteSize = 2200,
            retainedObjectCount = 43,
            referencingField = null
        )

        val actual = Node.fromString(inputString)

        assertEquals(expected, actual)
    }

    @Test
    fun `test parse leak trace leaking status multiple line`() {
        val inputString = """
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received
            ​     Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
        """.trimIndent()

        val expected = Node(
            nodeType = LeakTraceNodeType.INSTANCE,
            className = "androidx.constraintlayout.widget.ConstraintLayout",
            notes = listOf(),
            leakingStatus = LeakingStatus.YES,
            leakingStatusReason = "ObjectWatcher was watching this because " +
                    "com.amaze.filemanager.ui.fragments.TabFragment received " +
                    "Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks)",
            retainedByteSize = 2200,
            retainedObjectCount = 43,
            referencingField = null
        )

        val actual = Node.fromString(inputString)

        assertEquals(expected, actual)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse invalid input throws exception`() {
        val invalidInput = "This is not a valid leak trace object string"
        Node.fromString(invalidInput)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse with missing required information`() {
        val missingClassInput = """
            ​     Leaking: YES (ObjectWatcher was watching this because com.amaze.filemanager.ui.fragments.TabFragment received Fragment#onDestroyView() callback (references to its views should be cleared to prevent leaks))
            ​     Retaining 2.2 kB in 43 objects
            ​     key = fc9ece2c-b4e6-489a-b0a9-c36942c65878
            ​     watchDurationMillis = 87899
            ​     retainedDurationMillis = 82899
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        """.trimIndent()

        Node.fromString(missingClassInput)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse with missing leak status input`() {
        val missingLeakingStatusInput = """
            ╰→ androidx.constraintlayout.widget.ConstraintLayout instance
            ​     Retaining 2.2 kB in 43 objects
            ​     key = fc9ece2c-b4e6-489a-b0a9-c36942c65878
            ​     watchDurationMillis = 87899
            ​     retainedDurationMillis = 82899
            ​     View not part of a window view hierarchy
            ​     View.mAttachInfo is null (view detached)
            ​     View.mWindowAttachCount = 1
            ​     mContext instance of com.amaze.filemanager.ui.activities.MainActivity with mDestroyed = false
        """.trimIndent()

        Node.fromString(missingLeakingStatusInput)
    }
}
