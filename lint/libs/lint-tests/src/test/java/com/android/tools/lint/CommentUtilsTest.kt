package com.android.tools.lint

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_XML
import org.junit.Assert.*
import org.junit.Test

class CommentUtilsTest {
  @Test
  fun testJavaComments() {
    assertEquals(
      DOT_JAVA,
      """
      import androidx.recyclerview.widget.RecyclerView
      public class MyClass { String s = "/* This comment is \"in\" a string */" }
      """
        .trimIndent()
        .trim(),
      stripComments(
          """
        /** Comment */
        import androidx.recyclerview.widget.RecyclerView // unnecessary
          // Line comment
        public class MyClass { String s = "/* This comment is \"in\" a string */" }""",
          DOT_JAVA,
        )
        .trimIndent()
        .trim(),
    )
  }

  @Test
  fun testJavaCommentsNotLineComments() {
    assertEquals(
      """
      import androidx.recyclerview.widget.RecyclerView; // unnecessary
        // Line comment
      public class MyClass { String s = "/* This comment is \"in\" a string */"; }
      """
        .trimIndent()
        .trim(),
      stripComments(
          // language=Java
          """
          /** Comment */
          import androidx.recyclerview.widget.RecyclerView; // unnecessary
            // Line comment
          public class MyClass { String s = "/* This comment is \"in\" a string */"; }
          """,
          DOT_JAVA,
          stripLineComments = false,
        )
        .trimIndent()
        .trim(),
    )
  }

  @Test
  fun testKotlinComments() {
    // includes nested comments
    assertEquals(
      """
      fun test1() { }

      fun test2() { }
      """
        .trimIndent()
        .trim(),
      stripComments(
          // language=Kt
          """
                // Line comment /*
                /**/ /***/ fun test1() { }
                /* /* */ fun wrong() { } */
                fun test2() { }
                """,
          DOT_KT,
        )
        .trimIndent()
        .trim(),
    )
  }

  @Test
  fun testXmlComments() {
    // includes nested comments
    assertEquals(
      // language=XML
      """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.android.example.appwithdatabinding" >

          <application
              android:allowBackup="true"
              android:icon="@drawable/ic_launcher"
              android:label="@string/app_name"
              android:theme="@style/AppTheme" >
          </application>

      </manifest>
      """
        .trimIndent()
        .trim(),
      stripComments(
          // language=XML
          """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              package="com.android.example.appwithdatabinding" >

              <!-- comment -->
              <application
                  android:allowBackup="true"
                  android:icon="@drawable/ic_launcher"
                  android:label="@string/app_name"
                  android:theme="@style/AppTheme" >
              </application>

          </manifest>
          """,
          DOT_XML,
        )
        .trimIndent()
        .trim(),
    )
  }
}
