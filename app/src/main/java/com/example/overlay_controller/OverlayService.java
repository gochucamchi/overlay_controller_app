package com.example.overlay_controller;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service implements SocketIOManager.ConnectionListener, OverlayViewManager.OverlayInteractionListener {

    private static final String TAG = "OverlayService";
    private static final String NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel";
    private static final String NOTIFICATION_CHANNEL_NAME = "가상 컨트롤러 서비스 채널";
    private static final String NOTIFICATION_CHANNEL_DESC = "가상 컨트롤러 서비스 알림 채널입니다.";
    private static final int NOTIFICATION_ID = 1;
    private static final String SERVER_URL = "http://gocam.p-e.kr:8079";

    public static final String ACTION_TOGGLE_VISIBILITY = "com.example.overlay_controller.ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_STOP_SERVICE = "com.example.overlay_controller.ACTION_STOP_SERVICE";

    private Handler mainHandler;

    private WindowManager windowManager;
    private LayoutInflater layoutInflater;
    private OverlayViewManager overlayViewManager;
    private KeyInputHandler keyInputHandler;
    private NotificationCreator notificationCreator;
    private SocketIOManager socketIOManager;
    private ButtonConfigManager buttonConfigManager;
    private List<CustomButtonConfig> currentConfigs;
    private EditMode currentEditMode = EditMode.NORMAL;
    private BroadcastReceiver keyCaptureReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (windowManager == null || layoutInflater == null) {
            Log.e(TAG, "WindowManager 또는 LayoutInflater 초기화 실패. 서비스 종료.");
            stopSelf();
            return;
        }

        buttonConfigManager = new ButtonConfigManager(getApplicationContext());
        currentConfigs = buttonConfigManager.getAllButtonConfigs();

        socketIOManager = new SocketIOManager();
        socketIOManager.setConnectionListener(this);

        keyInputHandler = new KeyInputHandler(socketIOManager);

        overlayViewManager = new OverlayViewManager(this, windowManager, layoutInflater, keyInputHandler, this);

        notificationCreator = new NotificationCreator(this, NOTIFICATION_CHANNEL_ID);
        notificationCreator.createNotificationChannel(NOTIFICATION_CHANNEL_NAME, NOTIFICATION_CHANNEL_DESC);

        setupKeyCaptureReceiver();

        overlayViewManager.showOverlay();
        loadAndDisplayCustomButtons();
        startServiceNotification();
        if (socketIOManager != null) {
            socketIOManager.connect(SERVER_URL);
        }
    }

    private void setupKeyCaptureReceiver() {
        keyCaptureReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && KeyCaptureActivity.ACTION_KEY_CAPTURED.equals(intent.getAction())) {
                    String capturedKey = intent.getStringExtra(KeyCaptureActivity.EXTRA_CAPTURED_KEY);
                    String buttonLabel = intent.getStringExtra(KeyCaptureActivity.EXTRA_BUTTON_LABEL);

                    if (capturedKey != null && buttonLabel != null) {
                        updateButtonKey(buttonLabel, capturedKey);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(KeyCaptureActivity.ACTION_KEY_CAPTURED);
        ContextCompat.registerReceiver(this, keyCaptureReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void updateButtonKey(String buttonLabel, String newKey) {
        if (currentConfigs == null || buttonConfigManager == null) return;
        for (int i = 0; i < currentConfigs.size(); i++) {
            CustomButtonConfig config = currentConfigs.get(i);
            if (config.getLabel().equals(buttonLabel)) {
                config.setLabel(newKey);
                config.setKeyName(newKey);
                buttonConfigManager.updateButtonConfig(i, config);
                Log.d(TAG, buttonLabel + "의 라벨과 키가 '" + newKey + "' (으)로 변경 및 저장되었습니다.");
                loadAndDisplayCustomButtons();
                return;
            }
        }
    }

    private void loadAndDisplayCustomButtons() {
        if (overlayViewManager == null) return;
        overlayViewManager.displayCustomButtons(currentConfigs != null ? currentConfigs : new ArrayList<>());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_TOGGLE_VISIBILITY:
                    if (overlayViewManager != null) {
                        if (overlayViewManager.isOverlayVisible()) {
                            overlayViewManager.hideOverlay();
                        } else {
                            overlayViewManager.showOverlay();
                        }
                        updateNotification();
                    }
                    break;
                case ACTION_STOP_SERVICE:
                    customStopService();
                    return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    private void customStopService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void startServiceNotification() {
        if (notificationCreator == null) return;
        PendingIntent togglePendingIntent = createTogglePendingIntent();
        PendingIntent stopPendingIntent = createStopPendingIntent();

        boolean isVisible = (overlayViewManager != null) && overlayViewManager.isOverlayVisible();
        boolean isConnected = (socketIOManager != null) && socketIOManager.isConnected();

        String contentText = (isVisible ? "표시됨" : "숨겨짐") + (isConnected ? " (서버 연결됨)" : " (서버 미연결)");
        String toggleButtonText = isVisible ? "숨기기" : "보이기";

        Notification notification = notificationCreator.buildNotification("가상 컨트롤러", contentText, R.mipmap.ic_launcher,
                togglePendingIntent, toggleButtonText, stopPendingIntent, "종료");
        notificationCreator.startOrUpdateNotification(this, NOTIFICATION_ID, notification);
    }

    private PendingIntent createTogglePendingIntent() {
        Intent toggleIntent = new Intent(this, OverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE_VISIBILITY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, 0, toggleIntent, flags);
    }

    private PendingIntent createStopPendingIntent() {
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, 1, stopIntent, flags);
    }

    private void updateNotification() {
        startServiceNotification();
    }

    @Override
    public void onEditModeChanged(EditMode newMode) {
        this.currentEditMode = newMode;
        Log.d(TAG, "Edit mode changed to: " + newMode.name());
        if (overlayViewManager != null) {
            overlayViewManager.updateButtonVisuals(newMode);
        }
    }

    @Override
    public EditMode getCurrentEditMode() {
        return currentEditMode;
    }

    @Override
    public void onButtonUpdated(CustomButtonConfig updatedConfig) {
        if (currentConfigs == null || buttonConfigManager == null) return;
        for (int i = 0; i < currentConfigs.size(); i++) {
            if (currentConfigs.get(i).getLabel().equals(updatedConfig.getLabel())) {
                buttonConfigManager.updateButtonConfig(i, updatedConfig);
                return;
            }
        }
    }

    @Override
    public void onButtonDeleted(CustomButtonConfig configToDelete) {
        if (currentConfigs == null || buttonConfigManager == null) return;
        int targetIndex = -1;
        for (int i = 0; i < currentConfigs.size(); i++) {
            if (currentConfigs.get(i).getLabel().equals(configToDelete.getLabel())) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex != -1) {
            currentConfigs.remove(targetIndex);
            buttonConfigManager.deleteButtonConfig(targetIndex);
        }
    }

    @Override
    public void onNewButtonRequested() {
        if (currentConfigs == null || buttonConfigManager == null) return;
        String newButtonLabel = "Button" + (currentConfigs.size() + 1);
        CustomButtonConfig newConfig = new CustomButtonConfig(
                newButtonLabel, "NEW", 0.4f, 0.4f, 0.2f, 0.1f
        );
        currentConfigs.add(newConfig);
        buttonConfigManager.addButtonConfig(newConfig);
        loadAndDisplayCustomButtons();
    }

    @Override
    public void onKeyAssignRequested(CustomButtonConfig config) {
        Intent intent = new Intent(this, KeyCaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(KeyCaptureActivity.EXTRA_BUTTON_LABEL, config.getLabel());
        startActivity(intent);
    }

    @Override
    public void onConnected() {
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 성공", Toast.LENGTH_SHORT).show());
        updateNotification();
    }

    @Override
    public void onDisconnected(String reason) {
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 끊김: " + reason, Toast.LENGTH_LONG).show());
        updateNotification();
    }

    @Override
    public void onError(String error) {
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 오류: " + error, Toast.LENGTH_LONG).show());
        updateNotification();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (keyCaptureReceiver != null) {
            unregisterReceiver(keyCaptureReceiver);
        }
        if (overlayViewManager != null) {
            overlayViewManager.cleanup();
        }
        if (socketIOManager != null) {
            socketIOManager.cleanup();
        }
        if (keyInputHandler != null) {
            keyInputHandler.cleanup();
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}