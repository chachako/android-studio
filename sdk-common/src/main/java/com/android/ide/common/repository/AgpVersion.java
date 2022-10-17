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
package com.android.ide.common.repository;

import static com.android.ide.common.repository.AgpVersion.PreviewKind.ALPHA;
import static com.android.ide.common.repository.AgpVersion.PreviewKind.BETA;
import static com.android.ide.common.repository.AgpVersion.PreviewKind.DEV;
import static com.android.ide.common.repository.AgpVersion.PreviewKind.NONE;
import static com.android.ide.common.repository.AgpVersion.PreviewKind.RC;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import java.util.List;
import java.util.Locale;
import kotlin.text.MatchResult;
import kotlin.text.Regex;
import org.jetbrains.annotations.NotNull;

/**
 * This class is deliberately non-comparable with {@link GradleVersion}, for two reasons: firstly,
 * the ordering semantics we depend on for `-dev` versions are incompatible with the specified
 * ordering of Gradle version specifiers; secondly, we use Android Gradle Plugin versions as version
 * identifiers in various places in the IDE (particularly in sync-related areas) and it would be a
 * semantic error to compare such a version with a generic Gradle version.
 */
public class AgpVersion implements Comparable<AgpVersion> {

    enum PreviewKind {
        // order is important for comparison
        ALPHA,
        BETA,
        RC,
        DEV,
        NONE;

        @Override
        public String toString() {
            switch (this) {
                case ALPHA:
                    return "-alpha";
                case BETA:
                    return "-beta";
                case RC:
                    return "-rc";
                case DEV:
                    return "-dev";
                case NONE:
                    return "";
                default:
                    return super.toString();
            }
        }

        public static PreviewKind fromPreviewTypeAndIsSnapshot(
                @Nullable String value, boolean isSnapshot) {
            if ("alpha".equals(value)) return ALPHA;
            if ("beta".equals(value)) return BETA;
            if ("rc".equals(value)) return RC;
            if (value == null && isSnapshot) return DEV;
            if (value == null && !isSnapshot) return NONE;
            throw new IllegalArgumentException(value + " is not a PreviewKind");
        }

        public @Nullable String toPreviewType() {
            switch (this) {
                case ALPHA:
                    return "alpha";
                case BETA:
                    return "beta";
                case RC:
                    return "rc";
                case DEV:
                case NONE:
                default:
                    return null;
            }
        }

        public boolean toIsSnapshot() {
            switch (this) {
                case ALPHA:
                case BETA:
                case RC:
                case NONE:
                    return false;
                case DEV:
                default:
                    return true;
            }
        }
    }

    private final int major;

    public int getMajor() {
        return major;
    }

    private final int minor;

    public int getMinor() {
        return minor;
    }

    private final int micro;

    public int getMicro() {
        return micro;
    }

    private final @NonNull PreviewKind previewKind;

    public @NonNull PreviewKind getPreviewKind() {
        return previewKind;
    }

    public @Nullable String getPreviewType() {
        return previewKind.toPreviewType();
    }

    public boolean isPreview() {
        return !NONE.equals(previewKind);
    }

    public boolean isSnapshot() {
        return previewKind.toIsSnapshot();
    }

    private final @Nullable Integer preview;

    public @Nullable Integer getPreview() {
        return preview;
    }

    public AgpVersion(int major, int minor) {
        this(major, minor, 0);
    }

    public AgpVersion(int major, int minor, int micro) {
        this(major, minor, micro, NONE, null);
    }

