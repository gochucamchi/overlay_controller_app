// /com/example/overlay_controller/KeyInputHandler.java
package com.example.overlay_controller;

import android.util.Log;

public class KeyInputHandler {

    private static final String TAG = "KeyInputHandler";
    private final SocketIOManager socketIOManager;

    public KeyInputHandler(SocketIOManager socketIOManager) {
        this.socketIOManager = socketIOManager;
    }

    /**
     * 키 이벤트를 처리합니다.
     * @param keyName 서버로 전송될 키 이름 (예: "W", "SPACE")
     * @param action 이벤트 종류 ("KEY_DOWN" 또는 "KEY_UP")
     */
    public void onKeyEvent(String keyName, String action) {
        if (socketIOManager == null || !socketIOManager.isConnected()) {
            Log.w(TAG, "SocketIOManager is null or not connected. Cannot send key event for " + keyName);
            return;
        }

        // action 문자열에 따라 분기하여 서버로 이벤트 전송
        if ("KEY_DOWN".equals(action)) {
            Log.d(TAG, keyName + " 버튼 눌림 (KEY_DOWN)");
            socketIOManager.sendKeyEvent("KEY_DOWN", keyName);
        } else if ("KEY_UP".equals(action)) {
            Log.d(TAG, keyName + " 버튼 떼어짐 (KEY_UP)");
            socketIOManager.sendKeyEvent("KEY_UP", keyName);
        }
    }

    public void cleanup() {
        Log.d(TAG, "Cleaning up KeyInputHandler.");
        // 특별히 정리할 리소스가 현재는 없음
    }
}