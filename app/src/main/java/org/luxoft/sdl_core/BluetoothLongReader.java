package org.luxoft.sdl_core;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.DataFormatException;

public class BluetoothLongReader {
    private static final String TAG = BluetoothLongReader.class.getSimpleName();

    private ByteBuffer mBuffer;
    private int mMtu = 23;
    private final int mMessageOffset = 5;
    private LongReaderCallback mCallback = null;

    private class MessageInfo {
        public final int frames_count;
        public final boolean is_compressed;

        public MessageInfo(int frames_count_, boolean compressed_) {
            frames_count = frames_count_;
            is_compressed = compressed_;
        }
    }

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

    private MessageInfo extractMessageInfo(byte[] value) {
        byte[] frames = Arrays.copyOfRange(value, 0, mMessageOffset);
        ByteBuffer header_bytes = ByteBuffer.wrap(frames);
        final int frames_count = header_bytes.getInt();
        final boolean is_compressed = header_bytes.get() == 1;
        return new MessageInfo(frames_count, is_compressed);
    }

    private byte[] extractMessage(byte[] value) {
        return Arrays.copyOfRange(value, mMessageOffset, value.length);
    }

    public void processReadOperation(byte[] value) {
        MessageInfo m_info = extractMessageInfo(value);

        if (mBuffer == null) {
            Log.d(TAG, "Allocating new buffer size for " + m_info.frames_count);
            mBuffer = ByteBuffer.allocate(mMtu * (m_info.frames_count + 1));
            Log.d(TAG, "New buffer size is " + mBuffer.capacity());
        }

        byte[] message = extractMessage(value);
        mBuffer.put(message);

        if (m_info.frames_count > 0) {
            Log.d(TAG, "Added message of size " + message.length + " and remain " +
                    mBuffer.remaining() + " bytes and " + m_info.frames_count + " frames " +
                    " compression :" + m_info.is_compressed);
        } else {
            final int msg_size = mBuffer.capacity() - mBuffer.remaining();
            Log.d(TAG, "Added last message of size " + message.length +
                    " - passing to handler " + msg_size +
                    " compression :" + m_info.is_compressed);
            if (mCallback != null) {
                byte[] full_message = getFullMessage(m_info.is_compressed, msg_size);
                mCallback.OnLongMessageReceived(full_message);
            }

            resetBuffer();
        }
    }

    private byte[] getFullMessage(final boolean is_compressed, final int msg_size) {
        final byte[] full_message = Arrays.copyOf(mBuffer.array(), msg_size);

        if(is_compressed) {
            try {
                return CompressionUtil.decompress(full_message);
            } catch (IOException | DataFormatException e) {
                e.printStackTrace();
            }
        }

        return full_message;
    }
}
