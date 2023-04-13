/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.flags;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Assert;
import org.junit.Test;

public class FlagTest {

    @Test
    public void validateFlagValues() {
        assertThat(GameFeatures.GRAPHICS.getFlags()).isEqualTo(GameFeatures.FLAGS);
        assertThat(GameFeatures.GRAPHICS.getDisplayName()).isEqualTo("Graphics");
        assertThat(GameFeatures.AUDIO.getFlags()).isEqualTo(GameFeatures.FLAGS);
        assertThat(GameFeatures.AUDIO.getDisplayName()).isEqualTo("Audio");
        assertThat(GameFeatures.MEMORY.getFlags()).isEqualTo(GameFeatures.FLAGS);
        assertThat(GameFeatures.MEMORY.getDisplayName()).isEqualTo("Memory");

        assertThat(GameFeatures.USE_3D_AUDIO.get()).isTrue();
        assertThat(GameFeatures.USE_3D_AUDIO.getGroup()).isEqualTo(GameFeatures.AUDIO);
        assertThat(GameFeatures.USE_3D_AUDIO.getId()).isEqualTo("audio.3d");
        assertThat(GameFeatures.USE_3D_AUDIO.getDisplayName()).isEqualTo("Enable 3D audio");
        assertThat(GameFeatures.USE_3D_AUDIO.getDescription()).isEqualTo("<audio.3d description>");

        assertThat(GameFeatures.RESOLUTION.get()).isEqualTo("1280x720");
        assertThat(GameFeatures.RESOLUTION.getGroup()).isEqualTo(GameFeatures.GRAPHICS);
        assertThat(GameFeatures.RESOLUTION.getId()).isEqualTo("graphics.resolution");
        assertThat(GameFeatures.RESOLUTION.getDisplayName()).isEqualTo("Initial resolution");
        assertThat(GameFeatures.RESOLUTION.getDescription())
                .isEqualTo("<graphics.resolution description>");

        assertThat(GameFeatures.FPS_CAP.get()).isEqualTo(30);
        assertThat(GameFeatures.FPS_CAP.getGroup()).isEqualTo(GameFeatures.GRAPHICS);
        assertThat(GameFeatures.FPS_CAP.getId()).isEqualTo("graphics.fps.cap");
        assertThat(GameFeatures.FPS_CAP.getDisplayName()).isEqualTo("FPS cap");
        assertThat(GameFeatures.FPS_CAP.getDescription())
                .isEqualTo("<graphics.fps.cap description>");

        assertThat(GameFeatures.MAX_HEAP_SIZE.get()).isEqualTo(4000000000L);
        assertThat(GameFeatures.MAX_HEAP_SIZE.getGroup()).isEqualTo(GameFeatures.MEMORY);
        assertThat(GameFeatures.MAX_HEAP_SIZE.getId()).isEqualTo("memory.max.heap.size");
        assertThat(GameFeatures.MAX_HEAP_SIZE.getDisplayName()).isEqualTo("Max Heap Size in bytes");
        assertThat(GameFeatures.MAX_HEAP_SIZE.getDescription())
                .isEqualTo("<memory.max.heap.size description>");
    }

