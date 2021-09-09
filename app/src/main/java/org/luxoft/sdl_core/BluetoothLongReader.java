package org.luxoft.sdl_core;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class BluetoothLongReader {
    private static final String TAG = BluetoothLongReader.class.getSimpleName();

    private ByteBuffer mBuffer;
    private int mMtu = 23;
    private int mMessageOffset = 4;
    private LongReaderCallback mCallback = null;

    public interface LongReaderCallback {
        void OnLongMessageReceived(final byte[] message);
    }

    public void setMtu(final int value) {
        Log.d(TAG, "New MTU value is " + value);
        mMtu = value;
    }

    public void setCallback(LongReaderCallback callback) {
        Log.d(TAG, "New callback is set");
        mCallback = callback;
    }

    public void resetBuffer() {
        Log.d(TAG, "Resetting reader buffer");
        if (mBuffer != null) {
            mBuffer.clear();
            mBuffer = null;
        }
    }

    private int extractFramesCount(byte[] value) {
        byte[] frames = Arrays.copyOfRange(value, 0, mMessageOffset);
        return ByteBuffer.wrap(frames).getInt();
    }

    private byte[] extractMessage(byte[] value) {
        return Arrays.copyOfRange(value, mMessageOffset, value.length);
    }

    public void processReadOperation(byte[] value) {
        int consecutive_frames = extractFramesCount(value); // first 4 bytes always contains amount of frames left

        if (mBuffer == null) {
            Log.d(TAG, "Allocating new buffer size for " + consecutive_frames);
            mBuffer = ByteBuffer.allocate(mMtu * (consecutive_frames + 1));
            Log.d(TAG, "New buffer size is " + mBuffer.capacity());
        }

        byte[] message = extractMessage(value);
        mBuffer.put(message);

        if (consecutive_frames > 0) {
            Log.d(TAG, "Added message of size " + message.length + " and remain " +
                    mBuffer.remaining() + " bytes and " + consecutive_frames + " frames");
        } else {
            Log.d(TAG, "Added last message of size " + message.length + " - passing to handler " + (mBuffer.capacity() - mBuffer.remaining()));
            if (mCallback != null) {
                final byte[] full_message = Arrays.copyOf(mBuffer.array(), mBuffer.capacity() - mBuffer.remaining());
                mCallback.OnLongMessageReceived(full_message);
            }

            resetBuffer();
        }
    }
}
