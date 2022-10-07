/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.ide.common.repository.GradleVersion.AgpVersion.PreviewKind.ALPHA;
import static com.android.ide.common.repository.GradleVersion.AgpVersion.PreviewKind.BETA;
import static com.android.ide.common.repository.GradleVersion.AgpVersion.PreviewKind.DEV;
import static com.android.ide.common.repository.GradleVersion.AgpVersion.PreviewKind.NONE;
import static com.android.ide.common.repository.GradleVersion.AgpVersion.PreviewKind.RC;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.text.MatchResult;
import kotlin.text.Regex;
import org.jetbrains.annotations.NotNull;

/**
 * Supports versions in the given formats:
 *
 * <ul>
 *   <li>major (e.g. 1)
 *   <li>major.minor (e.g. 1.0)
 *   <li>major.minor.micro (e.g. 1.1.1)
 * </ul>
 *
 * A version can also be a "previewType" (e.g. 1-alpha1, 1.0.0-rc2) or an unreleased version (or
 * "snapshot") (e.g. 1-SNAPSHOT, 1.0.0-alpha1-SNAPSHOT).
 */
public class GradleVersion implements Comparable<GradleVersion> {
    private static final String PLUS = "+";

    // TODO(b/242691473): This pattern is not inclusive for all the possible versions.
    private static final Pattern PREVIEW_AND_SNAPSHOT_PATTERN =
            Pattern.compile("([a-zA-z]+)[\\-]?([\\d]+)?[\\-]?(\\d+)?[\\-]?([a-zA-z]+)?");
    private final String mRawValue;

    @NonNull
    private final VersionSegment mMajorSegment;

    @Nullable
    private final VersionSegment mMinorSegment;

    @Nullable
    private final VersionSegment mMicroSegment;

    private final int mPreview;

    @Nullable
    private final String mPreviewType;

    private final boolean mSnapshot;

    @NonNull private final List<VersionSegment> mAdditionalSegments;

    private final VersionQualifiers mQualifiers;

    private static class VersionQualifiers {
        @NotNull String previewType;

        int preview;

        // TODO(b/242691473): We could have multiple additional previewType types and should
        //   consider all of them eventually.
        int additionalPreviewType;

        @Nullable String previewChannel;

        private VersionQualifiers(
                @NotNull String previewType,
                int preview,
                int additionalPreviewType,
                @Nullable String previewChannel) {
            this.previewType = previewType;
            this.preview = preview;
            this.additionalPreviewType = additionalPreviewType;
            this.previewChannel = previewChannel;
        }

        @NonNull
        @Override
        public String toString() {
            return previewType
                    + ((preview != 0) ? "-" + preview : "")
                    + ((additionalPreviewType != 0) ? "-" + additionalPreviewType : "")
                    + ((!isNullOrEmpty(previewChannel)) ? "-" + previewChannel : "");
        }
    }

