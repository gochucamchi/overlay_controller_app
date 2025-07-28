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
import android.view.LayoutInflater;
import android.view.MotionEvent; // KeyInputListener 사용 시 필요할 수 있음
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList; // 추가
import java.util.List;    // 추가

public class OverlayService extends Service implements SocketIOManager.ConnectionListener {

    private static final String TAG = "OverlayService";
    private static final String NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel";
    private static final String NOTIFICATION_CHANNEL_NAME = "가상 컨트롤러 서비스 채널";
    private static final String NOTIFICATION_CHANNEL_DESC = "가상 컨트롤러 서비스 알림 채널입니다.";
    private static final int NOTIFICATION_ID = 1;
    private static final String SERVER_URL = "http://gocam.p-e.kr:8079"; // 실제 서버 주소

    public static final String ACTION_TOGGLE_VISIBILITY = "com.example.overlay_controller.ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_STOP_SERVICE = "com.example.overlay_controller.ACTION_STOP_SERVICE";

    private Handler mainHandler;

    // 매니저 클래스 인스턴스
    private WindowManager windowManager;
    private LayoutInflater layoutInflater;
    private OverlayViewManager overlayViewManager;
    private KeyInputHandler keyInputHandler;
    private NotificationCreator notificationCreator;
    private SocketIOManager socketIOManager;
    private ButtonConfigManager buttonConfigManager; // <<<<<< 추가

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "================ onCreate 시작 ================");
        mainHandler = new Handler(Looper.getMainLooper());

        // 시스템 서비스 가져오기
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (windowManager == null || layoutInflater == null) {
            Log.e(TAG, "WindowManager 또는 LayoutInflater 초기화 실패. 서비스 종료.");
            stopSelf();
            return;
        }

        // 0. ButtonConfigManager 초기화 (다른 매니저들보다 먼저 또는 함께)
        buttonConfigManager = new ButtonConfigManager(getApplicationContext()); // <<<<<< 추가

        // --- 테스트용 임시 버튼 설정 추가 (처음 실행 시 또는 필요에 따라) ---
        // if (buttonConfigManager.getAllButtonConfigs().isEmpty()) {
        //     Log.d(TAG, "onCreate: 임시 커스텀 버튼 설정 추가 (테스트용)");
        //     buttonConfigManager.addButtonConfig(new CustomButtonConfig("점프", "SPACE_KEY", 0.7f, 0.75f, 0.25f, 0.18f));
        //     buttonConfigManager.addButtonConfig(new CustomButtonConfig("공격", "LEFT_CLICK", 0.1f, 0.75f, 0.25f, 0.18f));
        //     buttonConfigManager.addButtonConfig(new CustomButtonConfig("스킬1", "Q_KEY", 0.4f, 0.5f, 0.15f, 0.1f));
        // }
        // --- 테스트용 코드 끝 ---


        // 1. SocketIOManager 초기화 및 리스너 설정
        socketIOManager = new SocketIOManager();
        socketIOManager.setConnectionListener(this);

        // 2. KeyInputHandler 초기화 (SocketIOManager 필요)
        // KeyInputHandler가 OverlayViewManager.KeyInputListener를 구현한다고 가정
        keyInputHandler = new KeyInputHandler(socketIOManager);

        // 3. OverlayViewManager 초기화 (Context, WindowManager, LayoutInflater, KeyInputListener 필요)
        // KeyInputHandler가 KeyInputListener를 구현하므로 keyInputHandler를 전달
        overlayViewManager = new OverlayViewManager(this, windowManager, layoutInflater, keyInputHandler);
        if (!overlayViewManager.createOverlayView(R.layout.controller_layout)) { // 실제 정적 레이아웃 ID
            Log.e(TAG, "오버레이 뷰 생성 실패. 서비스 종료 준비.");
            stopSelf();
            return;
        }

        // 4. NotificationCreator 초기화 및 채널 생성
        notificationCreator = new NotificationCreator(this, NOTIFICATION_CHANNEL_ID);
        notificationCreator.createNotificationChannel(NOTIFICATION_CHANNEL_NAME, NOTIFICATION_CHANNEL_DESC);

        // 5. 초기 오버레이 표시 및 커스텀 버튼 로드
        overlayViewManager.showOverlay(); // 오버레이 UI를 먼저 표시
        loadAndDisplayCustomButtons();    // 그 다음 커스텀 버튼들을 로드하여 표시 <<<<<< 추가된 호출

        // 6. 포그라운드 서비스 시작 및 알림 표시
        startServiceNotification(); // 알림 표시

        // 7. 서버 연결 시도
        Log.d(TAG, "onCreate: Socket.IO 연결 시도 호출");
        if (socketIOManager != null) {
            socketIOManager.connect(SERVER_URL);
        }

        Log.i(TAG, "================ onCreate 종료 (성공적) ================");
    }

    // <<<<<< 새로운 메소드 추가 >>>>>>
    private void loadAndDisplayCustomButtons() {
        if (overlayViewManager == null || buttonConfigManager == null) {
            Log.e(TAG, "loadAndDisplayCustomButtons: OverlayViewManager 또는 ButtonConfigManager가 null입니다.");
            return;
        }

        List<CustomButtonConfig> configs = buttonConfigManager.getAllButtonConfigs();
        if (configs != null && !configs.isEmpty()) {
            Log.i(TAG, "로드된 커스텀 버튼 설정 " + configs.size() + "개를 표시합니다.");
            overlayViewManager.displayCustomButtons(configs);
        } else {
            Log.i(TAG, "로드된 커스텀 버튼 설정이 없습니다. 기존 커스텀 버튼을 지웁니다.");
            overlayViewManager.displayCustomButtons(new ArrayList<>()); // 빈 리스트를 전달하여 기존 버튼 제거
        }
    }
    // <<<<<< 새로운 메소드 추가 끝 >>>>>>


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand() 호출됨 - Action: " + (intent != null ? intent.getAction() : "null intent") + ", Flags: " + flags + ", StartId: " + startId);

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "받은 Action: " + action);
            switch (action) {
                case ACTION_TOGGLE_VISIBILITY:
                    Log.d(TAG, "ACTION_TOGGLE_VISIBILITY 수신");
                    if (overlayViewManager != null) {
                        if (overlayViewManager.isOverlayVisible()) {
                            overlayViewManager.hideOverlay();
                        } else {
                            overlayViewManager.showOverlay();
                            // 오버레이가 다시 표시될 때 커스텀 버튼을 다시 로드할 필요는 없음
                            // (OverlayViewManager 내부에서 관리)
                            // 만약 설정이 변경되었을 가능성이 있다면 여기서 loadAndDisplayCustomButtons() 호출 고려
                        }
                        updateNotification(); // 오버레이 상태 변경 후 알림 업데이트
                    }
                    break;
                case ACTION_STOP_SERVICE:
                    Log.i(TAG, "ACTION_STOP_SERVICE 수신됨. 서비스 종료 절차 시작.");
                    customStopService();
                    return START_NOT_STICKY;
                default:
                    Log.w(TAG, "알 수 없는 Action 수신: " + action);
                    updateNotification();
                    break;
            }
        } else {
            Log.d(TAG, "onStartCommand: Intent가 null이거나 Action이 없습니다. 서비스 재시작 시나리오일 수 있습니다.");
            // 서비스 재시작 시 onCreate가 다시 호출되므로, 거기서 오버레이 및 버튼 로드가 처리됨
            // 여기서는 알림만 업데이트하거나, 특정 상태 복구 로직이 필요하다면 추가
            updateNotification();
        }

        return START_STICKY;
    }

    private void customStopService() {
        Log.i(TAG, "customStopService() 호출됨.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        Log.d(TAG, "stopForeground() 호출 완료.");
        stopSelf();
        Log.d(TAG, "stopSelf() 호출 완료.");
    }

    private void startServiceNotification() {
        if (notificationCreator == null) {
            Log.e(TAG, "NotificationCreator is not initialized.");
            return;
        }
        PendingIntent togglePendingIntent = createTogglePendingIntent();
        PendingIntent stopPendingIntent = createStopPendingIntent();

        boolean isVisible = (overlayViewManager != null) && overlayViewManager.isOverlayVisible();
        boolean isConnected = (socketIOManager != null) && socketIOManager.isConnected();

        String contentText = (isVisible ? "표시됨" : "숨겨짐");
        contentText += isConnected ? " (서버 연결됨)" : " (서버 미연결)";
        String toggleButtonText = isVisible ? "숨기기" : "보이기";

        Notification notification = notificationCreator.buildNotification(
                "가상 컨트롤러", contentText, R.mipmap.ic_launcher,
                togglePendingIntent, toggleButtonText, stopPendingIntent, "종료"
        );
        notificationCreator.startOrUpdateNotification(this, NOTIFICATION_ID, notification);
        Log.d(TAG, "포그라운드 서비스 시작/유지 및 알림 표시됨 (startServiceNotification).");
    }

    private PendingIntent createTogglePendingIntent() {
        Intent toggleIntent = new Intent(this, OverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE_VISIBILITY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, 0, toggleIntent, flags);
    }

    private PendingIntent createStopPendingIntent() {
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, 1, stopIntent, flags);
    }

    private void updateNotification() {
        startServiceNotification();
        Log.d(TAG, "알림 내용 업데이트 요청됨 (updateNotification 호출 -> startServiceNotification).");
    }

    // --- SocketIOManager.ConnectionListener 구현부 ---
    @Override
    public void onConnected() {
        Log.i(TAG, "ConnectionListener: Socket.IO 연결 성공!");
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 성공", Toast.LENGTH_SHORT).show());
        updateNotification();
    }

    @Override
    public void onDisconnected(String reason) {
        Log.i(TAG, "ConnectionListener: Socket.IO 연결 끊김: " + reason);
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 끊김: " + reason, Toast.LENGTH_LONG).show());
        updateNotification();
        // 자동 재연결 시도 로직 (필요시 활성화)
        // mainHandler.postDelayed(() -> {
        //     if (socketIOManager != null && !socketIOManager.isConnected()) {
        //         Log.d(TAG, "onDisconnected: 서버 재연결 시도...");
        //         socketIOManager.connect(SERVER_URL);
        //     }
        // }, 5000); // 5초 후 재시도
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "ConnectionListener: Socket.IO 오류: " + error);
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 오류: " + error, Toast.LENGTH_LONG).show());
        updateNotification();
    }
    // --- SocketIOManager.ConnectionListener 구현부 끝 ---

    @Override
    public void onDestroy() {
        Log.i(TAG, "================ onDestroy 시작 ================");
        super.onDestroy();

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            Log.d(TAG, "onDestroy: mainHandler의 모든 콜백 및 메시지 제거됨.");
        }
        if (overlayViewManager != null) {
            overlayViewManager.cleanup();
            Log.d(TAG, "onDestroy: OverlayViewManager 정리 완료.");
        }
        if (keyInputHandler != null) {
            keyInputHandler.cleanup();
            Log.d(TAG, "onDestroy: KeyInputHandler 정리 완료.");
        }
        if (socketIOManager != null) {
            socketIOManager.cleanup();
            Log.d(TAG, "onDestroy: SocketIOManager 정리 완료.");
        }
        // NotificationCreator의 명시적 정리는 필요에 따라.
        // buttonConfigManager는 별도의 close/cleanup 메소드가 없다면 참조 해제만으로 충분.

        overlayViewManager = null;
        keyInputHandler = null;
        socketIOManager = null;
        notificationCreator = null;
        windowManager = null;
        layoutInflater = null;
        buttonConfigManager = null; // <<<<<< 추가된 참조 해제
        Log.i(TAG, "================ onDestroy 종료 ================");
    }
}
