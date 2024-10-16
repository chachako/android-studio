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

package com.android.builder.merge;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import com.android.annotations.NonNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;

import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

public class StreamMergeAlgorithmsTests {

    /** The stream after merging. */
    private InputStream mergedStream;

    /**
     * Transforms a two dimensional array of data into a list of input streams, each returning an
     * element from the array.
     *
     * @param data the input arrays; must contain at least one element
     * @return a list with as many elements as {@code data}
     */
    @NonNull
    private static ImmutableList<MergeInput> makeInputs(@NonNull byte[][] data) {
        Preconditions.checkArgument(data.length > 0);

        ImmutableList.Builder<MergeInput> builder = ImmutableList.builder();
        for (byte[] streamData : data) {
            builder.add(new MergeInput(new ByteArrayInputStream(streamData), "name"));
        }

        return builder.build();
    }

    @Test
    public void pickFirstOneFile() throws IOException {
        StreamMergeAlgorithm pickFirst = StreamMergeAlgorithms.pickFirst();
        List<MergeInput> inputStreams = makeInputs(new byte[][] {{1, 2, 3}});
        try (Closer closer = Closer.create()) {
            mergedStream = pickFirst.merge("foo", inputStreams, closer);
            assertThat(mergedStream).isSameAs(inputStreams.get(0).getStream());
        }
    }

    @Test
    public void pickFirstTwoFiles() throws IOException {
        StreamMergeAlgorithm pickFirst = StreamMergeAlgorithms.pickFirst();
        List<MergeInput> inputStreams = makeInputs(new byte[][] {{1, 2, 3}, {4, 5, 6}});
        try (Closer closer = Closer.create()) {
            mergedStream = pickFirst.merge("foo", inputStreams, closer);
            assertThat(mergedStream).isSameAs(inputStreams.get(0).getStream());
        }
    }

    @Test
    public void concatOneFile() throws IOException {
        StreamMergeAlgorithm concat = StreamMergeAlgorithms.concat();
        List<MergeInput> inputStreams = makeInputs(new byte[][] {{1, 2, 3}});
        try (Closer closer = Closer.create()) {
            mergedStream = concat.merge("foo", inputStreams, closer);
            assertThat(ByteStreams.toByteArray(mergedStream)).isEqualTo(new byte[] {1, 2, 3});
        }
    }

    @Test
    public void concatTwoFiles() throws IOException {
        StreamMergeAlgorithm concat = StreamMergeAlgorithms.concat();
        List<MergeInput> inputStreams = makeInputs(new byte[][] {{1, 2}, {}, {3, 4, '\n'}, {5, 6}});
        try (Closer closer = Closer.create()) {
            mergedStream = concat.merge("foo", inputStreams, closer);
            assertThat(ByteStreams.toByteArray(mergedStream))
                    .isEqualTo(new byte[] {1, 2, '\n', 3, 4, '\n', 5, 6});
        }
    }

    @Test
    public void acceptOnlyOneOneFile() throws IOException {
        StreamMergeAlgorithm acceptOne = StreamMergeAlgorithms.acceptOnlyOne();
        List<MergeInput> inputStreams = makeInputs(new byte[][] {{1, 2, 3}});
        try (Closer closer = Closer.create()) {
            mergedStream = acceptOne.merge("foo", inputStreams, closer);
            assertThat(mergedStream).isSameAs(inputStreams.get(0).getStream());
        }
    }

    @Test
    public void acceptOnlyOneTwoFiles() throws IOException {
        StreamMergeAlgorithm acceptOne = StreamMergeAlgorithms.acceptOnlyOne();
        try (Closer closer = Closer.create()) {
            acceptOne.merge("foo", makeInputs(new byte[][] {{1, 2, 3}, {4, 5, 6}}), closer);
            fail();
        } catch (DuplicateRelativeFileException e) {
            /*
             * Expected.
             */
        }
    }

    @Test
    public void select() throws IOException {
        StreamMergeAlgorithm alg1 = Mockito.mock(StreamMergeAlgorithm.class);
        StreamMergeAlgorithm alg2 = Mockito.mock(StreamMergeAlgorithm.class);
        Function<String, StreamMergeAlgorithm> f = (p -> p.equals("foo")? alg1 : alg2);

        StreamMergeAlgorithm select = StreamMergeAlgorithms.select(f);

        ImmutableList<MergeInput> inputs = makeInputs(new byte[][] {{1, 2, 3}});
        try (Closer closer = Closer.create()) {
            select.merge("foo", inputs, closer);

            Mockito.verify(alg1)
                    .merge(
                            ArgumentMatchers.eq("foo"),
                            ArgumentMatchers.same(inputs),
                            ArgumentMatchers.same(closer));
            Mockito.verifyNoMoreInteractions(alg2);

            select.merge("bar", inputs, closer);
            Mockito.verifyNoMoreInteractions(alg1);
            Mockito.verify(alg2)
                    .merge(
                            ArgumentMatchers.eq("bar"),
                            ArgumentMatchers.same(inputs),
                            ArgumentMatchers.same(closer));
        }
    }
}