    /**
     * Parses the given version. This method does the same as {@link #parse(String)}, but it does
     * not throw exceptions if the given value does not conform with any of the supported version
     * formats.
     *
     * @param value the version to parse.
     * @return the created {@code Version} object, or {@code null} if the given value does not
     * conform with any of the supported version formats.
     */
    @Nullable
    public static GradleVersion tryParse(@NonNull String value) {
        try {
            return parse(value);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Parses the given version.
     *
     * @param value the version to parse.
     * @return the created {@code Version} object.
     * @throws IllegalArgumentException if the given value does not conform with any of the
     *                                  supported version formats.
     */

    @NonNull
    public static GradleVersion parse(@NonNull String value) {
        String version = value;
        VersionQualifiers qualifiers = null;
        char dash = '-';
        int dashIndex = value.indexOf(dash);
        if (dashIndex != -1) {
            if (dashIndex < value.length() - 1) {
                String qualifiersText = value.substring(dashIndex + 1);
                String qualifierName = null;
                int qualifierValue = 0;
                int additionalQualifierValue = 0;
                String qualifierChannel = null;
                Matcher matcher = PREVIEW_AND_SNAPSHOT_PATTERN.matcher(qualifiersText);
                if (matcher.matches()) {
                    qualifierName = matcher.group(1);
                    if (matcher.groupCount() >= 2) {
                        String group = matcher.group(2);
                        if (!isNullOrEmpty(group)) {
                            qualifierValue = Integer.parseInt(group);
                        }
                        String group3 = matcher.group(3);
                        if (!isNullOrEmpty(group3)) {
                            additionalQualifierValue = Integer.parseInt(group3);
                        }
                        String group4 = matcher.group(4);
                        if (!isNullOrEmpty(group4)) {
                            qualifierChannel = group4;
                        }
                    }
                }
                if (!isNullOrEmpty(qualifierName)
                        || qualifierValue != 0
                        || additionalQualifierValue != 0
                        || !isNullOrEmpty(qualifierChannel)) {
                    qualifiers =
                            new VersionQualifiers(
                                    qualifierName,
                                    qualifierValue,
                                    additionalQualifierValue,
                                    qualifierChannel);
                }
            }
            version = value.substring(0, dashIndex);
        }

        try {
            List<VersionSegment> parsedVersionSegments = splitSegments(version);
            int segmentCount = parsedVersionSegments.size();

            VersionSegment majorSegment;
            VersionSegment minorSegment = null;
            VersionSegment microSegment = null;

            List<VersionSegment> additionalSegments = Lists.newArrayList();
            if (segmentCount > 0) {
                majorSegment = parsedVersionSegments.get(0);
                if (segmentCount > 1) {
                    minorSegment = parsedVersionSegments.get(1);
                }
                if (segmentCount >= 3) {
                    microSegment = parsedVersionSegments.get(2);
                }
                if (segmentCount > 3) {
                    additionalSegments.addAll(parsedVersionSegments.subList(3, segmentCount));
                }

                boolean snapshot = false;

                if (qualifiers != null) {
                    if (isSnapshotQualifier(qualifiers.toString())) {
                        snapshot = true;
                        qualifiers = null;
                    } else if (!isNullOrEmpty(qualifiers.previewChannel)
                            && isSnapshotQualifier(qualifiers.previewChannel)) {
                        snapshot = true;
                        qualifiers.previewChannel = null;
                    }
                }

                int preview = (qualifiers != null) ? qualifiers.preview : 0;
                String previewType =
                        (qualifiers != null)
                                ? qualifiers.previewType
                                : null; // PreviewType is never null

                return new GradleVersion(value, majorSegment, minorSegment, microSegment,
                        additionalSegments, preview, previewType, snapshot, qualifiers);
            }
        } catch (NumberFormatException e) {
            throw parsingFailure(value, e);
        }
        throw parsingFailure(value);
    }

    @NonNull
    private static List<VersionSegment> splitSegments(@NonNull String version) {
        Iterable<String> segments = Splitter.on('.').split(version);
        List<VersionSegment> parsedSegments = Lists.newArrayListWithCapacity(3);

        for (String segment : segments) {
            parsedSegments.addAll(parseSegment(segment));
        }

        return parsedSegments;
    }

    @NonNull
    private static List<VersionSegment> parseSegment(@NonNull String text) {
        int length = text.length();
        if (length > 1 && text.endsWith(PLUS)) {
            // Segment has a number and a '+' (e.g. second segment in '2.1+')
            List<VersionSegment> segments = Lists.newArrayListWithCapacity(2);

            // We need to split '1+' into 2 segments: '1' and '+'
            segments.add(new VersionSegment(text.substring(0, length - 1)));
            segments.add(new VersionSegment(PLUS));
            return segments;
        }
        return Collections.singletonList(new VersionSegment(text));
    }

    private static boolean isSnapshotQualifier(@NonNull String value) {
        return "SNAPSHOT".equalsIgnoreCase(value) || "dev".equalsIgnoreCase(value);
    }

    @NonNull
    private static IllegalArgumentException parsingFailure(@NonNull String value) {
        return parsingFailure(value, null);
    }

    @NonNull
    private static IllegalArgumentException parsingFailure(@NonNull String value,
            @Nullable Throwable cause) {
        return new IllegalArgumentException(String.format("'%1$s' is not a valid version", value),
                cause);
    }

    public GradleVersion(int major, int minor) {
        this((major + "." + minor), new VersionSegment(major),
                new VersionSegment(minor), null,
                Collections.emptyList(), 0, null, false, null);
    }

    public GradleVersion(int major, int minor, int micro) {
        this((major + "." + minor + "." + micro), new VersionSegment(major),
                new VersionSegment(minor), new VersionSegment(micro),
                Collections.emptyList(), 0, null, false, null);
    }

    private GradleVersion(
            @NonNull String rawValue,
            @NonNull VersionSegment majorSegment,
            @Nullable VersionSegment minorSegment,
            @Nullable VersionSegment microSegment,
            @NonNull List<VersionSegment> additionalSegments,
            int preview,
            @Nullable String previewType,
            boolean snapshot,
            VersionQualifiers qualifiers) {
        mRawValue = rawValue;
        mMajorSegment = majorSegment;
        mMinorSegment = minorSegment;
        mMicroSegment = microSegment;
        mAdditionalSegments = ImmutableList.copyOf(additionalSegments);
        mPreview = preview;
        mPreviewType = previewType;
        mSnapshot = snapshot;
        mQualifiers = qualifiers;
    }

    public int getMajor() {
        return valueOf(mMajorSegment);
    }

    @NonNull
    public VersionSegment getMajorSegment() {
        return mMajorSegment;
    }

    public int getMinor() {
        return valueOf(mMinorSegment);
    }

    @Nullable
    public VersionSegment getMinorSegment() {
        return mMinorSegment;
    }

    public int getMicro() {
        return valueOf(mMicroSegment);
    }

    private static int valueOf(@Nullable VersionSegment segment) {
        return segment != null ? segment.getValue() : 0;
    }

    @Nullable
    public VersionSegment getMicroSegment() {
        return mMicroSegment;
    }

    public int getPreview() {
        return mPreview;
    }

    public boolean isPreview() {
        return mPreviewType != null || mSnapshot;
    }

    @Nullable
    public String getPreviewType() {
        return mPreviewType;
    }

    public boolean isSnapshot() {
        return mSnapshot;
    }

    public int compareTo(@NonNull String version) {
        return compareTo(parse(version));
    }

    @Override
    public int compareTo(@NonNull GradleVersion version) {
        return compareTo(version, false);
    }

    public int compareIgnoringQualifiers(@NonNull String version) {
        return compareIgnoringQualifiers(parse(version));
    }

    public int compareIgnoringQualifiers(@NonNull GradleVersion version) {
        return compareTo(version, true);
    }

    private int compareTo(@NonNull GradleVersion version, boolean ignoreQualifiers) {
        int delta = getMajor() - version.getMajor();
        if (delta != 0) {
            return delta;
        }
        delta = getMinor() - version.getMinor();
        if (delta != 0) {
            return delta;
        }
        delta = getMicro() - version.getMicro();
        if (delta != 0) {
            return delta;
        }
        if (!ignoreQualifiers) {
            if (mQualifiers == null) {
                //noinspection VariableNotUsedInsideIf
                if (version.mQualifiers != null) {
                    return 1;
                }
                delta = mSnapshot == version.mSnapshot ? 0 : (mSnapshot ? -1 : 1);
            } else if (version.mQualifiers == null) {
                return -1;
            } else if (mQualifiers.previewType.equals("dev")
                    && version.mQualifiers.previewType.equals("dev")) {
                delta = mQualifiers.preview - version.mQualifiers.preview;
            } else if (mQualifiers.previewType.equals("dev")
                    || version.mQualifiers.previewType.equals("dev")) {
                delta = mQualifiers.previewType.equals("dev") ? -1 : 1;
            } else if (mQualifiers.previewType.equals(version.mQualifiers.previewType)) {
                delta = mQualifiers.preview - version.mQualifiers.preview;
                if (delta == 0) {
                    delta =
                            mQualifiers.additionalPreviewType
                                    - version.mQualifiers.additionalPreviewType;
                }
            } else {
                // TODO(b/242691473): Fix previewType comparison.
                delta = mQualifiers.previewType.compareTo(version.mQualifiers.previewType);
            }
        }
        return delta;
    }

    /**
     * Is this {@linkplain GradleVersion} at least as high as the given major, minor, micro version?
     */
    public boolean isAtLeast(int major, int minor, int micro) {
        return isAtLeast(major, minor, micro, null, 0, false);
    }

    /**
     * Is this {@linkplain GradleVersion} at least as high as the given major, minor, micro version?
     * Any previewType suffixes are ignored.
     */
    public boolean isAtLeastIncludingPreviews(int major, int minor, int micro) {
        return isAtLeast(major, minor, micro, "", 0, false);
    }

    /**
     * Is this {@linkplain GradleVersion} at least as high as the given
     * major, minor, micro version?
     */
    public boolean isAtLeast(int major, int minor, int micro,
            @Nullable String previewType, int previewVersion, boolean isSnapshot) {
        int delta = getMajor() - major;
        if (delta != 0) {
            return delta >= 0;
        }
        delta = getMinor() - minor;
        if (delta != 0) {
            return delta >= 0;
        }
        delta = getMicro() - micro;
        if (delta != 0) {
            return delta >= 0;
        }

        if (mPreviewType == null) {
            //noinspection VariableNotUsedInsideIf
            if (previewType != null) {
                return true;
            }
        } else if (previewType == null) {
            return false;
        } else {
            delta = mPreviewType.compareToIgnoreCase(previewType);
        }
        if (delta != 0) {
            return delta >= 0;
        }

        delta = mPreview - previewVersion;
        if (delta != 0) {
            return delta >= 0;
        }
        return mSnapshot == isSnapshot || !mSnapshot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradleVersion that = (GradleVersion) o;
        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mMajorSegment, mMinorSegment, mMicroSegment, mPreview, mPreviewType,
                mSnapshot);
    }

    @Override
    public String toString() {
        return mRawValue;
    }

    /** Returns the max of the two versions */
    @Nullable
    public static GradleVersion max(@Nullable GradleVersion version1, @Nullable GradleVersion version2) {
        if (version2 == null) {
            return version1;
        } else if (version1 == null) {
            return version2;
        } else if (version1.compareTo(version2) < 0) {
            return version2;
        } else {
            return version1;
        }
    }

    public static class VersionSegment {
        @NonNull
        private final String mText;

        private final int mValue;

        // Used for serialization by the IDE.
        @SuppressWarnings("unused")
        VersionSegment() {
            this(0);
        }

        VersionSegment(int value) {
            mText = String.valueOf(value);
            mValue = value;
        }

        VersionSegment(@NonNull String text) {
            mText = text;
            if (PLUS.equals(text)) {
                mValue = Integer.MAX_VALUE;
            } else {
                // +1 is a valid number which will be parsed correctly but it is not a correct
                // version segment.
                if (text.startsWith(PLUS)) {
                    throw new NumberFormatException("Version segment cannot start with +");
                }
                int value;
                try {
                    value = Integer.parseInt(text);
                }
                catch (NumberFormatException e) {
                    value = 0;
                }
                mValue = value;
            }
        }

        @NonNull
        public String getText() {
            return mText;
        }

        public int getValue() {
            return mValue;
        }

        public boolean acceptsGreaterValue() {
            return PLUS.equals(mText);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VersionSegment that = (VersionSegment) o;
            return Objects.equal(mText, that.mText);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mText);
        }

        @Override
        public String toString() {
            return mText;
        }
    }

