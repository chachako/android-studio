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

package com.android.tools.pixelprobe.tests.psd;

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.tests.PixelProbeTestUtils;
import java.awt.*;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class ChannelsTest {
    @Test
    public void bitmap() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/bitmap.psd");
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(3, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void cmyk() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/cmyk.psd");
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(4, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void cmykAlpha() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/cmyk_alpha.psd");
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(5, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void duotone() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/duotone.psd");
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(1, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void duotoneAlpha() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/duotone_alpha.psd");
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(2, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void grayscale() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/grayscale.psd");
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(1, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void grayscaleAlpha() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/grayscale_alpha.psd");
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(2, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void indexed() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/indexed.psd");
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(3, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void indexedAlpha() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/indexed_alpha.psd");
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.BITMASK, image.getMergedImage().getTransparency());
        Assert.assertEquals(4, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void lab() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/lab.psd");
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(3, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void labAlpha() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/lab_alpha.psd");
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(4, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void rgb() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/rgb.psd");
        Assert.assertFalse(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.OPAQUE, image.getMergedImage().getTransparency());
        Assert.assertEquals(3, image.getMergedImage().getColorModel().getNumComponents());
    }

    @Test
    public void rgbAlpha() throws IOException {
        Image image = PixelProbeTestUtils.loadImage("psd/rgb_alpha.psd");
        Assert.assertTrue(image.getMergedImage().getColorModel().hasAlpha());
        Assert.assertEquals(Transparency.TRANSLUCENT, image.getMergedImage().getTransparency());
        Assert.assertEquals(4, image.getMergedImage().getColorModel().getNumComponents());
    }
}
