// OverlayService.java
package com.example.overlay_controller; // 본인의 패키지 이름으로 변경하세요

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service implements WebSocketManager.ConnectionListener {

    private static final String TAG = "OverlayService";
    private static final String NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String SERVER_URL = "ws://gocam.p-e.kr:8079"; // Node.js 서버 주소

    public static final String ACTION_TOGGLE_VISIBILITY = "com.example.overlay_controller.ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_STOP_SERVICE = "com.example.overlay_controller.ACTION_STOP_SERVICE";

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private boolean isOverlayVisible = false;

    private Handler mainHandler; // UI 스레드에서 Toast 등을 실행하기 위함

    private WebSocketManager webSocketManager;
    private InputController inputController;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // 바인딩된 서비스가 아님
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "================ onCreate 시작 ================");
        mainHandler = new Handler(Looper.getMainLooper());

        // WindowManager 및 LayoutInflater 초기화
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (windowManager == null || inflater == null) {
            Log.e(TAG, "WindowManager 또는 LayoutInflater 초기화 실패. 서비스 종료.");
            stopSelf();
            return;
        }

        try {
            overlayView = inflater.inflate(R.layout.controller_layout, null); // 본인의 레이아웃 파일명
        } catch (Exception e) {
            Log.e(TAG, "오버레이 뷰 인플레이트 실패: " + e.getMessage(), e);
            stopSelf();
            return;
        }

        // 오버레이 파라미터 설정
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE; // TYPE_SYSTEM_ALERT와 유사, 더 낮은 API 레벨 호환
        }
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | // 포커스를 가지지 않음 (뒤의 앱과 상호작용 가능)
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | // 오버레이 바깥 터치 이벤트를 뒤로 전달
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // 화면 전체를 기준으로 레이아웃
                PixelFormat.TRANSLUCENT // 투명 배경 허용
        );
        params.gravity = Gravity.CENTER; // 초기 위치 (예: 중앙) - 드래그 기능 추가 시 변경될 수 있음

        // WebSocketManager 및 InputController 초기화
        webSocketManager = new WebSocketManager();
        webSocketManager.setConnectionListener(this); // 연결 상태 콜백 리스너 설정
        inputController = new InputController(webSocketManager /*, this*/); // Context가 필요하면 this 전달

        showOverlay(); // 오버레이 UI 표시
        setupControllerButtons(); // 컨트롤러 버튼 리스너 설정

        // 서비스 시작 시 WebSocket 연결 시도
        Log.d(TAG, "onCreate: WebSocket 연결 시도 호출");
        webSocketManager.connect(SERVER_URL);

        Log.i(TAG, "================ onCreate 종료 (성공적) ================");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand() 호출됨 - Action: " + (intent != null ? intent.getAction() : "null intent"));

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "받은 Action: " + action);
            switch (action) {
                case ACTION_TOGGLE_VISIBILITY:
                    Log.d(TAG, "ACTION_TOGGLE_VISIBILITY 수신");
                    if (isOverlayVisible) {
                        hideOverlay();
                    } else {
                        showOverlay();
                    }
                    break;
                case ACTION_STOP_SERVICE:
                    Log.i(TAG, "ACTION_STOP_SERVICE 수신됨. 서비스 종료 시도.");
                    stopSelf(); // 서비스 종료
                    return START_NOT_STICKY; // 종료 시 재시작 안 함
            }
        } else {
            // 서비스가 (예: 시스템에 의해) 재시작되었으나 인텐트가 없는 경우
            // 연결이 끊겨있을 수 있으므로 다시 연결 시도
            if (webSocketManager != null && !webSocketManager.isConnected()) {
                Log.d(TAG, "onStartCommand (null intent): 서버 미연결 상태, 재연결 시도");
                webSocketManager.connect(SERVER_URL);
            }
        }

        startServiceNotification(); // 알림 상태 업데이트 또는 최초 시작
        return START_STICKY; // 서비스가 비정상 종료 시 시스템이 재시작하도록 함
    }

    private void setupControllerButtons() {
        if (overlayView == null) {
            Log.e(TAG, "setupControllerButtons: overlayView가 null입니다.");
            return;
        }
        Log.d(TAG, "setupControllerButtons 호출됨");

        // 실제 버튼 ID로 변경하세요 (예: R.id.button_up)
        Button buttonUp = overlayView.findViewById(R.id.button_up);
        Button buttonDown = overlayView.findViewById(R.id.button_down);
        Button buttonLeft = overlayView.findViewById(R.id.button_left);
        Button buttonRight = overlayView.findViewById(R.id.button_right);
        Button buttonAction = overlayView.findViewById(R.id.button_action); // controller_layout.xml에 정의된 ID

        if (buttonUp != null) buttonUp.setOnClickListener(v -> {
            Log.d(TAG, "UP 버튼 클릭됨");
            inputController.processButtonPress("UP");
        });
        if (buttonDown != null) buttonDown.setOnClickListener(v -> {
            Log.d(TAG, "DOWN 버튼 클릭됨");
            inputController.processButtonPress("DOWN");
        });
        if (buttonLeft != null) buttonLeft.setOnClickListener(v -> {
            Log.d(TAG, "LEFT 버튼 클릭됨");
            inputController.processButtonPress("LEFT");
        });
        if (buttonRight != null) buttonRight.setOnClickListener(v -> {
            Log.d(TAG, "RIGHT 버튼 클릭됨");
            inputController.processButtonPress("RIGHT");
        });
        if (buttonAction != null) buttonAction.setOnClickListener(v -> {
            Log.d(TAG, "ACTION 버튼 클릭됨");
            inputController.processButtonPress("ACTION_A"); // InputController와 약속된 식별자
        });
        Log.i(TAG, "컨트롤러 버튼 리스너 설정 완료 (InputController 사용)");
    }

    private void showOverlay() {
        if (overlayView != null && !isOverlayVisible) {
            // 뷰가 이미 추가되어 있는지 확인 (중복 추가 방지)
            if (overlayView.getParent() == null) {
                try {
                    windowManager.addView(overlayView, params);
                    isOverlayVisible = true;
                    Log.i(TAG, "오버레이 UI 표시됨");
                } catch (Exception e) {
                    Log.e(TAG, "오버레이 UI 추가 실패: " + e.getMessage(), e);
                    // 권한 문제 등이 있을 수 있음
                    mainHandler.post(() -> Toast.makeText(this, "오버레이 표시 실패", Toast.LENGTH_SHORT).show());
                    return; // 실패 시 isOverlayVisible=false 유지
                }
            } else {
                // 이미 추가된 경우 (hide 후 다시 show 하는 경우 등)
                overlayView.setVisibility(View.VISIBLE);
                isOverlayVisible = true;
                Log.i(TAG, "오버레이 UI 다시 표시됨 (Visibility)");
            }
            updateNotification(); // 알림 내용 업데이트 (예: "표시됨" 상태로)
        }
    }

    private void hideOverlay() {
        if (overlayView != null && isOverlayVisible) {
            // WindowManager에 뷰가 실제로 연결되어 있는지 확인 (removeView 호출 전)
            if (overlayView.getWindowToken() != null && overlayView.getParent() != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception e) {
                    Log.e(TAG, "오버레이 UI 제거 실패: " + e.getMessage(), e);
                    // 이 경우는 드물지만, 발생하면 UI가 화면에 남을 수 있음
                }
            } else {
                // removeView를 호출할 수 없는 상태 (예: 이미 제거됨)
                // 또는 View.GONE으로만 처리할 수도 있음
                // overlayView.setVisibility(View.GONE);
            }
            isOverlayVisible = false;
            Log.i(TAG, "오버레이 UI 숨겨짐");
            updateNotification(); // 알림 내용 업데이트 (예: "숨겨짐" 상태로)
        }
    }

    private void startServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
            Log.d(TAG, "포그라운드 서비스 시작 및 알림 표시됨.");
        } catch (Exception e) {
            Log.e(TAG, "startForeground 실패: " + e.getMessage(), e);
            // 안드로이드 12 (S) 이상에서는 포그라운드 서비스 시작 제한이 있을 수 있음
            // (예: 앱이 백그라운드에 있을 때 시작 불가)
            // 또는 안드로이드 14 (U) 에서 FOREGROUND_SERVICE_SPECIAL_USE 권한 필요 등
            mainHandler.post(() -> Toast.makeText(this, "알림 시작 실패", Toast.LENGTH_LONG).show());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "가상 컨트롤러 서비스 채널", // 사용자에게 보여질 채널 이름
                    NotificationManager.IMPORTANCE_LOW // 알림 중요도 (소리 없이, 상태 표시줄에만)
            );
            channel.setDescription("가상 컨트롤러 서비스 알림 채널입니다.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "알림 채널 생성됨.");
            } else {
                Log.e(TAG, "NotificationManager 가져오기 실패 (채널 생성 불가)");
            }
        }
    }

    private Notification buildNotification() {
        // "숨기기/보이기" 액션 PendingIntent
        Intent toggleIntent = new Intent(this, OverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE_VISIBILITY);
        PendingIntent togglePendingIntent = PendingIntent.getService(this, 0, // requestCode 0
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0));

        // "종료" 액션 PendingIntent
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, // requestCode 1
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0));

        String contentText = isOverlayVisible ? "실행 중 - 화면에 표시됨" : "실행 중 - 숨겨짐";
        if (webSocketManager != null && webSocketManager.isConnected()) {
            contentText += " (서버 연결됨)";
        } else {
            contentText += " (서버 미연결)";
        }
        String toggleButtonText = isOverlayVisible ? "숨기기" : "보이기";

        // 알림 아이콘 설정 (mipmap 또는 drawable 리소스 사용)
        // 예시: res/mipmap-anydpi-v26/ic_launcher.xml 또는 res/drawable/ic_notification.png
        // 실제 아이콘 리소스를 프로젝트에 추가해야 합니다. 여기서는 기본 앱 아이콘을 사용합니다.
        int notificationIcon = R.mipmap.ic_launcher; // 실제 프로젝트의 아이콘으로 변경

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("가상 컨트롤러")
                .setContentText(contentText)
                .setSmallIcon(notificationIcon) // 상태 표시줄에 표시될 작은 아이콘
                .addAction(new NotificationCompat.Action(0, toggleButtonText, togglePendingIntent)) // 아이콘 없는 액션
                .addAction(new NotificationCompat.Action(0, "종료", stopPendingIntent))         // 아이콘 없는 액션
                .setOngoing(true) // 사용자가 쉽게 지울 수 없는 진행 중인 알림
                .setPriority(NotificationCompat.PRIORITY_LOW) // 중요도 낮음
                // .setOnlyAlertOnce(true) // 알림 업데이트 시 소리/진동 한번만 (선택 사항)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
            Log.d(TAG, "알림 내용 업데이트됨.");
        } else {
            Log.e(TAG, "NotificationManager 가져오기 실패 (알림 업데이트 불가)");
        }
    }

    // --- WebSocketManager.ConnectionListener 구현부 ---
    @Override
    public void onConnected() {
        Log.i(TAG, "ConnectionListener: WebSocket 연결 성공!");
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 성공", Toast.LENGTH_SHORT).show());
        updateNotification(); // 알림에 연결 상태 반영
    }

    @Override
    public void onMessageReceived(String message) {
        Log.i(TAG, "ConnectionListener: 서버로부터 메시지 수신: " + message);
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버: " + message, Toast.LENGTH_SHORT).show());
        // 필요 시 여기서 추가 로직 처리
    }

    @Override
    public void onDisconnected(String reason) {
        Log.i(TAG, "ConnectionListener: WebSocket 연결 끊김: " + reason);
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 끊김", Toast.LENGTH_LONG).show());
        updateNotification(); // 알림에 연결 상태 반영

        // 간단한 재연결 로직 (필요하다면)
        // mainHandler.postDelayed(() -> {
        //     if (webSocketManager != null && !webSocketManager.isConnected()) {
        //         Log.d(TAG, "onDisconnected: 서버 재연결 시도...");
        //         webSocketManager.connect(SERVER_URL);
        //     }
        // }, 5000); // 5초 후 재시도
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "ConnectionListener: WebSocket 오류: " + error);
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 오류: " + error, Toast.LENGTH_LONG).show());
        updateNotification(); // 알림에 연결 상태 반영
    }
    // --- WebSocketManager.ConnectionListener 구현부 끝 ---

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "================ onDestroy 시작 ================");

        hideOverlay(); // 오버레이 뷰 제거
        if (overlayView != null) {
            overlayView = null;
        }

        // WebSocketManager 정리
        if (webSocketManager != null) {
            Log.d(TAG, "onDestroy: WebSocketManager 정리 및 연결 해제 호출");
            webSocketManager.disconnect();
            webSocketManager.cleanup();
            webSocketManager = null;
        }

        if (inputController != null) {
            inputController = null;
        }

        // 포그라운드 서비스 상태 해제 및 알림 제거
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24 (Nougat) 이상
            stopForeground(STOP_FOREGROUND_REMOVE); // 알림 제거 포함
        } else {
            stopForeground(true); // 알림 제거 포함 (API 24 미만)
        }

        // NotificationManager를 통해 명시적으로 알림 취소 (추가적인 방어 코드)
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.d(TAG, "알림 명시적 취소됨 (ID: " + NOTIFICATION_ID + ")");
        }

        mainHandler.removeCallbacksAndMessages(null); // 핸들러에 예약된 작업들 제거

        Log.i(TAG, "================ onDestroy 종료 ================");
    }
}
