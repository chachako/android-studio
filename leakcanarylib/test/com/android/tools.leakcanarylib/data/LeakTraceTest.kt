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

import junit.framework.TestCase.assertEquals
import org.junit.Test

class LeakTraceTest {
    @Test
    fun `parseLeakTrace - basic leak trace`() {
        val leakTraceText = """
        ┬───
        │ GC Root: Input or output parameters in native code
        │
        ├─ com.example.Singleton class
        │    Leaking: YES (ObjectWatcher was watching this because its lifecycle has ended)
        │    Retaining 12.8 kB in 100 objects
        │    ↓ static Singleton.leakedActivity
        ╰→ com.example.LeakingActivity instance
        ​     Leaking: YES (Activity has leaked)
        ​     Retaining 50 B in 1 objects
    """.trimIndent()

        val expected = LeakTrace(
            gcRootType = GcRootType.NATIVE_STACK,
            nodes = listOf(
                Node(
                    nodeType = LeakTraceNodeType.CLASS,
                    className = "com.example.Singleton",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.YES,
                    leakingStatusReason =
                            "ObjectWatcher was watching this because its lifecycle has ended",
                    retainedByteSize = 12800,
                    retainedObjectCount = 100,
                    referencingField = ReferencingField(
                        className = "Singleton",
                        referenceName = "leakedActivity",
                        type = ReferencingField.ReferencingFieldType.STATIC_FIELD,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.LeakingActivity",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.YES,
                    leakingStatusReason = "Activity has leaked",
                    retainedByteSize = 50,
                    retainedObjectCount = 1,
                    referencingField = null
                )
            )
        )

        val actual = LeakTrace.fromString(leakTraceText)
        assertEquals(expected.toString(), actual.toString())

        val actualToString = actual.toString()
        assertEquals(leakTraceText, actualToString)

        // convert back and check
        val convertedBack = LeakTrace.fromString(actualToString)
        assertEquals(actual, convertedBack)
    }

    @Test
    fun `parseLeakTrace - leak trace`() {
        val leakTraceText = """
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

        val actual = LeakTrace.fromString(leakTraceText)

        val actualToString = actual.toString()
        assertEquals(leakTraceText, actualToString)

        //convertBack and check
        val convertedBack = LeakTrace.fromString(actualToString)
        assertEquals(actual, convertedBack)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseLeakTrace - invalid GC root type`() {
        val leakTraceText = """
            ┬───
            │ GC Root: InvalidType
            │
            ╰→ com.example.LeakingActivity INSTANCE
                 Leaking: YES (Activity has leaked)
        """.trimIndent()

        LeakTrace.fromString(leakTraceText)
    }

    @Test
    fun `parseLeakTrace - empty reference path`() {
        val leakTraceText = """
            ┬───
            │ GC Root: Input or output parameters in native code
            │
            ╰→ com.example.LeakingActivity INSTANCE
                 Leaking: YES (Activity has leaked)
        """.trimIndent()

        val expected = LeakTrace(
            gcRootType = GcRootType.NATIVE_STACK,
            nodes = listOf( Node(
                nodeType = LeakTraceNodeType.INSTANCE,
                className ="com.example.LeakingActivity",
                notes = listOf(),
                leakingStatus = LeakingStatus.YES,
                leakingStatusReason = "Activity has leaked",
                retainedByteSize = null,
                retainedObjectCount = null,
                referencingField = null
            ))
        )
        val actual = LeakTrace.fromString(leakTraceText)
        assertEquals(expected, actual)
    }

    @Test
    fun `parseLeakTrace - multiple references`() {
        val leakTraceText = """
        ┬───
        │ GC Root: Thread object
        │
        ├─ java.lang.Thread instance
        │    Leaking: NO (Thread is running)
        │    ↓ Thread.threadLocals
        ├─ java.lang.ThreadLocal instance
        │    Leaking: NO (MyObject↓ is not leaking)
        │    ↓ ThreadLocal.table
        ├─ java.lang.Object[] array
        │    Leaking: NO (MyObject↓ is not leaking)
        │    ↓ Object[1]
        ╰→ com.example.MyObject instance
             Leaking: YES (Example of custom leaking status)
             Retaining 5.4 kB in 80 objects
    """.trimIndent()

        val expected = LeakTrace(
            gcRootType = GcRootType.THREAD_OBJECT,
            nodes = listOf(
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "java.lang.Thread",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.NO,
                    leakingStatusReason = "Thread is running",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = ReferencingField(
                        className = "java.lang.Thread",
                        referenceName = "threadLocals",
                        type = ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "java.lang.ThreadLocal",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.NO,
                    leakingStatusReason = "MyObject↓ is not leaking",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = ReferencingField(
                        className = "java.lang.ThreadLocal",
                        referenceName = "table",
                        type = ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.ARRAY,
                    className = "java.lang.Object[]",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.NO,
                    leakingStatusReason = "MyObject↓ is not leaking",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = ReferencingField(
                        className = "java.lang.Object[]",
                        referenceName = "1",
                        type = ReferencingField.ReferencingFieldType.ARRAY_ENTRY,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.MyObject",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.YES,
                    leakingStatusReason = "Example of custom leaking status",
                    retainedByteSize = 5400,
                    retainedObjectCount = 80,
                    referencingField = null
                ),
        ))

        val actual = LeakTrace.fromString(leakTraceText)
        assertEquals(expected.toString(), actual.toString())
    }

    @Test
    fun `parseLeakTrace - no retained heap information`() {
        val leakTraceText = """
        ┬───
        │ GC Root: Java local variable
        │
        ├─ com.example.MyActivity instance
        │    Leaking: NO (Activity#mDestroyed is false)
        │    ↓ MyActivity.listener
        ╰→ com.example.MyListener instance
             Leaking: YES (Listener should be null)
    """.trimIndent()

        val expected = LeakTrace(
            gcRootType = GcRootType.JAVA_FRAME,
            nodes = listOf(
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.MyActivity",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.NO,
                    leakingStatusReason = "Activity#mDestroyed is false",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = ReferencingField(
                        className = "com.example.MyActivity",
                        referenceName = "listener",
                        type = ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.MyListener",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.YES,
                    leakingStatusReason = "Listener should be null",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = null
                ),
            )
        )

        val actual = LeakTrace.fromString(leakTraceText)
        assertEquals(expected.toString(), actual.toString())
    }

    @Test
    fun `parseLeakTrace - array reference`() {
        val leakTraceText = """
        ┬───
        │ GC Root: Java local variable
        │
        ├─ com.example.MyClass instance
        │    Leaking: NO (MyClass is not leaking)
        │    ↓ MyClass.myArray
        ├─ java.lang.Object[] array
        │    Leaking: NO (MyClass is not leaking)
        │    ↓ Object[42]
        ╰→ com.example.LeakedObject instance
             Leaking: YES (LeakedObject is leaking)
             Retaining 2.5 kB in 30 objects
    """.trimIndent()

        val expected = LeakTrace(
            gcRootType = GcRootType.JAVA_FRAME,
            nodes = listOf(
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.MyClass",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.NO,
                    leakingStatusReason = "MyClass is not leaking",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = ReferencingField(
                        className = "com.example.MyClass",
                        referenceName = "myArray",
                        type = ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.ARRAY,
                    className = "java.lang.Object[]",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.NO,
                    leakingStatusReason = "MyClass is not leaking",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = ReferencingField(
                        className = "java.lang.Object[]",
                        referenceName = "42",
                        type = ReferencingField.ReferencingFieldType.ARRAY_ENTRY,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.LeakedObject",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.YES,
                    leakingStatusReason = "LeakedObject is leaking",
                    retainedByteSize = 2500,
                    retainedObjectCount = 30,
                    referencingField = null
                )
        ))

        val actual = LeakTrace.fromString(leakTraceText)
        assertEquals(expected.toString(), actual.toString()) // Compare toString() outputs

        // convert back and check
        val convertedBack = LeakTrace.fromString(actual.toString())
        assertEquals(actual, convertedBack)
    }

    @Test
    fun `parseLeakTrace - local reference`() {
        val leakTraceText = """
        ┬───
        │ GC Root: Java local variable
        │
        ├─ com.example.MyClass instance
        │    Leaking: NO (MyClass is not leaking)
        │    ↓MyClass <Java Local>
        ╰→ com.example.LeakedObject instance
             Leaking: YES (LeakedObject is leaking)
    """.trimIndent()

        val expected = LeakTrace(
            gcRootType = GcRootType.JAVA_FRAME,
            nodes = listOf(
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.MyClass",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.NO,
                    leakingStatusReason = "MyClass is not leaking",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = ReferencingField(
                        className = "com.example.MyClass",
                        referenceName = "<Java Local>",
                        type = ReferencingField.ReferencingFieldType.LOCAL,
                        isLikelyCause = false
                    )
                ),
                Node(
                    nodeType = LeakTraceNodeType.INSTANCE,
                    className = "com.example.LeakedObject",
                    notes = listOf(),
                    leakingStatus = LeakingStatus.YES,
                    leakingStatusReason = "LeakedObject is leaking",
                    retainedByteSize = null,
                    retainedObjectCount = null,
                    referencingField = null
                )
        ))

        val actual = LeakTrace.fromString(leakTraceText)
        assertEquals(expected.toString(), actual.toString())
    }

    @Test
    fun `parseLeakTraces - single leak trace with signature line`() {
        val leakTraceText = """
            Signature: abcdef1234567890
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

        val actual = LeakTrace.fromString(leakTraceText)

        // Input starts with signature. But toString() will not contain it.
        // Hence, remove the signature line from the input string leakTrace to compare with the actual output
        val inputWithoutSignature = leakTraceText.lines()
            .subList(1, leakTraceText.lines().size).joinToString("\n")

        assertEquals(inputWithoutSignature, actual.toString())
    }

    @Test
    fun `parseLeakTraces - multiple leak traces with same signature`() {
        val leakTraceText = """
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

        val actual = LeakTrace.fromString(leakTraceText)

        // Input starts with signature, bytes and displaying information. But, toString() will have only leak trace.
        // Hence, remove the signature line from the input leakTrace string to compare with the actual output.
        val inputWithoutSignature = leakTraceText.lines()
            .subList(3, leakTraceText.lines().size).joinToString("\n")
        assertEquals(inputWithoutSignature, actual.toString())
    }
}


