package com.milktea.fileserver;

import java.util.ArrayList;
import java.util.Arrays;


public final class QrCode {


    public static final int ECC_L = 0;
    public static final int ECC_M = 1;
    public static final int ECC_Q = 2;
    public static final int ECC_H = 3;

    private static final int MIN_VERSION = 1;
    private static final int MAX_VERSION = 10;


    private static final int[][] ECC_CODEWORDS_PER_BLOCK = {
             {0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18},
             {0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26},
             {0, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24},
             {0, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28},
    };


    private static final int[][] NUM_ERROR_CORRECTION_BLOCKS = {
             {0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4},
             {0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5},
             {0, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8},
             {0, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8},
    };


    private static final int[] ECL_FORMAT_BITS = {1, 0, 3, 2};

    public final int version;
    public final int size;
    public final int eccLevel;
    public final int mask;

    private final boolean[][] modules;
    private final boolean[][] isFunction;


    public static QrCode encodeText(String text, int eccLevel) {
        byte[] data;
        try {
            data = text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            data = text.getBytes();
        }
        return encodeBytes(data, eccLevel);
    }


    public static QrCode encodeBytes(byte[] data, int eccLevel) {
        if (eccLevel < 0 || eccLevel > 3) throw new IllegalArgumentException("bad ecc level");

        int version = -1;
        int ccBits = 0;
        for (int v = MIN_VERSION; v <= MAX_VERSION; v++) {
            int cc = (v <= 9) ? 8 : 16;
            int capacityBits = getNumDataCodewords(v, eccLevel) * 8;
            if (4 + cc + data.length * 8 <= capacityBits) {
                version = v;
                ccBits = cc;
                break;
            }
        }
        if (version < 0) throw new IllegalArgumentException("data too long for version " + MAX_VERSION);

        BitBuffer bb = new BitBuffer();
        bb.appendBits(0x4, 4);
        bb.appendBits(data.length, ccBits);
        for (byte b : data) bb.appendBits(b & 0xFF, 8);

        int dataCapacityBits = getNumDataCodewords(version, eccLevel) * 8;
        bb.appendBits(0, Math.min(4, dataCapacityBits - bb.bitLength()));
        bb.appendBits(0, (8 - bb.bitLength() % 8) % 8);
        for (int pad = 0xEC; bb.bitLength() < dataCapacityBits; pad ^= 0xEC ^ 0x11) {
            bb.appendBits(pad, 8);
        }

        byte[] dataCodewords = bb.toBytes();
        int rawCodewords = getNumRawDataModules(version) / 8;
        byte[] allCodewords = addEccAndInterleave(dataCodewords, version, eccLevel);
        if (allCodewords.length != rawCodewords) throw new IllegalStateException("codeword count");

        QrCode best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            QrCode q = new QrCode(version, eccLevel, allCodewords, m);
            int p = q.penalty();
            if (p < bestPenalty) {
                bestPenalty = p;
                best = q;
            }
        }
        return best;
    }


    public boolean getModule(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && modules[y][x];
    }


    public int[] toArgb(int scale, int quietZone, int darkColor, int lightColor) {
        int dim = (size + quietZone * 2) * scale;
        int[] pixels = new int[dim * dim];
        for (int y = 0; y < dim; y++) {
            int sy = y / scale - quietZone;
            for (int x = 0; x < dim; x++) {
                int sx = x / scale - quietZone;
                boolean dark = sx >= 0 && sx < size && sy >= 0 && sy < size && modules[sy][sx];
                pixels[y * dim + x] = dark ? darkColor : lightColor;
            }
        }
        return pixels;
    }


    public int bitmapDimension(int scale, int quietZone) {
        return (size + quietZone * 2) * scale;
    }


    private QrCode(int version, int eccLevel, byte[] allCodewords, int mask) {
        this.version = version;
        this.eccLevel = eccLevel;
        this.mask = mask;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];

        drawFunctionPatterns();
        drawCodewords(allCodewords);
        applyMask(mask);
        drawFormatBits(mask);
    }


    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunctionModule(6, i, i % 2 == 0);
            setFunctionModule(i, 6, i % 2 == 0);
        }
        drawFinderPattern(3, 3);
        drawFinderPattern(size - 4, 3);
        drawFinderPattern(3, size - 4);

        int[] pos = getAlignmentPatternPositions(version);
        int numAlign = pos.length;
        for (int i = 0; i < numAlign; i++) {
            for (int j = 0; j < numAlign; j++) {
                boolean corner = (i == 0 && j == 0) || (i == 0 && j == numAlign - 1)
                        || (i == numAlign - 1 && j == 0);
                if (!corner) drawAlignmentPattern(pos[i], pos[j]);
            }
        }
        drawFormatBits(0);
        drawVersion();
    }

    private void drawFinderPattern(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) {
                    setFunctionModule(xx, yy, dist != 2 && dist != 4);
                }
            }
        }
    }

    private void drawAlignmentPattern(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunctionModule(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void drawFormatBits(int mask) {
        int data = ECL_FORMAT_BITS[eccLevel] << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setFunctionModule(8, i, getBit(bits, i));
        setFunctionModule(8, 7, getBit(bits, 6));
        setFunctionModule(8, 8, getBit(bits, 7));
        setFunctionModule(7, 8, getBit(bits, 8));
        for (int i = 9; i < 15; i++) setFunctionModule(14 - i, 8, getBit(bits, i));

        for (int i = 0; i < 8; i++) setFunctionModule(size - 1 - i, 8, getBit(bits, i));
        for (int i = 8; i < 15; i++) setFunctionModule(8, size - 15 + i, getBit(bits, i));
        setFunctionModule(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean bit = getBit(bits, i);
            int a = size - 11 + i % 3;
            int b = i / 3;
            setFunctionModule(a, b, bit);
            setFunctionModule(b, a, bit);
        }
    }

    private void setFunctionModule(int x, int y, boolean isDark) {
        modules[y][x] = isDark;
        isFunction[y][x] = true;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (isFunction[y][x]) continue;
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    case 7: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                    default: throw new IllegalArgumentException("mask");
                }
                if (invert) modules[y][x] = !modules[y][x];
            }
        }
    }


    private int penalty() {
        int result = 0;
        for (int y = 0; y < size; y++) {
            int run = 1;
            for (int x = 1; x < size; x++) {
                if (modules[y][x] == modules[y][x - 1]) {
                    run++;
                    if (run == 5) result += 3;
                    else if (run > 5) result++;
                } else run = 1;
            }
        }
        for (int x = 0; x < size; x++) {
            int run = 1;
            for (int y = 1; y < size; y++) {
                if (modules[y][x] == modules[y - 1][x]) {
                    run++;
                    if (run == 5) result += 3;
                    else if (run > 5) result++;
                } else run = 1;
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    result += 3;
                }
            }
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x + 10 < size; x++) {
                if (isFinderLike(y, x, true)) result += 40;
            }
        }
        for (int x = 0; x < size; x++) {
            for (int y = 0; y + 10 < size; y++) {
                if (isFinderLike(y, x, false)) result += 40;
            }
        }
        int dark = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) if (modules[y][x]) dark++;
        }
        int total = size * size;
        int k = (int) (Math.abs(dark * 100.0 / total - 50.0) / 5.0);
        result += k * 10;
        return result;
    }

    private boolean isFinderLike(int y, int x, boolean horizontal) {
        int[] p = new int[11];
        for (int i = 0; i < 11; i++) {
            boolean v = horizontal ? modules[y][x + i] : modules[y + i][x];
            p[i] = v ? 1 : 0;
        }
        boolean a = p[0] == 1 && p[1] == 0 && p[2] == 1 && p[3] == 1 && p[4] == 1
                && p[5] == 0 && p[6] == 1 && p[7] == 0 && p[8] == 0 && p[9] == 0 && p[10] == 0;
        boolean b = p[0] == 0 && p[1] == 0 && p[2] == 0 && p[3] == 0 && p[4] == 1
                && p[5] == 0 && p[6] == 1 && p[7] == 1 && p[8] == 1 && p[9] == 0 && p[10] == 1;
        return a || b;
    }


    private static boolean getBit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private static int getNumRawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    private static int getNumDataCodewords(int ver, int ecl) {
        return getNumRawDataModules(ver) / 8
                - ECC_CODEWORDS_PER_BLOCK[ecl][ver] * NUM_ERROR_CORRECTION_BLOCKS[ecl][ver];
    }

    private static int[] getAlignmentPatternPositions(int ver) {
        if (ver == 1) return new int[0];
        int numAlign = ver / 7 + 2;
        int step = (ver == 32) ? 26 : (ver * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = result.length - 1, pos = ver * 4 + 10; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    private static byte[] addEccAndInterleave(byte[] data, int ver, int ecl) {
        int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecl][ver];
        int blockEccLen = ECC_CODEWORDS_PER_BLOCK[ecl][ver];
        int rawCodewords = getNumRawDataModules(ver) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;
        int shortDataLen = shortBlockLen - blockEccLen;

        byte[] rsDiv = reedSolomonDivisor(blockEccLen);
        byte[][] dataBlocks = new byte[numBlocks][];
        byte[][] eccBlocks = new byte[numBlocks][];
        int k = 0;
        for (int i = 0; i < numBlocks; i++) {
            int datLen = shortDataLen + (i < numShortBlocks ? 0 : 1);
            dataBlocks[i] = Arrays.copyOfRange(data, k, k + datLen);
            k += datLen;
            eccBlocks[i] = reedSolomonRemainder(dataBlocks[i], rsDiv);
        }

        byte[] result = new byte[rawCodewords];
        int idx = 0;
        int maxDataLen = shortDataLen + 1;
        for (int j = 0; j < maxDataLen; j++) {
            for (int i = 0; i < numBlocks; i++) {
                if (j < dataBlocks[i].length) result[idx++] = dataBlocks[i][j];
            }
        }
        for (int j = 0; j < blockEccLen; j++) {
            for (int i = 0; i < numBlocks; i++) result[idx++] = eccBlocks[i][j];
        }
        if (idx != rawCodewords) throw new IllegalStateException("interleave mismatch");
        return result;
    }

    private static byte[] reedSolomonDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMultiply(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMultiply(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) gfMultiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int gfMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }


    private static final class BitBuffer {
        private final ArrayList<byte[]> chunks = new ArrayList<byte[]>();
        private int bitLength = 0;

        void appendBits(int val, int len) {
            for (int i = len - 1; i >= 0; i--) {
                int bit = (val >>> i) & 1;
                int byteIndex = bitLength >>> 3;
                while (chunks.size() <= byteIndex) chunks.add(new byte[1]);
                chunks.get(byteIndex)[0] |= (bit << (7 - (bitLength & 7)));
                bitLength++;
            }
        }

        int bitLength() {
            return bitLength;
        }

        byte[] toBytes() {
            byte[] r = new byte[chunks.size()];
            for (int i = 0; i < r.length; i++) r[i] = chunks.get(i)[0];
            return r;
        }
    }
}
