package pl.dzoku.sectorsystem.util;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class NbtSerializer {

    public static byte[] compress(byte[] data) {
        if (data == null || data.length == 0) return data;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 2);
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Compression failed", e);
        }
        return baos.toByteArray();
    }

    public static byte[] decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) return compressed;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(compressed.length * 3);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException("Decompression failed", e);
        }
        return baos.toByteArray();
    }

    public static byte[] combine(byte[]... arrays) {
        int totalSize = 4 * arrays.length;
        for (byte[] arr : arrays) {
            totalSize += (arr != null ? arr.length : 0);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(totalSize);
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(arrays.length);
            for (byte[] arr : arrays) {
                if (arr == null) {
                    dos.writeInt(-1);
                } else {
                    dos.writeInt(arr.length);
                    dos.write(arr);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Combine failed", e);
        }
        return baos.toByteArray();
    }

    public static byte[][] split(byte[] combined) {
        ByteArrayInputStream bais = new ByteArrayInputStream(combined);
        DataInputStream dis = new DataInputStream(bais);
        try {
            int count = dis.readInt();
            byte[][] result = new byte[count][];
            for (int i = 0; i < count; i++) {
                int len = dis.readInt();
                if (len == -1) {
                    result[i] = null;
                } else {
                    result[i] = new byte[len];
                    dis.readFully(result[i]);
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Split failed", e);
        }
    }
}
