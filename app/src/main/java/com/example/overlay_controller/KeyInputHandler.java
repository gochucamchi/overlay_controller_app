package com.example.overlay_controller;

import android.util.Log;
import android.view.MotionEvent;

public class KeyInputHandler implements OverlayViewManager.KeyInputListener {

    private static final String TAG = "KeyInputHandler";
    private SocketIOManager socketIOManager;

    public KeyInputHandler(SocketIOManager socketIOManager) {
        this.socketIOManager = socketIOManager;
    }

    @Override
    public void onKeyEvent(String keyName, MotionEvent event) {
        if (socketIOManager == null || !socketIOManager.isConnected()) {
            Log.w(TAG, "SocketIOManager is null or not connected. Cannot send key event.");
            return;
        }

        // event.getAction() 대신 event.getActionMasked()를 사용하도록 수정합니다.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d(TAG, keyName + " 버튼 눌림 (KEY_DOWN)");
                socketIOManager.sendKeyEvent("KEY_DOWN", keyName);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                Log.d(TAG, keyName + " 버튼 떼어짐 (KEY_UP)");
                socketIOManager.sendKeyEvent("KEY_UP", keyName);
                break;

            default:
                break;
        }
    }

    public void cleanup() {
        Log.d(TAG, "Cleaning up KeyInputHandler.");
    }
}