package org.luxoft.sdl_core;

public interface IpcSender {
    void Connect(OnConnectCallback callback);
    void Disconnect();
    void Write(byte[] rawMessage);
}