    private AgpVersion(
            int major,
            int minor,
            int micro,
            @NonNull PreviewKind previewKind,
            @Nullable Integer preview) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.previewKind = previewKind;
        this.preview = preview;
    }

    private static final Regex VERSION_REGEX;

    static {
        String digit = "[0-9]";
        String num = "(?:0|[1-9]" + digit + "*)";
        String previewKind = "-(?:alpha|beta|rc)";
        String dot = Regex.Companion.escape(".");
        String dev = "-dev";
        String pattern =
                "("
                        + num
                        + ")"
                        + dot
                        + "("
                        + num
                        + ")"
                        + dot
                        + "("
                        + num
                        + ")"
                        + "(?:"
                        + "("
                        + previewKind
                        + ")"
                        + "("
                        + digit
                        + digit
                        + "?"
                        + ")"
                        + "|"
                        + "("
                        + dev
                        + ")"
                        + ")?";
        VERSION_REGEX = new Regex(pattern);
    }

    /** precondition: PreviewKind should be ALPHA, BETA or RC */
    private static boolean isTwoDigitPreviewFormat(
            int major, int minor, int micro, PreviewKind previewKind) {
        return (major > 3)
                || (major == 3 && minor > 1)
                || (major == 3 && minor == 1 && micro == 0 && previewKind != BETA);
    }

    private static int parsePreviewString(
            int major, int minor, int micro, PreviewKind previewKind, String previewString) {
        if (isTwoDigitPreviewFormat(major, minor, micro, previewKind)
                && previewString.length() != 2) {
            throw new NumberFormatException(
                    "AgpVersion "
                            + major
                            + "."
                            + minor
                            + "."
                            + micro
                            + previewKind
                            + " requires a two digit preview, but received \""
                            + previewString
                            + "\".");
        }
        if (!isTwoDigitPreviewFormat(major, minor, micro, previewKind)
                && previewString.startsWith("0")) {
            throw new NumberFormatException(
                    "AgpVersion "
                            + major
                            + "."
                            + minor
                            + "."
                            + micro
                            + previewKind
                            + " requires no zero-padding, but received \""
                            + previewString
                            + "\".");
        }
        return Integer.parseInt(previewString);
    }

    /**
     * Attempt to parse {@param value} as a String corresponding to a valid AGP version. The
     * (regular) language recognized is:
     *
     * <p>NUM "." NUM "." NUM (PREVIEW-KIND digit digit? | "-dev")? where NUM = "0" | [1-9] digit*
     * and PREVIEW-KIND = "-" ("alpha" | "beta" | "rc")
     *
     * <p>After the regular language is recognized, we additionally impose a constraint on the
     * numeric preview value, corresponding to the versions actually released: in the 3.0.0 series
     * and earlier, and in the 3.1.0-beta series, numeric preview versions were used with no
     * padding; in 3.1.0-alpha and 3.1.0-rc and all later versions, numeric preview versions were
     * zero-padded with a field width of 2.
     *
     * <p>(See also {@code AndroidGradlePluginVersion} in the {@code gradle-dsl} module).
     *
     * @param value a String
     * @return an AgpVersion object corresponding to {@param value}, or null
     */
    @Nullable
    public static AgpVersion tryParse(@NonNull String value) {
        final MatchResult matchResult = VERSION_REGEX.matchEntire(value);
        if (matchResult == null) return null;
        final List<String> matchList = matchResult.getDestructured().toList();
        try {
            final int major = Integer.parseInt(matchList.get(0));
            final int minor = Integer.parseInt(matchList.get(1));
            final int micro = Integer.parseInt(matchList.get(2));
            PreviewKind previewKind;
            if ("-alpha".equals(matchList.get(3))) {
                previewKind = ALPHA;
            } else if ("-beta".equals(matchList.get(3))) {
                previewKind = BETA;
            } else if ("-rc".equals(matchList.get(3))) {
                previewKind = RC;
            } else if ("-dev".equals(matchList.get(5))) {
                previewKind = DEV;
            } else {
                previewKind = NONE;
            }
            Integer preview = null;
            switch (previewKind) {
                case ALPHA:
                case BETA:
                case RC:
                    preview =
                            parsePreviewString(major, minor, micro, previewKind, matchList.get(4));
            }
            return new AgpVersion(major, minor, micro, previewKind, preview);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse {@param value} as a String corresponding to a valid AGP version. See {@link
     * AgpVersion#tryParse} for details on the version string format recognized.
     *
     * @return an AgpVersion object corresponding to {@param value}.
     * @throws IllegalArgumentException if {@param value} is not a valid AGP version string.
     */
    @NonNull
    public static AgpVersion parse(@NonNull String value) {
        AgpVersion agpVersion = tryParse(value);
        if (agpVersion == null) {
            throw new IllegalArgumentException(value + " is not a valid AGP version string.");
        }
        return agpVersion;
    }

    private static final Regex STABLE_VERSION_REGEX;

    static {
        String digit = "[0-9]";
        String num = "(?:0|[1-9]" + digit + "*)";
        String dot = Regex.Companion.escape(".");
        String pattern = "(" + num + ")" + dot + "(" + num + ")" + dot + "(" + num + ")";
        STABLE_VERSION_REGEX = new Regex(pattern);
    }

    /**
     * Attempt to parse {@param value} as a String corresponding to a valid stable AGP version. The
     * (regular) language recognized is:
     *
     * <p>NUM "." NUM "." NUM where NUM = "0" | [1-9] digit*
     *
     * @param value a String
     * @return an AgpVersion object corresponding to {@param value}, or null
     */
    @Nullable
    public static AgpVersion tryParseStable(@NonNull String value) {
        final MatchResult matchResult = STABLE_VERSION_REGEX.matchEntire(value);
        if (matchResult == null) return null;
        final List<String> matchList = matchResult.getDestructured().toList();
        try {
            final int major = Integer.parseInt(matchList.get(0));
            final int minor = Integer.parseInt(matchList.get(1));
            final int micro = Integer.parseInt(matchList.get(2));
            return new AgpVersion(major, minor, micro);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgpVersion that = (AgpVersion) o;
        return this.compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(major, minor, micro, previewKind, preview);
    }

    @Override
    public int compareTo(@NotNull AgpVersion that) {
        if (major != that.major) return major - that.major;
        if (minor != that.minor) return minor - that.minor;
        if (micro != that.micro) return micro - that.micro;
        if (previewKind != that.previewKind) {
            return previewKind.ordinal() - that.previewKind.ordinal();
        }
        if (preview != null && that.preview != null) {
            return preview - that.preview;
        }
        return 0; // if either is null, both must be null (both DEV or NONE versions).
    }

    public int compareTo(@NotNull String value) {
        return compareTo(AgpVersion.parse(value));
    }

    public int compareIgnoringQualifiers(@NonNull AgpVersion that) {
        AgpVersion thisWithoutQualifiers = new AgpVersion(this.major, this.minor, this.micro);
        AgpVersion thatWithoutQualifiers = new AgpVersion(that.major, that.minor, that.micro);
        return thisWithoutQualifiers.compareTo(thatWithoutQualifiers);
    }

    public int compareIgnoringQualifiers(@NonNull String value) {
        return compareIgnoringQualifiers(AgpVersion.parse(value));
    }

    public boolean isAtLeast(int major, int minor, int micro) {
        return this.compareTo(new AgpVersion(major, minor, micro)) >= 0;
    }

    public boolean isAtLeastIncludingPreviews(int major, int minor, int micro) {
        AgpVersion thisWithoutQualifiers = new AgpVersion(this.major, this.minor, this.micro);
        return thisWithoutQualifiers.compareTo(new AgpVersion(major, minor, micro)) >= 0;
    }

    public boolean isAtLeast(
            int major,
            int minor,
            int micro,
            @Nullable String previewType,
            int previewVersion,
            boolean isSnapshot) {
        PreviewKind previewKind = PreviewKind.fromPreviewTypeAndIsSnapshot(previewType, isSnapshot);
        AgpVersion that = new AgpVersion(major, minor, micro, previewKind, previewVersion);
        return this.compareTo(that) >= 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%d.%d.%d%s", major, minor, micro, previewKind));
        if (preview != null) {
            if (isTwoDigitPreviewFormat(major, minor, micro, previewKind)) {
                sb.append(String.format(Locale.US, "%02d", preview));
            } else {
                sb.append(String.format(Locale.US, "%d", preview));
            }
        }
        return sb.toString();
    }
}
