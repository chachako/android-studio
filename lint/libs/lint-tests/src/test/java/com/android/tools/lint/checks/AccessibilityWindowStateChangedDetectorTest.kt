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
package com.android.tools.lint.checks

class AccessibilityWindowStateChangedDetectorTest : AbstractCheckTest() {
  override fun getDetector() = AccessibilityWindowStateChangedDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityEvent
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {

          fun foo() {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
          }

          override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
              event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
            }
            return super.dispatchPopulateAccessibilityEvent(event)
          }

          override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
              event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
            }
            super.onPopulateAccessibilityEvent(event)
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:12: Warning: Manually populating or sending TYPE_WINDOW_STATE_CHANGED events should be avoided. They may be ignored on certain versions of Android. Prefer setting UI metadata using View.onInitializeAccessibilityNodeInfo, Activity.setTitle, ViewCompat.setAccessibilityPaneTitle, etc. to inform users of crucial changes to the UI. [AccessibilityWindowStateChangedEvent]
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/my/app/MyView.kt:16: Warning: Manually populating or sending TYPE_WINDOW_STATE_CHANGED events should be avoided. They may be ignored on certain versions of Android. Prefer setting UI metadata using View.onInitializeAccessibilityNodeInfo, Activity.setTitle, ViewCompat.setAccessibilityPaneTitle, etc. to inform users of crucial changes to the UI. [AccessibilityWindowStateChangedEvent]
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/my/app/MyView.kt:23: Warning: Manually populating or sending TYPE_WINDOW_STATE_CHANGED events should be avoided. They may be ignored on certain versions of Android. Prefer setting UI metadata using View.onInitializeAccessibilityNodeInfo, Activity.setTitle, ViewCompat.setAccessibilityPaneTitle, etc. to inform users of crucial changes to the UI. [AccessibilityWindowStateChangedEvent]
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 3 warnings
        """
      )
  }

  fun testWindowStateChangedEvent() {
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityEvent

        class MyView(context: Context) : View(context)

        fun foo(view: MyView) {
          view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:10: Warning: Manually populating or sending TYPE_WINDOW_STATE_CHANGED events should be avoided. They may be ignored on certain versions of Android. Prefer setting UI metadata using View.onInitializeAccessibilityNodeInfo, Activity.setTitle, ViewCompat.setAccessibilityPaneTitle, etc. to inform users of crucial changes to the UI. [AccessibilityWindowStateChangedEvent]
          view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testWindowStateChangedReferenceInOverride() {
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityEvent

        class MyView(context: Context) : View(context) {
          override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
              event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
            }
            return super.dispatchPopulateAccessibilityEvent(event)
          }

          override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
              event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
            }
            super.onPopulateAccessibilityEvent(event)
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:9: Warning: Manually populating or sending TYPE_WINDOW_STATE_CHANGED events should be avoided. They may be ignored on certain versions of Android. Prefer setting UI metadata using View.onInitializeAccessibilityNodeInfo, Activity.setTitle, ViewCompat.setAccessibilityPaneTitle, etc. to inform users of crucial changes to the UI. [AccessibilityWindowStateChangedEvent]
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/my/app/MyView.kt:16: Warning: Manually populating or sending TYPE_WINDOW_STATE_CHANGED events should be avoided. They may be ignored on certain versions of Android. Prefer setting UI metadata using View.onInitializeAccessibilityNodeInfo, Activity.setTitle, ViewCompat.setAccessibilityPaneTitle, etc. to inform users of crucial changes to the UI. [AccessibilityWindowStateChangedEvent]
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                                                      ~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }
}