    /**
     * This class is temporarily here, but is now incompatible with {@link GradleVersion}.  The
     * semantic error of comparing an AGP Version with a generic GradleVersion is now detected
     * at compile-time.
     */
    public static class AgpVersion implements Comparable<AgpVersion> {
        enum PreviewKind {
            // order is important for comparison
            ALPHA,
            BETA,
            RC,
            DEV,
            NONE
            ;

            @Override
            public String toString() {
                switch(this) {
                    case ALPHA: return "-alpha";
                    case BETA: return "-beta";
                    case RC: return "-rc";
                    case DEV: return "-dev";
                    case NONE: return "";
                    default: return super.toString();
                }
            }

            public static PreviewKind fromPreviewTypeAndIsSnapshot(
                    @Nullable String value,
                    boolean isSnapshot
            ) {
                if ("alpha".equals(value)) return ALPHA;
                if ("beta".equals(value)) return BETA;
                if ("rc".equals(value)) return RC;
                if (value == null && isSnapshot) return DEV;
                if (value == null && !isSnapshot) return NONE;
                throw new IllegalArgumentException(value + " is not a PreviewKind");
            }

            public @Nullable String toPreviewType() {
                switch(this) {
                    case ALPHA: return "alpha";
                    case BETA: return "beta";
                    case RC: return "rc";
                    case DEV:
                    case NONE:
                    default:
                        return null;
                }
            }

