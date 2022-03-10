/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class ActivityIconColorDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return ActivityIconColorDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        manifest().minSdk(14),
        image("res/drawable-ldpi/ic_walk.png", 48, 48).fill(10, 10, 20, 20, -0xff0001),
        image("res/drawable-ldpi/animated_walk.png", 48, 48).fill(10, 10, 20, 20, -0xff0001),
        ONGOING_ACTIVITY_STUB,
        kotlin(
            "src/test/pkg/ForegroundOnlyWalkingWorkoutService.kt",
            """
                    package test.pkg;

                    import androidx.wear.ongoing.OngoingActivity

                    class ForegroundOnlyWalkingWorkoutService {
                        private fun generateNotification(mainText: String) {
                            val ongoingActivity =
                                OngoingActivity.Builder()
                                    .setAnimatedIcon(R.drawable.animated_walk)
                                    .setStaticIcon(R.drawable.ic_walk)
                                    .build()
                        }
                    }
                    """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR
      )
      .run()
      .expect(
        """
            src/test/pkg/ForegroundOnlyWalkingWorkoutService.kt:9: Warning: The animated icon for an ongoing activity should be white with a transparent background [ActivityIconColor]
                            .setAnimatedIcon(R.drawable.animated_walk)
                                             ~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/ForegroundOnlyWalkingWorkoutService.kt:10: Warning: The static icon for an ongoing activity should be white with a transparent background [ActivityIconColor]
                            .setStaticIcon(R.drawable.ic_walk)
                                           ~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
      )
  }

  fun testBinaryIcons() {
    lint()
      .files(
        manifest().minSdk(14),
        base64gzip("res/drawable-ldpi/static_icon.png", COLORED_ICON_DATA),
        base64gzip("res/drawable-ldpi/ic_walk.png", TRANSPARENT_ICON_DATA),
        ONGOING_ACTIVITY_STUB,
        kotlin(
            "src/test/pkg/ForegroundOnlyWalkingWorkoutService.kt",
            """
                    package test.pkg;

                    import androidx.wear.ongoing.OngoingActivity

                    class ForegroundOnlyWalkingWorkoutService {
                        private fun generateNotification(mainText: String) {
                            val ongoingActivity =
                                OngoingActivity.Builder()
                                    .setStaticIcon(R.drawable.static_icon)
                                    .setStaticIcon(R.drawable.animated_walk)
                                    .build()
                        }
                    }
                    """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """
            src/test/pkg/ForegroundOnlyWalkingWorkoutService.kt:9: Warning: The static icon for an ongoing activity should be white with a transparent background [ActivityIconColor]
                            .setStaticIcon(R.drawable.static_icon)
                                           ~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testAnimatedXmlIcon() {
    lint()
      .files(
        manifest().minSdk(14),
        xml("res/drawable/animated_walk.xml", ANIMATED_WALK_ICON),
        xml("res/drawable/ic_walk.xml", STATIC_WALK_ICON),
        ONGOING_ACTIVITY_STUB,
        kotlin(
            "src/test/pkg/ForegroundOnlyWalkingWorkoutService.kt",
            """
                    package test.pkg;

                    import androidx.wear.ongoing.OngoingActivity

                    class ForegroundOnlyWalkingWorkoutService {
                        private fun generateNotification(mainText: String) {
                            val ongoingActivity =
                                OngoingActivity.Builder()
                                    .setAnimatedIcon(R.drawable.animated_walk)
                                    .setStaticIcon(R.drawable.ic_walk)
                                    .build()
                        }
                    }
                    """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """
            res/drawable/animated_walk.xml:47: Warning: The animated icon for an ongoing activity should be white with a transparent background [ActivityIconColor]
                                    android:strokeColor="#fffffA"
                                                         ~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  val ONGOING_ACTIVITY_STUB =
    java(
      "src/androidx/wear/ongoing/OngoingActivity.java",
      """
    package androidx.wear.ongoing;

    import android.graphics.drawable.Icon;

    import androidx.annotation.DrawableRes;
    import androidx.annotation.NonNull;
    import androidx.annotation.Nullable;

    public final class OngoingActivity {

        public OngoingActivity() {
        }

        public static final class Builder {

            public Builder() {
            }

            @NonNull
            public Builder setAnimatedIcon(@DrawableRes int animatedIcon) {
                return this;
            }

            @NonNull
            public Builder setStaticIcon(@DrawableRes int staticIcon) {
                return this;
            }

            public OngoingActivity build() {
                return new OngoingActivity();
            }
        }

    }
    """
        .trimIndent()
    )

  private val COLORED_ICON_DATA =
    "" +
      "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"

  private val TRANSPARENT_ICON_DATA =
    "" +
      "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"

  val ANIMATED_WALK_ICON =
    """
        <animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt">
            <aapt:attr name="android:drawable">
                <vector
                    android:width="240dp"
                    android:height="240dp"
                    android:viewportWidth="240"
                    android:viewportHeight="240">
                    <group android:name="_R_G">
                        <group android:name="_R_G_L_6_G">
                            <path
                                android:name="_R_G_L_6_G_S"
                                android:fillColor="#000000"
                                android:pathData="M0,0 L240,0 L240,240 L0,240z" />
                        </group>
                        <group
                            android:name="_R_G_L_5_G"
                            android:translateX="129.5"
                            android:translateY="43">
                            <path
                                android:name="_R_G_L_5_G_D_0_P_0"
                                android:fillAlpha="1"
                                android:fillColor="#ffffff"
                                android:fillType="nonZero"
                                android:pathData=" M0 -18 C9.93,-18 18,-9.93 18,0 C18,9.93 9.93,18 0,18 C-9.93,18 -18,9.93 -18,0 C-18,-9.93 -9.93,-18 0,-18c " />
                        </group>
                        <group
                            android:name="_R_G_L_4_G"
                            android:translateX="120"
                            android:translateY="120">
                            <path
                                android:name="_R_G_L_4_G_D_0_P_0"
                                android:fillAlpha="1"
                                android:fillColor="#ffffff"
                                android:fillType="nonZero"
                                android:pathData=" M7.75 -52.25 C7.75,-52.25 -6.75,-56 -9,-44.75 C-11.25,-33.5 -14.75,-17.25 -15.25,-3.75 C-15.75,9.75 -20.5,32.75 -6.25,32.75 C8,32.75 10,25.5 13.25,5 C16.5,-15.5 17.5,-31.75 17.5,-38.5 C17.5,-52.25 7.75,-52.25 7.75,-52.25c " />
                        </group>
                        <group
                            android:name="_R_G_L_3_G"
                            android:translateX="120"
                            android:translateY="120">
                            <path
                                android:name="_R_G_L_3_G_D_0_P_0"
                                android:pathData=" M1.5 -43.5 C1.5,-43.5 -38,-30.5 -38,-30.5 C-38,-30.5 -35.75,-0.75 -35.75,-0.75 "
                                android:strokeWidth="17"
                                android:strokeAlpha="1"
                                android:strokeColor="#fffffA"
                                android:strokeLineCap="round"
                                android:strokeLineJoin="round" />
                        </group>
                        <group
                            android:name="_R_G_L_2_G"
                            android:translateX="120"
                            android:translateY="120">
                            <path
                                android:name="_R_G_L_2_G_D_0_P_0"
                                android:pathData=" M9 -41.75 C9,-41.75 31,-6.5 31,-6.5 C31,-6.5 50.5,-21.5 50.5,-21.5 "
                                android:strokeWidth="17"
                                android:strokeAlpha="1"
                                android:strokeColor="#ffffff"
                                android:strokeLineCap="round"
                                android:strokeLineJoin="round" />
                        </group>
                        <group
                            android:name="_R_G_L_1_G"
                            android:translateX="120"
                            android:translateY="120">
                            <path
                                android:name="_R_G_L_1_G_D_0_P_0"
                                android:pathData=" M-5.25 18.5 C-5.25,18.5 -29.75,40.75 -29.75,40.75 C-29.75,40.75 -65.5,34.25 -65.5,34.25 "
                                android:strokeWidth="18"
                                android:strokeAlpha="1"
                                android:strokeColor="#ffffff"
                                android:strokeLineCap="round"
                                android:strokeLineJoin="round" />
                        </group>
                        <group
                            android:name="_R_G_L_0_G"
                            android:translateX="120"
                            android:translateY="120">
                            <path
                                android:name="_R_G_L_0_G_D_0_P_0"
                                android:pathData=" M-1.75 21.5 C-1.75,21.5 17.5,55 17.5,55 C17.5,55 38.5,94.5 38.5,94.5 "
                                android:strokeWidth="18"
                                android:strokeAlpha="1"
                                android:strokeColor="#ffffff"
                                android:strokeLineCap="round"
                                android:strokeLineJoin="round" />
                        </group>
                    </group>
                    <group android:name="time_group" />
                </vector>
            </aapt:attr>
            <target android:name="_R_G_L_5_G">
                <aapt:attr name="android:animation">
                    <set android:ordering="together">
                        <objectAnimator
                            android:duration="83"
                            android:pathData="M 129.5,43C 129.667,42.833 129.75,43.583 130.5,42"
                            android:propertyName="translateXY"
                            android:propertyXName="translateX"
                            android:propertyYName="translateY"
                            android:startOffset="0">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                        <objectAnimator
                            android:duration="83"
                            android:pathData="M 133.25,33C 132.25,34.458 130.125,41.333 129.5,43"
                            android:propertyName="translateXY"
                            android:propertyXName="translateX"
                            android:propertyYName="translateY"
                            android:startOffset="583">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                    </set>
                </aapt:attr>
            </target>
            <target android:name="_R_G_L_4_G_D_0_P_0">
                <aapt:attr name="android:animation">
                    <set android:ordering="together">
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="0"
                            android:valueFrom="M7.75 -52.25 C7.75,-52.25 -6.75,-56 -9,-44.75 C-11.25,-33.5 -14.75,-17.25 -15.25,-3.75 C-15.75,9.75 -20.5,32.75 -6.25,32.75 C8,32.75 10,25.5 13.25,5 C16.5,-15.5 17.5,-31.75 17.5,-38.5 C17.5,-52.25 7.75,-52.25 7.75,-52.25c "
                            android:valueTo="M7.75 -52.25 C7.75,-52.25 -6.75,-56 -9,-44.75 C-11.25,-33.5 -14.75,-17.25 -15.25,-3.75 C-15.75,9.75 -20.5,32.75 -6.25,32.75 C8,32.75 9.65,25.84 12.25,5.25 C15,-16.5 15.25,-31.75 15.25,-38.5 C15.25,-52.25 7.75,-52.25 7.75,-52.25c "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="583"
                            android:valueFrom="M10.5 -63 C10.5,-63 -5.25,-66.75 -8.25,-46.75 C-9.95,-35.4 -13.56,-15.59 -14,-0.5 C-14.5,16.5 -10.5,22 -4,23.5 C8.76,26.45 12,13.25 14.5,2.75 C16.92,-7.41 18.52,-26.02 19.25,-34 C22,-64.25 10.5,-63 10.5,-63c "
                            android:valueTo="M7.75 -52.25 C7.75,-52.25 -6.75,-56 -9,-44.75 C-11.25,-33.5 -14.75,-17.25 -15.25,-3.75 C-15.75,9.75 -20.5,32.75 -6.25,32.75 C8,32.75 10,25.5 13.25,5 C16.5,-15.5 17.5,-31.75 17.5,-38.5 C17.5,-52.25 7.75,-52.25 7.75,-52.25c "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                    </set>
                </aapt:attr>
            </target>
            <target android:name="_R_G_L_3_G_D_0_P_0">
                <aapt:attr name="android:animation">
                    <set android:ordering="together">
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="0"
                            android:valueFrom="M1.5 -43.5 C1.5,-43.5 -38,-30.5 -38,-30.5 C-38,-30.5 -35.75,-0.75 -35.75,-0.75 "
                            android:valueTo="M1.5 -43.5 C1.5,-43.5 -14.1,-18.09 -15.5,-3.75 C-16.58,7.29 -5.5,28 -5.5,28 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="583"
                            android:valueFrom="M7.75 -53.25 C7.75,-53.25 -28.75,-39.25 -32.25,-32 C-34.69,-26.94 -33,-5.75 -33,-5.75 "
                            android:valueTo="M1.5 -43.5 C1.5,-43.5 -38,-30.5 -38,-30.5 C-38,-30.5 -35.75,-0.75 -35.75,-0.75 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                    </set>
                </aapt:attr>
            </target>
            <target android:name="_R_G_L_2_G_D_0_P_0">
                <aapt:attr name="android:animation">
                    <set android:ordering="together">
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="0"
                            android:valueFrom="M9 -41.75 C9,-41.75 31,-6.5 31,-6.5 C31,-6.5 50.5,-21.5 50.5,-21.5 "
                            android:valueTo="M6.25 -41.75 C6.25,-41.75 3.25,-13.75 5.25,-2.5 C7.15,8.18 26.75,20.5 26.75,20.5 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="583"
                            android:valueFrom="M9.25 -51.25 C9.25,-51.25 21.75,-27.75 30.5,-16.5 C32.98,-13.32 52.5,-23.5 52.5,-23.5 "
                            android:valueTo="M9 -41.75 C9,-41.75 31,-6.5 31,-6.5 C31,-6.5 50.5,-21.5 50.5,-21.5 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                    </set>
                </aapt:attr>
            </target>
            <target android:name="_R_G_L_1_G_D_0_P_0">
                <aapt:attr name="android:animation">
                    <set android:ordering="together">
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="0"
                            android:valueFrom="M-5.25 18.5 C-5.25,18.5 -29.75,40.75 -29.75,40.75 C-29.75,40.75 -65.5,34.25 -65.5,34.25 "
                            android:valueTo="M-5.25 18.5 C-5.25,18.5 -14.5,49.5 -14.5,49.5 C-14.5,49.5 -49.25,49 -49.25,49 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="583"
                            android:valueFrom="M-3 10 C-3,10 -38.75,39.25 -38.75,39.25 C-38.75,39.25 -68.75,65.75 -68.75,65.75 "
                            android:valueTo="M-5.25 18.5 C-5.25,18.5 -29.75,40.75 -29.75,40.75 C-29.75,40.75 -65.5,34.25 -65.5,34.25 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                    </set>
                </aapt:attr>
            </target>
            <target android:name="_R_G_L_0_G_D_0_P_0">
                <aapt:attr name="android:animation">
                    <set android:ordering="together">
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="0"
                            android:valueFrom="M-1.75 21.5 C-1.75,21.5 17.5,55 17.5,55 C17.5,55 38.5,94.5 38.5,94.5 "
                            android:valueTo="M-1.75 21.5 C-1.75,21.5 1,59.5 1,59.5 C1,59.5 5.5,94.5 5.5,94.5 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                        <objectAnimator
                            android:duration="83"
                            android:propertyName="pathData"
                            android:startOffset="583"
                            android:valueFrom="M2.75 15.25 C2.75,15.25 21.5,36.75 21.5,36.75 C21.5,36.75 22.75,74 22.75,74 "
                            android:valueTo="M-1.75 21.5 C-1.75,21.5 17.5,55 17.5,55 C17.5,55 38.5,94.5 38.5,94.5 "
                            android:valueType="pathType">
                            <aapt:attr name="android:interpolator">
                                <pathInterpolator android:pathData="M 0.0,0.0 c0.4,0 0.999,1 1.0,1.0" />
                            </aapt:attr>
                        </objectAnimator>
                    </set>
                </aapt:attr>
            </target>
            <target android:name="time_group">
                <aapt:attr name="android:animation">
                    <set android:ordering="together">
                        <objectAnimator
                            android:duration="667"
                            android:propertyName="translateX"
                            android:startOffset="0"
                            android:valueFrom="0"
                            android:valueTo="1"
                            android:valueType="floatType" />
                    </set>
                </aapt:attr>
            </target>
        </animated-vector>
    """
      .trimIndent()

  val STATIC_WALK_ICON =
    """
        <vector
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:autoMirrored="true"
            android:height="24dp"
            android:tint="#FFFFFF"
            android:viewportHeight="24"
            android:viewportWidth="24"
            android:width="24dp">
                <path android:fillColor="@android:color/white" android:pathData="M13.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM9.8,8.9L7,23h2.1l1.8,-8 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3C14.8,12 16.8,13 19,13v-2c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1L6,8.3V13h2V9.6l1.8,-0.7"/>
        </vector>
    """
      .trimIndent()
}
