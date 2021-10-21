package org.luxoft.sdl_core;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class CompressionUtil {
    private static final String TAG = CompressionUtil.class.getSimpleName();

    public static byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);

        deflater.finish();
        byte[] buffer = new byte[AndroidSettings.getIntValue(AndroidSettings.IniParams.BufferSize)];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            if(count > 0) {
                outputStream.write(buffer, 0, count);
            } else {
                break;
            }
        }
        deflater.end();
        outputStream.close();
        byte[] output = outputStream.toByteArray();

        Log.d(TAG, "Original: " + data.length + " Compressed: " + output.length);
        return output;
    }

    public static byte[] decompress(byte[] data) throws IOException, DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[AndroidSettings.getIntValue(AndroidSettings.IniParams.BufferSize)];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if(count > 0) {
                outputStream.write(buffer, 0, count);
            } else {
                break;
            }
        }
        inflater.end();
        outputStream.close();
        byte[] output = outputStream.toByteArray();

        Log.d(TAG, "Original: " + data.length + " Decompressed: " + output.length);
        return output;
    }
}
