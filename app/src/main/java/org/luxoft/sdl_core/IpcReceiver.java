package org.luxoft.sdl_core;

public interface IpcReceiver {
        void Connect(OnConnectCallback callback);
        void Disconnect();
        void Read(WriteMessageCallback callback);
}
