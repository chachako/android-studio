package com.android.flags;

import static com.google.common.truth.Truth.assertThat;

import com.android.flags.overrides.DefaultFlagOverrides;
import org.junit.Test;

public class FlagOverridesTest {
    @Test
    public void testAddingAndRemovingOverrides() throws Exception {
        FlagOverrides flagOverrides = new DefaultFlagOverrides();

        Flags flags = new Flags(flagOverrides);
        FlagGroup group = new FlagGroup(flags, "test", "Dummy");
        Flag<String> flagA = new StringFlag(group, "a", "Dummy", "Dummy", "A");
        Flag<String> flagB = new StringFlag(group, "b", "Dummy", "Dummy", "B");
        Flag<String> flagC = new StringFlag(group, "c", "Dummy", "Dummy", "C");
        Flag<String> flagD = new StringFlag(group, "d", "Dummy", "Dummy", "D");

        flagOverrides.put(flagA, "a");
        flagOverrides.put(flagB, "b");
        flagOverrides.put(flagC, "d");
        flagOverrides.put(flagC, "c");

        assertThat(flagOverrides.get(flagA)).isEqualTo("a");
        assertThat(flagOverrides.get(flagB)).isEqualTo("b");
        assertThat(flagOverrides.get(flagC)).isEqualTo("c");
        assertThat(flagOverrides.get(flagD)).isNull();

        flagOverrides.remove(flagB);
        assertThat(flagOverrides.get(flagB)).isNull();
    }
}
