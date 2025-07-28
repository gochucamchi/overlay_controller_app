package com.example.overlay_controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;

// OverlayViewManager.KeyInputListener 인터페이스를 정확히 구현하도록 수정
public class KeyInputHandler implements OverlayViewManager.KeyInputListener {

    private static final String TAG = "KeyInputHandler";
    private final long REPEAT_DELAY_MS = 100;
    private final long INITIAL_REPEAT_DELAY_MS = 500;

    private SocketIOManager socketIOManager;
    private Handler keyRepeatHandler;
    private Runnable keyRepeatRunnable;
    private String currentRepeatingKey = null;

    public KeyInputHandler(SocketIOManager socketIOManager) {
        this.socketIOManager = socketIOManager;
        this.keyRepeatHandler = new Handler(Looper.getMainLooper());
    }

    // <<<< 여기를 수정합니다: 매개변수 순서를 (String keyName, MotionEvent event)로 변경 >>>>
    @Override
    public void onKeyEvent(String keyName, MotionEvent event) { // 매개변수 순서 변경
        if (socketIOManager == null) {
            Log.w(TAG, "SocketIOManager is null. Cannot send key event.");
            return;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d(TAG, keyName + " 버튼 눌림 (KEY_DOWN)");
                socketIOManager.sendKeyEvent("KEY_DOWN", keyName);

                currentRepeatingKey = keyName;
                keyRepeatRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (socketIOManager != null && currentRepeatingKey != null) {
                            if (socketIOManager.isConnected()) {
                                Log.d(TAG, currentRepeatingKey + " 버튼 반복 (KEY_DOWN)");
                                socketIOManager.sendKeyEvent("KEY_DOWN", currentRepeatingKey);
                                keyRepeatHandler.postDelayed(this, REPEAT_DELAY_MS);
                            } else {
                                Log.w(TAG, "Server disconnected during key repeat for: " + currentRepeatingKey + ". Stopping repeat.");
                                stopRepeatingKey();
                            }
                        }
                    }
                };
                keyRepeatHandler.postDelayed(keyRepeatRunnable, INITIAL_REPEAT_DELAY_MS);
                break;

            case MotionEvent.ACTION_UP:
                stopRepeatingKey();
                Log.d(TAG, keyName + " 버튼 떼어짐 (KEY_UP)");
                socketIOManager.sendKeyEvent("KEY_UP", keyName);
                break;

            default:
                break;
        }
    }

    private void stopRepeatingKey() {
        if (keyRepeatRunnable != null) {
            keyRepeatHandler.removeCallbacks(keyRepeatRunnable);
        }
        keyRepeatRunnable = null;
        currentRepeatingKey = null;
    }

    public void cleanup() {
        Log.d(TAG, "Cleaning up KeyInputHandler.");
        stopRepeatingKey();
    }
}