            public boolean toIsSnapshot() {
                switch(this) {
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
                @Nullable Integer preview
        ) {
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
                    "(" + num + ")" + dot + "(" + num + ")" + dot + "(" + num + ")" +
                    "(?:" + "(" + previewKind + ")" + "(" + digit + digit + "?" + ")" +
                    "|" + "(" + dev + ")" + ")?";
            VERSION_REGEX = new Regex(pattern);
        }

        /** precondition: PreviewKind should be ALPHA, BETA or RC */
        private static boolean isTwoDigitPreviewFormat(int major,
                int minor,
                int micro,
                PreviewKind previewKind) {
            return (major > 3) ||
                   (major == 3 && minor > 1) ||
                   (major == 3 && minor == 1 && micro == 0 && previewKind != BETA);
        }

        private static int parsePreviewString(int major,
                int minor,
                int micro,
                PreviewKind previewKind,
                String previewString) {
            if (isTwoDigitPreviewFormat(major, minor, micro, previewKind) &&
                previewString.length() != 2) {
                throw new NumberFormatException(
                        "AgpVersion " + major + "." + minor + "." + micro + previewKind +
                        " requires a two digit preview, but received \"" + previewString + "\".");
            }
            if (!isTwoDigitPreviewFormat(major, minor, micro, previewKind) &&
                previewString.startsWith("0")) {
                throw new NumberFormatException(
                        "AgpVersion " + major + "." + minor + "." + micro + previewKind +
                        " requires no zero-padding, but received \"" + previewString + "\".");
            }
            return Integer.parseInt(previewString);
        }

        /**
         * Attempt to parse {@param value} as a String corresponding to a valid AGP version.  The
         * (regular) language recognized is:
         *
         *   NUM "." NUM "." NUM (PREVIEW-KIND digit digit? | "-dev")?
         * where
         *   NUM = "0" | [1-9] digit*
         * and
         *   PREVIEW-KIND = "-" ("alpha" | "beta" | "rc")
         *
         * After the regular language is recognized, we additionally impose a constraint on the
         * numeric preview value, corresponding to the versions actually released: in the 3.0.0
         * series and earlier, and in the 3.1.0-beta series, numeric preview versions were used
         * with no padding; in 3.1.0-alpha and 3.1.0-rc and all later versions, numeric preview
         * versions were zero-padded with a field width of 2.
         *
         * (See also {@code AndroidGradlePluginVersion} in the {@code gradle-dsl} module).
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
                if ("-alpha".equals(matchList.get(3))) previewKind = ALPHA;
                else if ("-beta".equals(matchList.get(3))) previewKind = BETA;
                else if ("-rc".equals(matchList.get(3))) previewKind = RC;
                else if ("-dev".equals(matchList.get(5))) previewKind = DEV;
                else previewKind = NONE;
                Integer preview = null;
                switch(previewKind) {
                    case ALPHA:
                    case BETA:
                    case RC:
                        preview = parsePreviewString(major,
                                                     minor,
                                                     micro,
                                                     previewKind,
                                                     matchList.get(4));
                }
                return new AgpVersion(major, minor, micro, previewKind, preview);
            }
            catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * Parse {@param value} as a String corresponding to a valid AGP version.  See
         * {@link AgpVersion#tryParse} for details on the version string format recognized.
         *
         * @param value
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
                String pattern =
                        "(" + num + ")" + dot + "(" + num + ")" + dot + "(" + num + ")";
                STABLE_VERSION_REGEX = new Regex(pattern);
        }

        /**
         * Attempt to parse {@param value} as a String corresponding to a valid stable AGP version.
         * The (regular) language recognized is:
         *
         *   NUM "." NUM "." NUM
         * where
         *   NUM = "0" | [1-9] digit*
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
            }
            catch (NumberFormatException e) {
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
            if (previewKind != that.previewKind)
                return previewKind.ordinal() - that.previewKind.ordinal();
            if (preview != null && that.preview != null)
                return preview - that.preview;
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

        public boolean isAtLeast(int major, int minor, int micro,
                @Nullable String previewType, int previewVersion, boolean isSnapshot) {
            PreviewKind previewKind = PreviewKind.fromPreviewTypeAndIsSnapshot(previewType,
                                                                               isSnapshot);
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
                }
                else {
                    sb.append(String.format(Locale.US, "%d", preview));
                }
            }
            return sb.toString();
        }
    }
}