    @Test
    public void testFailureInputsThrowException() {
        Flags flags = new Flags();
        try {
            FlagGroup group = new FlagGroup(flags, "dummy", "");
            group.validate();
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        FlagGroup group = new FlagGroup(flags, "dummy", "Dummy");
        try {
            Flag<Boolean> flag = Flag.create(group, "", "Dummy", "Dummy Description", false);
            flag.validate();
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag<Boolean> flag = Flag.create(group, "dummy", "", "Dummy Description", false);
            flag.validate();
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag<Boolean> flag = Flag.create(group, "dummy", "Dummy", "", false);
            flag.validate();
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void verifyIdThrowsExceptionForInvalidCases() {
        Flag.verifyFlagIdFormat("valid");
        Flag.verifyFlagIdFormat("valid.id");
        Flag.verifyFlagIdFormat("v");
        Flag.verifyFlagIdFormat("valid.multi.part.id");
        Flag.verifyFlagIdFormat("a123.4a.numbers.ok.if.not.leading.5");

        try {
            Flag.verifyFlagIdFormat("");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyFlagIdFormat(".");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyFlagIdFormat("a.");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyFlagIdFormat(".a");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyFlagIdFormat("a..a");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyFlagIdFormat("1.a");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void verifyDisplayTextThrowsExceptionForInvalidCases() {
        Flag.verifyDisplayTextFormat("Valid");
        Flag.verifyDisplayTextFormat("Valid name");
        Flag.verifyDisplayTextFormat("V");
        Flag.verifyDisplayTextFormat("Numbers are ok: 123");

        try {
            Flag.verifyDisplayTextFormat("");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyDisplayTextFormat("      ");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyDisplayTextFormat("    A");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Flag.verifyDisplayTextFormat("A      ");
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void flagCanOverrideValue() {
        Flags flags = new Flags();
        FlagGroup group = new FlagGroup(flags, "dummy", "Dummy");
        Flag<Boolean> boolFlag = Flag.create(group, "bool", "Dummy", "Dummy", true);
        Flag<Integer> intFlag = Flag.create(group, "int", "Dummy", "Dummy", 123);
        Flag<Long> longFlag = Flag.create(group, "long", "Dummy", "Dummy", 30L);

        assertThat(boolFlag.isOverridden()).isFalse();
        assertThat(intFlag.isOverridden()).isFalse();
        assertThat(longFlag.isOverridden()).isFalse();

        boolFlag.override(false);
        intFlag.override(456);
        longFlag.override(60L);

        assertThat(flags.getOverrides().get(boolFlag)).isEqualTo("false");
        assertThat(flags.getOverrides().get(intFlag)).isEqualTo("456");
        assertThat(flags.getOverrides().get(longFlag)).isEqualTo("60");
        assertThat(boolFlag.get()).isFalse();
        assertThat(intFlag.get()).isEqualTo(456);
        assertThat(longFlag.get()).isEqualTo(60L);

        assertThat(boolFlag.isOverridden()).isTrue();
        assertThat(intFlag.isOverridden()).isTrue();
        assertThat(longFlag.isOverridden()).isTrue();

        boolFlag.clearOverride();
        intFlag.clearOverride();
        longFlag.clearOverride();

        assertThat(boolFlag.isOverridden()).isFalse();
        assertThat(intFlag.isOverridden()).isFalse();
        assertThat(longFlag.isOverridden()).isFalse();
        assertThat(boolFlag.get()).isTrue();
        assertThat(intFlag.get()).isEqualTo(123);
        assertThat(longFlag.get()).isEqualTo(30L);
    }

    private static final class GameFeatures {
        private static final Flags FLAGS = new Flags();

        private static final FlagGroup AUDIO = new FlagGroup(FLAGS, "audio", "Audio");
        public static final Flag<Boolean> USE_3D_AUDIO =
                Flag.create(AUDIO, "3d", "Enable 3D audio", "<audio.3d description>", true);

        private static final FlagGroup GRAPHICS = new FlagGroup(FLAGS, "graphics", "Graphics");
        public static final Flag<String> RESOLUTION =
                Flag.create(
                        GRAPHICS,
                        "resolution",
                        "Initial resolution",
                        "<graphics.resolution description>",
                        "1280x720");

        public static final Flag<Integer> FPS_CAP =
                Flag.create(GRAPHICS, "fps.cap", "FPS cap", "<graphics.fps.cap description>", 30);

        private static final FlagGroup MEMORY = new FlagGroup(FLAGS, "memory", "Memory");
        public static final Flag<Long> MAX_HEAP_SIZE =
                Flag.create(
                        MEMORY,
                        "max.heap.size",
                        "Max Heap Size in bytes",
                        "<memory.max.heap.size description>",
                        4_000_000_000L);
    }
}
