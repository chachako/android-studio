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

class AccessibilityForceFocusDetectorTest : AbstractCheckTest() {
  override fun getDetector() = AccessibilityForceFocusDetector()

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
            performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:12: Warning: Do not force accessibility focus, as this interferes with screen readers and gives an inconsistent user experience, especially across apps [AccessibilityFocus]
            performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
  }

  fun testFocusAction() {
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo

        class MyView(context: Context) : View(context)

        fun foo(view: MyView) {
          view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
          view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS.id, null)
          // No warning.
          view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:10: Warning: Do not force accessibility focus, as this interferes with screen readers and gives an inconsistent user experience, especially across apps [AccessibilityFocus]
          view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/com/my/app/MyView.kt:11: Warning: Do not force accessibility focus, as this interferes with screen readers and gives an inconsistent user experience, especially across apps [AccessibilityFocus]
          view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS.id, null)
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }
}
