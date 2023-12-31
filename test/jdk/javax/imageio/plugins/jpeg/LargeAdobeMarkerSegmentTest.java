/*
 * Copyright 2023 Alphabet LLC.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug     6355567
 * @summary Verifies that AdobeMarkerSegment() keeps the available bytes
 *          and buffer pointer in sync, when a non-standard length Adobe
 *          marker is encountered.
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class LargeAdobeMarkerSegmentTest {

    private static String fileName = "jdk_6355567.jpg";

    public static void main(String[] args) throws IOException {
      /*
       * Open a JPEG image, and get the metadata. Without the fix for
       * 6355567, a NegativeArraySizeException is thrown while reading
       * the metadata from the JPEG below.
       */
      String sep = System.getProperty("file.separator");
      String dir = System.getProperty("test.src", ".");
      String filePath = dir+sep+fileName;
      System.out.println("Test file: " + filePath);
      File f = new File(filePath);
      ImageInputStream iis = ImageIO.createImageInputStream(f);
      ImageReader r = ImageIO.getImageReaders(iis).next();
      r.setInput(iis);
      r.getImageMetadata(0);
    }
}
