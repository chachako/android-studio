/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.apk.analyzer;

import static org.junit.Assert.assertEquals;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceValue;
import java.nio.ByteBuffer;
import org.junit.Test;

public class BinaryXmlParserTest {
    @Test
    public void testFormatFloatValue() {
        BinaryResourceValue value42 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.FLOAT.code()
                                        },
                                        Ints.toByteArray(Float.floatToIntBits(42.0f)))));

        BinaryResourceValue value425 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.FLOAT.code()
                                        },
                                        Ints.toByteArray(Float.floatToIntBits(42.5f)))));

        assertEquals("42", BinaryXmlParser.formatValue(value42, null));
        assertEquals("42.5", BinaryXmlParser.formatValue(value425, null));
    }

    @Test
    public void testFormatDimensionValue() {
        BinaryResourceValue value1 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.DIMENSION.code()
                                        },
                                        Ints.toByteArray((384 << 8) + 1))));

        BinaryResourceValue value2 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.DIMENSION.code()
                                        },
                                        Ints.toByteArray(0x11400024))));

        assertEquals("384dp", BinaryXmlParser.formatValue(value1, null));
        assertEquals("34.5in", BinaryXmlParser.formatValue(value2, null));
    }

    @Test
    public void testFormatFractionValue() {
        BinaryResourceValue value1 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.FRACTION.code()
                                        },
                                        Ints.toByteArray(0x01000030))));

        BinaryResourceValue value2 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.FRACTION.code()
                                        },
                                        Ints.toByteArray(0x20000030))));

        BinaryResourceValue value3 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.FRACTION.code()
                                        },
                                        Ints.toByteArray(0x40000030))));

        BinaryResourceValue value4 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.FRACTION.code()
                                        },
                                        Ints.toByteArray(0x40000031))));

        assertEquals("0.78125%", BinaryXmlParser.formatValue(value1, null));
        assertEquals("25%", BinaryXmlParser.formatValue(value2, null));
        assertEquals("50%", BinaryXmlParser.formatValue(value3, null));
        assertEquals("50%p", BinaryXmlParser.formatValue(value4, null));
    }

    @Test
    public void testFormatColorValue() {
        BinaryResourceValue value1 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0,
                                            0x8,
                                            0x0,
                                            BinaryResourceValue.Type.INT_COLOR_ARGB8.code()
                                        },
                                        Ints.toByteArray(0xFF556677))));

        BinaryResourceValue value2 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0,
                                            0x8,
                                            0x0,
                                            BinaryResourceValue.Type.INT_COLOR_RGB8.code()
                                        },
                                        Ints.toByteArray(0xFF556677))));

        BinaryResourceValue value3 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0,
                                            0x8,
                                            0x0,
                                            BinaryResourceValue.Type.INT_COLOR_ARGB4.code()
                                        },
                                        Ints.toByteArray(0x1234F567))));

        BinaryResourceValue value4 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0,
                                            0x8,
                                            0x0,
                                            BinaryResourceValue.Type.INT_COLOR_RGB4.code()
                                        },
                                        Ints.toByteArray(0x1234F567))));

        assertEquals("#FF556677", BinaryXmlParser.formatValue(value1, null));
        assertEquals("#556677", BinaryXmlParser.formatValue(value2, null));
        assertEquals("#F567", BinaryXmlParser.formatValue(value3, null));
        assertEquals("#567", BinaryXmlParser.formatValue(value4, null));
    }

    @Test
    public void testFormatReferenceValue() {
        ResourceIdResolver resolver =
                i -> {
                    if (i == 0x12345678) {
                        return "@package:restype/res_name";
                    } else {
                        return "";
                    }
                };

        BinaryResourceValue value1 =
                BinaryResourceValue.create(
                        ByteBuffer.wrap(
                                Bytes.concat(
                                        new byte[] {
                                            0x0, 0x8, 0x0, BinaryResourceValue.Type.REFERENCE.code()
                                        },
                                        Ints.toByteArray(0x12345678))));

        assertEquals("@ref/0x12345678", BinaryXmlParser.formatValue(value1, null));
        assertEquals(
                "@package:restype/res_name", BinaryXmlParser.formatValue(value1, null, resolver));
    }
}
