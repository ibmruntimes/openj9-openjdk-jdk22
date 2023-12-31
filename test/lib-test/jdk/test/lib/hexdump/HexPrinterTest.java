/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.hexdump;

import jdk.test.lib.hexdump.HexPrinter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


/*
 * @test
 * @summary Check HexPrinter formatting
 * @library /test/lib
 * @compile HexPrinterTest.java
 * @run junit jdk.test.lib.hexdump.HexPrinterTest
 */
class HexPrinterTest {

    @Test
    void testMinimalToStringByteArray() {
        int len = 16;
        byte[] bytes = genData(len);
        StringBuilder expected = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < len; i++)
            expected.append(String.format("%02x", bytes[i]));
        String actual = HexPrinter.minimal().toString(bytes);
        System.out.println(actual);
        assertEquals(expected.toString(), actual, "Minimal format incorrect");
    }

    static Stream<Arguments> columnParams() {
        return Stream.of(
                Arguments.of("%4d: ", "%d ", 10, " ; ", 50, HexPrinter.Formatters.PRINTABLE, "\n"),
                Arguments.of("%03o: ", "%d ", 16, " ; ", 50, HexPrinter.Formatters.ofPrimitive(byte.class, ""), "\n"),
                Arguments.of("%5d: ", "%02x:", 16, " ; ", 50, HexPrinter.Formatters.ofPrimitive(byte.class, ""), "\n"),
                Arguments.of("%5d: ", "%3d", 16, " ; ", 50, HexPrinter.Formatters.ofPrimitive(byte.class, ""), "\n"),
                Arguments.of("%05o: ", "%3o", 8, " ; ", 50, HexPrinter.Formatters.ofPrimitive(byte.class, ""), "\n"),
                Arguments.of("%6x: ", "%02x", 8, " | ", 50, HexPrinter.Formatters.ofPrimitive(byte.class, "%d "), "\n"),
                Arguments.of("%2x: ", "%02x", 8, " | ", 50, HexPrinter.Formatters.PRINTABLE, "\n"),
                Arguments.of("%5d: ", "%02x", 16, " | ", 50, HexPrinter.Formatters.ofPrimitive(short.class, "%d "), "\n")
        );
    }

    static Stream<Arguments> builtinParams() {
        return Stream.of(
                Arguments.of("minimal", "", "%02x", 16, "", 64, HexPrinter.Formatters.NONE, ""),
                Arguments.of("canonical", "%08x  ", "%02x ", 16, "|", 31, HexPrinter.Formatters.PRINTABLE, "|" + System.lineSeparator()),
                Arguments.of("simple", "%04x: ", "%02x ", 16, " // ", 64, HexPrinter.Formatters.ASCII, System.lineSeparator()),
                Arguments.of("source", "    ", "(byte)%3d, ", 8, " // ", 64, HexPrinter.Formatters.PRINTABLE, System.lineSeparator())
        );
    }

    @ParameterizedTest
    @MethodSource("builtinParams")
    void testBuiltins(String name, String offsetFormat, String binFormat, int colWidth,
                             String annoDelim, int annoWidth,
                             HexPrinter.Formatter mapper, String lineSep) {
        HexPrinter f = switch (name) {
            case "minimal" -> HexPrinter.minimal();
            case "simple" -> HexPrinter.simple();
            case "canonical" -> HexPrinter.canonical();
            case "source" -> HexPrinter.source();
            default -> throw new IllegalStateException("Unexpected value: " + name);
        };

        testParams(f, offsetFormat, binFormat, colWidth, annoDelim, annoWidth, mapper, lineSep);

        String actual = f.toString();
        HexPrinter f2 = HexPrinter.simple()
                .withOffsetFormat(offsetFormat)
                .withBytesFormat(binFormat, colWidth)
                .formatter(mapper, annoDelim, annoWidth)
                .withLineSeparator(lineSep);
        String expected = f2.toString();
        assertEquals(expected, actual, "toString of " + name + " does not match");
    }

    @ParameterizedTest
    @MethodSource("columnParams")
    void testToStringTwoLines(String offsetFormat, String binFormat, int colWidth,
                                     String annoDelim, int annoWidth,
                                     HexPrinter.Formatter mapper, String lineSep) {
        HexPrinter f = HexPrinter.simple()
                .withOffsetFormat(offsetFormat)
                .withBytesFormat(binFormat, colWidth)
                .formatter(mapper, annoDelim, annoWidth)
                .withLineSeparator(lineSep);
        testParams(f, offsetFormat, binFormat, colWidth, annoDelim, annoWidth, mapper, lineSep);
    }

    static void testParams(HexPrinter printer, String offsetFormat, String binFormat, int colWidth,
                                  String annoDelim, int annoWidth,
                                  HexPrinter.Formatter mapper, String lineSep) {
        byte[] bytes = genData(colWidth * 2);
        System.out.println("Params: " + printer.toString());
        String out = printer.toString(bytes);
        System.out.println(out);

        // Compare the actual output with the expected output of each formatting element
        int padToWidth = colWidth * String.format(binFormat, 0xff).length();
        int ndx = 0;
        int valuesStart = 0;
        int l;
        for (int i = 0; i < bytes.length; i++) {
            if (i % colWidth == 0) {
                String offset = String.format(offsetFormat, i);
                l = offset.length();
                assertEquals(offset, out.substring(ndx, ndx + l),
                        "offset format mismatch: " + ndx);
                ndx += l;
                valuesStart = ndx;
            }
            String value = String.format(binFormat, (0xff & bytes[i]));
            l = value.length();
            assertEquals(value, out.substring(ndx, ndx + l),
                    "value format mismatch: " + ndx + ", i: " + i);
            ndx += l;
            if (((i + 1) % colWidth) == 0) {
                // Rest of line is for padding, delimiter, formatter
                String padding = " ".repeat(padToWidth - (ndx - valuesStart));
                assertEquals(padding, out.substring(ndx, ndx + padding.length()), "padding");
                ndx += padding.length();
                assertEquals(annoDelim, out.substring(ndx, ndx + annoDelim.length()),
                        "delimiter mismatch");
                ndx += annoDelim.length();

                // Formatter output is tested separately
                ndx = out.indexOf(lineSep, ndx) + lineSep.length();
            }
        }
    }

    @Test
    void testPrintable() {
        String expected =
                "................................" +
                " !\"#$%&'()*+,-./0123456789:;<=>?" +
                "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_" +
                "`abcdefghijklmnopqrstuvwxyz{|}~." +
                "................................" +
                "................................" +
                "................................" +
                "................................";
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte)i;
        HexPrinter p = HexPrinter.minimal()
                        .withBytesFormat("", 256)
                        .formatter(HexPrinter.Formatters.PRINTABLE, "", 512);
        String actual = p.toString(bytes);
        assertEquals(expected, actual, "Formatters.Printable mismatch");
    }

    @Test
    void testASCII() {
        String expected = "\\nul\\soh\\stx\\etx\\eot\\enq\\ack\\bel\\b\\t\\n\\vt\\f\\r\\so\\si\\dle" +
                "\\dc1\\dc2\\dc3\\dc4\\nak\\syn\\etb\\can\\em\\sub\\esc\\fs\\gs\\rs\\us" +
                " !\"#$%&'()*+,-./0123456789:;<=>?" +
                "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_" +
                "`abcdefghijklmnopqrstuvwxyz{|}~\\127" +
                "\\128\\129\\130\\131\\132\\133\\134\\135\\136\\137\\138\\139\\140\\141\\142\\143" +
                "\\144\\145\\146\\147\\148\\149\\150\\151\\152\\153\\154\\155\\156\\157\\158\\159" +
                "\\160\\161\\162\\163\\164\\165\\166\\167\\168\\169\\170\\171\\172\\173\\174\\175" +
                "\\176\\177\\178\\179\\180\\181\\182\\183\\184\\185\\186\\187\\188\\189\\190\\191" +
                "\\192\\193\\194\\195\\196\\197\\198\\199\\200\\201\\202\\203\\204\\205\\206\\207" +
                "\\208\\209\\210\\211\\212\\213\\214\\215\\216\\217\\218\\219\\220\\221\\222\\223" +
                "\\224\\225\\226\\227\\228\\229\\230\\231\\232\\233\\234\\235\\236\\237\\238\\239" +
                "\\240\\241\\242\\243\\244\\245\\246\\247\\248\\249\\250\\251\\252\\253\\254\\255";
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte)i;
        HexPrinter p = HexPrinter.minimal()
                        .withBytesFormat("", 256)
                        .formatter(HexPrinter.Formatters.ASCII, "", 256);
        String actual = p.toString(bytes);
        assertEquals(expected, actual, "Formatters.ASCII mismatch");
    }

    static Stream<Arguments> formatterParams() {
        return Stream.of(
                Arguments.of(byte.class, ""),
                Arguments.of(byte.class, "%02x: "),
                Arguments.of(short.class, "%d "),
                Arguments.of(int.class, "%08x, "),
                Arguments.of(long.class, "%16x "),
                Arguments.of(float.class, "%3.4f "),
                Arguments.of(double.class, "%6.3g "),
                Arguments.of(boolean.class, "%b ")
        );
    }

    @ParameterizedTest
    @MethodSource("formatterParams")
    void testFormatter(Class<?> primClass, String fmtString) {
        HexPrinter.Formatter formatter = HexPrinter.Formatters.ofPrimitive(primClass, fmtString);
        // Create a byte array with data for two lines
        int colWidth = 8;
        byte[] bytes = genData(colWidth);
        StringBuilder sb = new StringBuilder();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        DataInputStream in2 = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            while (true) {
                formatter.annotate(in, sb);
                Object n = readPrimitive(primClass, in2);
                String expected = String.format(fmtString, n);
                assertEquals(expected, sb.toString(), "mismatch");
                sb.setLength(0);
            }
        } catch (IOException ioe) {
            // EOF is done
        }
        try {
            assertEquals(0, in.available(), "not all input consumed");
            assertEquals(0, in2.available(), "not all 2nd stream input consumed");
        } catch (IOException ioe) {
            //
        }
    }

    @ParameterizedTest
    @MethodSource("formatterParams")
    void testHexPrinterPrimFormatter(Class<?> primClass, String fmtString) {
        // Create a byte array with data for two lines
        int colWidth = 8;
        byte[] bytes = genData(colWidth);

        HexPrinter p = HexPrinter.simple()
                .formatter(primClass, fmtString);
        String actual = p.toString(bytes);
        System.out.println(actual);
        // The formatter should produce the same output as using the formatter method
        // with an explicit formatter for the primitive
        String expected = HexPrinter.simple()
                .formatter(HexPrinter.Formatters.ofPrimitive(primClass, fmtString))
                .toString(bytes);
        assertEquals(expected, actual, "mismatch");
    }

    private static Object readPrimitive(Class<?> primClass, DataInputStream in) throws IOException {
        if (int.class.equals(primClass)) {
            return in.readInt();
        } else if (byte.class.equals(primClass)) {
            return (int) in.readByte();
        } else if (short.class.equals(primClass)) {
            return in.readShort();
        } else if (char.class.equals(primClass)) {
            return in.readChar();
        } else if (long.class.equals(primClass)) {
            return in.readLong();
        } else if (float.class.equals(primClass)) {
            return in.readFloat();
        } else if (double.class.equals(primClass)) {
            return in.readDouble();
        } else if (boolean.class.equals(primClass)) {
            return in.readBoolean();
        } else {
            throw new RuntimeException("unknown primitive class: " + primClass);
        }
    }

    static Stream<Arguments> sources() {
        return Stream.of(
                Arguments.of(genBytes(21), 0, -1),
                Arguments.of(genBytes(21), 5, 12)
        );
    }

    static Stream<Arguments> badSources() {
        return Stream.of(
                Arguments.of(genBytes(21), 5, 22)
        );
    }

    static byte[] genData(int len) {
        // Create a byte array with data for two lines
        byte[] bytes = new byte[len];
        for (int i = 0; i < len / 2; i++) {
            bytes[i] = (byte) (i + 'A');
            bytes[i + len / 2] = (byte) (i + 'A' + 128);
        }
        return bytes;
    }

    static byte[] genFloat(int len) {
        byte[] bytes = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            for (int i = 0; i < len; i++) {
                out.writeFloat(i);
            }
            bytes = baos.toByteArray();
        } catch (IOException unused) {
        }
        return bytes;
    }

    static byte[] genDouble(int len) {
        byte[] bytes = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            for (int i = 0; i < len; i++) {
                out.writeDouble(i);
            }
            bytes = baos.toByteArray();
        } catch (IOException unused) {
        }
        return bytes;
    }

    static byte[] genBytes(int len) {
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++)
            bytes[i] = (byte) ('A' + i);
        return bytes;
    }

    ByteBuffer genByteBuffer(int len) {
        return ByteBuffer.wrap(genBytes(len));
    }

    InputStream genInputStream(int len) {
        return new ByteArrayInputStream(genBytes(len));
    }

    @Test
    void testNilPrinterBigBuffer() {
        byte[] bytes = new byte[1024];
        HexPrinter p = HexPrinter.minimal();
        String r = p.toString(bytes);
        assertEquals(bytes.length * 2, r.length(), "encoded byte wrong size");
        assertEquals(0, r.replace("00", "").length(), "contents not all zeros");
    }

    @ParameterizedTest
    @MethodSource("badSources")
    void testBadToStringByteBuffer(byte[] bytes, int offset, int length) {
        assertThrows(java.lang.IndexOutOfBoundsException.class,
                () -> testThrowsForBadToStringByteBuffer(bytes, offset, length));
    }

    void testThrowsForBadToStringByteBuffer(byte[] bytes, int offset, int length) {
        if (length < 0)
            length = bytes.length - offset;
        ByteBuffer bb = ByteBuffer.wrap(bytes, 0, bytes.length);
        System.out.printf("Source: %s, off: %d, len: %d%n",
                bytes.getClass().getName(), offset, length);
        String actual;
        if (offset == 0 && length < 0) {
            bb.position(offset);
            bb.limit(length);
            actual = HexPrinter.simple().toString(bb);
        } else
            actual = HexPrinter.simple().toString(bb, offset, length);
        System.out.println(actual);
        String expected = HexPrinter.simple().toString(bytes, offset, length);
        assertEquals(expected, actual, "mismatch in format()");
    }

    @ParameterizedTest
    @MethodSource("sources")
    void testToStringByteBuffer(byte[] bytes, int offset, int length) {
        if (length < 0)
            length = bytes.length - offset;
        ByteBuffer bb = ByteBuffer.wrap(bytes, 0, bytes.length);
        System.out.printf("Source: %s, off: %d, len: %d%n",
                bytes.getClass().getName(), offset, length);
        String actual;
        if (offset == 0 && length < 0) {
            bb.position(offset);
            bb.limit(length);
            actual = HexPrinter.simple().toString(bb);
        } else
            actual = HexPrinter.simple().toString(bb, offset, length);
        System.out.println(actual);
        String expected = HexPrinter.simple().toString(bytes, offset, length);
        assertEquals(expected, actual, "mismatch in format()");
    }

    @ParameterizedTest
    @MethodSource("sources")
    void testFormatBytes(byte[] bytes, int offset, int length) {
        int len = length >= 0 ? length : bytes.length;
        System.out.printf("Source: %s, off: %d, len: %d%n",
                "bytes", offset, len);
        StringBuilder sb = new StringBuilder();
        if (offset == 0 && length < 0)
            HexPrinter.simple().dest(sb).format(bytes);
        else
            HexPrinter.simple().dest(sb).format(bytes, offset, len);
        String actual = sb.toString();
        System.out.println(actual);
        String expected = HexPrinter.simple().toString(bytes, offset, len);
        assertEquals(expected, actual, "mismatch in format()");
    }

    @ParameterizedTest
    @MethodSource("sources")
    void testFormatByteBuffer(byte[] bytes, int offset, int length) {
        if (length < 0)
            length = bytes.length - offset;
        ByteBuffer bb = ByteBuffer.wrap(bytes, 0, bytes.length);
        System.out.printf("Source: %s, off: %d, len: %d%n",
                bytes.getClass().getName(), offset, length);
        StringBuilder sb = new StringBuilder();
        if (offset == 0 && length < 0) {
            bb.position(offset);
            bb.limit(length);
            HexPrinter.simple().dest(sb).format(bb);
        } else
            HexPrinter.simple().dest(sb).format(bb, offset, length);
        String actual = sb.toString();
        System.out.println(actual);
        String expected = HexPrinter.simple().toString(bytes, offset, length);
        assertEquals(expected, actual, "mismatch in format()");
    }

    @ParameterizedTest
    @MethodSource("sources")
    void testFormatInputStream(byte[] bytes, int offset, int length) {
        // Offset is ignored
        InputStream is = new ByteArrayInputStream(bytes, 0, length);
        StringBuilder sb = new StringBuilder();
        System.out.printf("Source: %s, off: %d, len: %d%n",
                bytes.getClass().getName(), offset, length);
        HexPrinter.simple().dest(sb).format(is);
        String actual = sb.toString();
        System.out.println(actual);
        String expected = HexPrinter.simple().toString(bytes, 0, length);
        assertEquals(expected, actual, "mismatch in format()");
    }

    @Test
    void testNullByteArray() {
        assertThrows(NullPointerException.class,
                () -> HexPrinter.simple().dest(System.out).format((byte[]) null));
    }

    @Test
    void testNullByteArrayOff() {
        assertThrows(NullPointerException.class,
                () -> HexPrinter.simple().dest(System.out).format((byte[]) null, 0, 1));
    }

    @Test
    void testNullByteBuffer() {
        assertThrows(NullPointerException.class,
                () -> HexPrinter.simple().dest(System.out).format((ByteBuffer) null));
    }

    @Test
    void testNullByteBufferOff() {
        assertThrows(NullPointerException.class,
                () -> HexPrinter.simple().dest(System.out).format((ByteBuffer) null, 0, 1));
    }

    @Test
    void testNullInputStream() {
        assertThrows(NullPointerException.class, () ->
                HexPrinter.simple().dest(System.out).format((InputStream) null));
    }

}
