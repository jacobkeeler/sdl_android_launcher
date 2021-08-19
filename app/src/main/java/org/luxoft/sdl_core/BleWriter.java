package org.luxoft.sdl_core;

public interface BleWriter {
    void Connect(OnConnectCallback callback);
    void Disconnect();
    void Write(byte[] rawMessage);
}
