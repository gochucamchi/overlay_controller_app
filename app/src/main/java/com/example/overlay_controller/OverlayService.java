// /com/example/overlay_controller/OverlayService.java
package com.example.overlay_controller;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OverlayService extends Service implements OverlayViewManager.OverlayInteractionListener, SocketIOManager.ConnectionListener {

    private static final String TAG = "OverlayService";
    private static final String NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel";
    public static final String ACTION_START = "com.example.overlay_controller.ACTION_START";
    public static final String ACTION_STOP = "com.example.overlay_controller.ACTION_STOP";
    public static final String ACTION_SHOW = "com.example.overlay_controller.ACTION_SHOW";
    public static final String ACTION_HIDE = "com.example.overlay_controller.ACTION_HIDE";

    public static KeyCaptureActivity.KeyCaptureListener keyCaptureListener;

    private Handler mainHandler;
    private WindowManager windowManager;
    private OverlayViewManager overlayViewManager;
    private KeyInputHandler keyInputHandler;
    private NotificationCreator notificationCreator;
    private SocketIOManager socketIOManager;
    private ButtonConfigManager buttonConfigManager;
    private List<CustomButtonConfig> currentConfigs;
    private EditMode currentEditMode = EditMode.NORMAL;
    private boolean isOverlayShown = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        buttonConfigManager = new ButtonConfigManager(this);
        socketIOManager = new SocketIOManager();
        socketIOManager.setConnectionListener(this);
        keyInputHandler = new KeyInputHandler(socketIOManager);
        overlayViewManager = new OverlayViewManager(this, windowManager, keyInputHandler, this);
        notificationCreator = new NotificationCreator(this, NOTIFICATION_CHANNEL_ID);
        notificationCreator.createNotificationChannel("가상 컨트롤러 서비스", "서비스 실행 알림");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START:
                    startForegroundService();
                    socketIOManager.connect("http://gocam.p-e.kr:8079");
                    showButtons();
                    break;
                case ACTION_STOP:
                    stopService();
                    break;
                case ACTION_SHOW:
                    showButtons();
                    break;
                case ACTION_HIDE:
                    if (overlayViewManager != null) {
                        overlayViewManager.hideOverlay();
                        isOverlayShown = false;
                        updateNotification();
                    }
                    break;
            }
        }
        return START_STICKY;
    }

    private void startForegroundService() {
        startForeground(1, buildNotification());
    }

    private void showButtons() {
        if (overlayViewManager != null) {
            currentConfigs = buttonConfigManager.getAllButtonConfigs();
            overlayViewManager.displayCustomButtons(currentConfigs);
            isOverlayShown = true;
            updateNotification();
        }
    }

    private void stopService() {
        if (socketIOManager != null) socketIOManager.disconnect();
        if (overlayViewManager != null) overlayViewManager.cleanup();
        stopForeground(true);
        stopSelf();
    }

    private void updateNotification() {
        if (notificationCreator != null) {
            notificationCreator.startOrUpdateNotification(this, 1, buildNotification());
        }
    }

    private Notification buildNotification() {
        String contentText = isOverlayShown ? "표시됨" : "숨겨짐";
        String toggleActionText = isOverlayShown ? "숨기기" : "보이기";
        Intent toggleIntent = new Intent(this, OverlayService.class);
        toggleIntent.setAction(isOverlayShown ? ACTION_HIDE : ACTION_SHOW);
        PendingIntent togglePI = PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPI = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return notificationCreator.buildNotification("가상 컨트롤러", contentText, R.mipmap.ic_launcher, togglePI, toggleActionText, stopPI, "종료");
    }

    @Override
    public void onEditModeChanged(EditMode newMode) {
        this.currentEditMode = newMode;
        if (overlayViewManager != null) {
            overlayViewManager.updateButtonVisuals();
        }
    }

    @Override
    public void onButtonDeleted(CustomButtonConfig configToDelete) {
        if (buttonConfigManager != null) {
            buttonConfigManager.deleteButtonConfigByLabel(configToDelete.getLabel());
        }
        showButtons();
    }

    @Override
    public EditMode getCurrentEditMode() {
        return currentEditMode;
    }

    @Override
    public void onButtonUpdated(CustomButtonConfig updatedConfig) {
        // MainActivity에서 SharedPreferences를 통해 직접 수정하므로, 여기서는 새로고침만 해주면 됨
        showButtons();
    }

    @Override
    public void onNewButtonRequested() {
        if (buttonConfigManager == null) return;

        // 새 버튼의 고유한 이름 생성
        String newButtonLabel = "새 버튼 " + (currentConfigs.size() + 1);

        // 새 버튼 설정 객체 생성
        CustomButtonConfig newConfig = new CustomButtonConfig(
                newButtonLabel,    // 버튼에 표시될 이름
                "미지정",          // 전송될 키 값 (초기에는 없음)
                0.4f,              // 초기 x 위치 (화면 중앙)
                0.4f,              // 초기 y 위치 (화면 중앙)
                0.06f,              // 너비 (화면 너비의 10%)
                0.06f               // 높이 (화면 높이의 10%)
        );

        // 새 설정을 저장하고 화면에 다시 표시
        buttonConfigManager.addButtonConfig(newConfig);
        showButtons();
    }

    @Override
    public void onKeyAssignRequested(CustomButtonConfig config) {
        KeyCaptureActivity.finishActivity();
        keyCaptureListener = (originalButtonLabel, newKey) -> {
            currentConfigs = buttonConfigManager.getAllButtonConfigs();
            for (int i = 0; i < currentConfigs.size(); i++) {
                if (Objects.equals(currentConfigs.get(i).getLabel(), originalButtonLabel)) {
                    CustomButtonConfig currentConfig = currentConfigs.get(i);
                    currentConfig.setLabel(newKey);
                    currentConfig.setKeyName(newKey);
                    buttonConfigManager.updateButtonConfig(i, currentConfig);
                    showButtons();
                    break;
                }
            }
        };

        Intent intent = new Intent(this, KeyCaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.putExtra(KeyCaptureActivity.EXTRA_BUTTON_LABEL, config.getLabel());
        startActivity(intent);
    }

    @Override public void onConnected() { mainHandler.post(() -> Toast.makeText(this, "서버 연결 성공", Toast.LENGTH_SHORT).show()); }
    @Override public void onDisconnected(String reason) { mainHandler.post(() -> Toast.makeText(this, "서버 연결 끊김: " + reason, Toast.LENGTH_LONG).show()); }
    @Override public void onError(String error) { mainHandler.post(() -> Toast.makeText(this, "서버 오류: " + error, Toast.LENGTH_LONG).show()); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService();
    }
}