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

class AccessibilityViewScrollActionsDetectorTest : AbstractCheckTest() {
  override fun getDetector() = AccessibilityViewScrollActionsDetector()

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

          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }

          override fun getAccessibilityClassName(): CharSequence {
            return ScrollView::class.java.name
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:11: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        0 errors, 1 warnings
        """
      )
  }

  fun testMissingActionScrollWithGetAccessibilityClassNameOverrideKotlin() {
    // We include a few different ways of providing the getAccessibilityClassName override in
    // Kotlin.
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }

          override fun getAccessibilityClassName(): CharSequence {
            return ScrollView::class.java.name
          }
        }

        class MyView2(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }

          override fun getAccessibilityClassName(): CharSequence {
            return ScrollView::class.java.canonicalName!!
          }
        }

        class MyView3(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }

          override fun getAccessibilityClassName(): CharSequence {
            return "CustomScrollView"
          }
        }

        class MyView4(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }

          override fun getAccessibilityClassName(): CharSequence = ScrollView::class.java.name
        }

        class MyView5(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }

          override fun getAccessibilityClassName(): CharSequence = "CustomScrollView"
        }

        class MyView6(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }

          // No warning.
          override fun getAccessibilityClassName(): CharSequence = "MyView"
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:9: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        src/com/my/app/MyView.kt:21: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        src/com/my/app/MyView.kt:33: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        src/com/my/app/MyView.kt:45: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        src/com/my/app/MyView.kt:55: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        0 errors, 5 warnings
        """
      )
  }

  fun testMissingActionScrollWithGetAccessibilityClassNameOverrideJava() {
    // We include a few different ways of providing the getAccessibilityClassName override in Java.
    lint()
      .files(
        java(
            """
        package com.my.app;

        import android.content.Context;
        import android.view.View;
        import android.view.accessibility.AccessibilityNodeInfo;
        import android.widget.ScrollView;

        public class MyViewJava extends View {
            public MyViewJava(Context context) {
                super(context);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }

            @Override
            public CharSequence getAccessibilityClassName() {
                return "CustomScrollView";
            }
        }

        class MyViewJava2 extends View {
            public MyViewJava2(Context context) {
                super(context);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }

            @Override
            public CharSequence getAccessibilityClassName() {
                return ScrollView.class.getName();
            }
        }

        class MyViewJava3 extends View {
            public MyViewJava3(Context context) {
                super(context);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }

            @Override
            public CharSequence getAccessibilityClassName() {
                return ScrollView.class.getCanonicalName();
            }
        }

        class MyViewJava4 extends View {
            public MyViewJava3(Context context) {
                super(context);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }

            @Override
            public CharSequence getAccessibilityClassName() {
                // No warning.
                return "MyView";
            }
        }

        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyViewJava.java:13: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
            @Override
            ^
        src/com/my/app/MyViewJava.java:31: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
            @Override
            ^
        src/com/my/app/MyViewJava.java:49: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
            @Override
            ^
        0 errors, 3 warnings
        """
      )
  }

  fun testMissingActionScrollNoWarning() {
    // No warning because this View does not seem to be imitating a ScrollView.
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testMissingActionScrollWithCollectionInfo() {
    // The warning also triggers if the View has collection info.
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {

          var collectionInfo: AccessibilityNodeInfo.CollectionInfo? = null

          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.collectionInfo = collectionInfo
          }
        }

        class MyView2(context: Context) : View(context) {

          var collectionInfo: AccessibilityNodeInfo.CollectionInfo? = null

          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            // No warning if set to null.
            info.collectionInfo = null
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:12: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        0 errors, 1 warnings
        """
      )
  }

  fun testMissingActionScrollWithAccessibilityClassNameInfo() {
    // The warning also triggers if the "ScrollView" accessibility class name is provided in
    // onInitializeAccessibilityNodeInfo.
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.className = "ScrollView"
          }
        }

        class MyView2(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.className = ScrollView::class.java.name
          }
        }

        class MyView3(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            // No warning if set to null.
            info.className = null
          }
        }

        class MyView3(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            // No warning if the name does not contain "ScrollView".
            info.className = "MyView"
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:9: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        src/com/my/app/MyView.kt:18: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
          ^
        0 errors, 2 warnings
        """
      )
  }

  fun testActionScrollCorrect() {
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            // Even a single up/down/left/right is sufficient to avoid the warning, although up and
            // down or left and right is more likely.
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)

            info.className = "ScrollView"
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testMissingActionScrollViaOtherAddActionOverload() {
    // There is an overload of addAction that takes an int.
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {

          var collectionInfo: AccessibilityNodeInfo.CollectionInfo? = null

          @Suppress("DEPRECATION")
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            // Here, ACTION_SCROLL_{FORWARD,BACKWARD} is an int constant, not a field containing a
            // reference to an object. This overload of addAction is deprecated, but we still want
            // to report the warning.
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            info.collectionInfo = collectionInfo
          }
        }
        """
          )
          .indented()
      )
      .run()
      .expect(
        """
        src/com/my/app/MyView.kt:12: Warning: Views that behave like ScrollView and support ACTION_SCROLL_{FORWARD,BACKWARD} should also support ACTION_SCROLL_{LEFT,RIGHT} and/or ACTION_SCROLL_{UP,DOWN} [AccessibilityScrollActions]
          @Suppress("DEPRECATION")
          ^
        0 errors, 1 warnings
        """
      )
  }

  fun testMissingActionScrollEscaped() {
    // We report no warning if the info parameter escapes (excluding calls to the super method).
    lint()
      .files(
        kotlin(
            """
        package com.my.app

        import android.content.Context
        import android.view.View
        import android.view.accessibility.AccessibilityNodeInfo
        import android.widget.ScrollView

        class MyView(context: Context) : View(context) {
          override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            // No warning because info escapes.
            foo(info)
          }

          abstract fun foo(info: AccessibilityNodeInfo)

          override fun getAccessibilityClassName(): CharSequence = ScrollView::class.java.name

        }
        """
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
