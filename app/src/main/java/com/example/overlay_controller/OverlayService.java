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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service implements SocketIOManager.ConnectionListener {

    private static final String TAG = "OverlayService";
    private static final String NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String SERVER_URL = "http://gocam.p-e.kr:8079";

    public static final String ACTION_TOGGLE_VISIBILITY = "com.example.overlay_controller.ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_STOP_SERVICE = "com.example.overlay_controller.ACTION_STOP_SERVICE";

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private boolean isOverlayVisible = false;

    private Handler mainHandler;
    private SocketIOManager socketIOManager;

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

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (windowManager == null || inflater == null) {
            Log.e(TAG, "WindowManager 또는 LayoutInflater 초기화 실패. 서비스 종료.");
            stopSelf(); // onCreate에서 실패 시 스스로 종료
            return;
        }

        try {
            overlayView = inflater.inflate(R.layout.controller_layout, null);
        } catch (Exception e) {
            Log.e(TAG, "오버레이 뷰 인플레이트 실패: " + e.getMessage(), e);
            stopSelf(); // onCreate에서 실패 시 스스로 종료
            return;
        }

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        socketIOManager = new SocketIOManager();
        socketIOManager.setConnectionListener(this);

        showOverlay();
        setupControllerButtons();

        Log.d(TAG, "onCreate: Socket.IO 연결 시도 호출");
        if (socketIOManager != null) {
            socketIOManager.connect(SERVER_URL);
        }

        Log.i(TAG, "================ onCreate 종료 (성공적) ================");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand() 호출됨 - Action: " + (intent != null ? intent.getAction() : "null intent") + ", Flags: " + flags + ", StartId: " + startId);

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
                    // 토글 후에도 알림은 계속 유지되어야 하므로 포그라운드 서비스 상태 유지
                    startServiceNotification();
                    break;
                case ACTION_STOP_SERVICE:
                    Log.i(TAG, "ACTION_STOP_SERVICE 수신됨. 서비스 종료 절차 시작.");
                    customStopService();
                    // 서비스 종료를 요청했으므로, 시스템에 의해 재시작되지 않도록 함
                    return START_NOT_STICKY;
                default:
                    // 알 수 없는 액션이지만, 서비스는 계속 실행되어야 할 수 있음
                    Log.w(TAG, "알 수 없는 Action 수신: " + action);
                    startServiceNotification(); // 기본적으로 포그라운드 유지
                    break;
            }
        } else {
            // Intent가 null인 경우 (예: 서비스가 시스템에 의해 비정상 종료 후 재시작될 때)
            Log.d(TAG, "onStartCommand: Intent가 null이거나 Action이 없습니다. 서비스 재시작 시나리오일 수 있습니다.");
            // 이 경우, 사용자가 명시적으로 다시 시작하지 않는 한 자동 연결을 시도하지 않을 수 있음.
            // 또는 마지막 상태를 복원하려는 로직이 필요할 수 있음.
            // if (socketIOManager != null && !socketIOManager.isConnected()) {
            //     Log.d(TAG, "onStartCommand (null intent): 서버 미연결 상태, 재연결 시도 (주석 처리됨)");
            //     // socketIOManager.connect(SERVER_URL);
            // }
            // 서비스가 재시작되었으므로 포그라운드 상태는 유지해야 함
            startServiceNotification();
        }

        // ACTION_STOP_SERVICE가 아닌 모든 경우에는 START_STICKY를 반환하여
        // 서비스가 비정상 종료 시 시스템이 재시작하도록 함
        return START_STICKY;
    }

    private void customStopService() {
        Log.i(TAG, "customStopService() 호출됨.");

        // 1. 오버레이 UI 제거 (hideOverlay는 onDestroy에서도 호출되지만, 선제적으로 수행)
        // hideOverlay(); // onDestroy에서 최종적으로 처리하도록 둘 수 있음

        // 2. SocketIO 연결 해제 (onDestroy에서도 호출되지만, 선제적으로 수행)
        // if (socketIOManager != null) {
        // socketIOManager.cleanup();
        // }

        // 3. 포그라운드 서비스 상태 해제 및 알림 제거
        Log.d(TAG, "포그라운드 서비스 해제 시도...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        Log.d(TAG, "stopForeground() 호출 완료.");

        // 4. 서비스 종료 요청
        Log.d(TAG, "stopSelf() 호출 시도...");
        stopSelf(); // 이 호출 후 시스템이 onDestroy()를 호출함
        Log.d(TAG, "stopSelf() 호출 완료.");
    }


    private void setupControllerButtons() {
        if (overlayView == null) {
            Log.e(TAG, "setupControllerButtons: overlayView가 null입니다.");
            return;
        }
        Log.d(TAG, "setupControllerButtons 호출됨");

        Button buttonUp = overlayView.findViewById(R.id.button_up);
        Button buttonDown = overlayView.findViewById(R.id.button_down);
        Button buttonLeft = overlayView.findViewById(R.id.button_left);
        Button buttonRight = overlayView.findViewById(R.id.button_right);
        Button buttonAction = overlayView.findViewById(R.id.button_action);

        if (buttonUp != null) buttonUp.setOnTouchListener((v, event) -> { handleButtonTouchEvent(event, "ARROW_UP"); return true; });
        if (buttonDown != null) buttonDown.setOnTouchListener((v, event) -> { handleButtonTouchEvent(event, "ARROW_DOWN"); return true; });
        if (buttonLeft != null) buttonLeft.setOnTouchListener((v, event) -> { handleButtonTouchEvent(event, "ARROW_LEFT"); return true; });
        if (buttonRight != null) buttonRight.setOnTouchListener((v, event) -> { handleButtonTouchEvent(event, "ARROW_RIGHT"); return true; });
        if (buttonAction != null) buttonAction.setOnTouchListener((v, event) -> { handleButtonTouchEvent(event, "ACTION_A"); return true; });

        Log.i(TAG, "컨트롤러 버튼 터치 리스너 설정 완료 (SocketIOManager 사용)");
    }

    private void handleButtonTouchEvent(MotionEvent event, String keyName) {
        if (socketIOManager == null || !socketIOManager.isConnected()) {
            // Log.w(TAG, "서버에 연결되지 않아 키 이벤트를 전송할 수 없습니다.");
            // mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 안됨", Toast.LENGTH_SHORT).show());
            return;
        }
        String eventType;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: eventType = "KEY_DOWN"; break;
            case MotionEvent.ACTION_UP: eventType = "KEY_UP"; break;
            default: return; // 다른 모션 이벤트는 무시
        }
        Log.d(TAG, keyName + " 버튼 이벤트 (" + eventType + ")");
        socketIOManager.sendKeyEvent(eventType, keyName);
    }

    private void showOverlay() {
        if (overlayView != null && !isOverlayVisible) {
            if (overlayView.getParent() == null) {
                try {
                    windowManager.addView(overlayView, params);
                    isOverlayVisible = true;
                    Log.i(TAG, "오버레이 UI 표시됨");
                } catch (Exception e) {
                    Log.e(TAG, "오버레이 UI 추가 실패: " + e.getMessage(), e);
                    mainHandler.post(() -> Toast.makeText(this, "오버레이 표시 실패", Toast.LENGTH_SHORT).show());
                    return;
                }
            } else {
                overlayView.setVisibility(View.VISIBLE);
                isOverlayVisible = true;
                Log.i(TAG, "오버레이 UI 다시 표시됨 (Visibility)");
            }
            updateNotification(); // 오버레이 상태 변경 시 알림 업데이트
        }
    }

    private void hideOverlay() {
        if (overlayView != null && isOverlayVisible) {
            if (overlayView.getWindowToken() != null && overlayView.getParent() != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception e) {
                    Log.e(TAG, "오버레이 UI 제거 실패: " + e.getMessage(), e);
                }
            }
            isOverlayVisible = false;
            Log.i(TAG, "오버레이 UI 숨겨짐");
            updateNotification(); // 오버레이 상태 변경 시 알림 업데이트
        }
    }

    private void startServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }
        try {
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "포그라운드 서비스 시작/유지 및 알림 표시됨.");
        } catch (Exception e) {
            Log.e(TAG, "startForeground 실패: " + e.getMessage(), e);
            mainHandler.post(() -> Toast.makeText(this, "알림 시작 실패: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "가상 컨트롤러 서비스 채널";
            String description = "가상 컨트롤러 서비스 알림 채널입니다.";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "알림 채널 생성됨.");
            } else {
                Log.e(TAG, "NotificationManager 가져오기 실패 (채널 생성 불가)");
            }
        }
    }

    private Notification buildNotification() {
        Intent toggleIntent = new Intent(this, OverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE_VISIBILITY);
        PendingIntent togglePendingIntent = PendingIntent.getService(
                this,
                0, // requestCode
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1, // requestCode (toggle과 달라야 함)
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String contentText = isOverlayVisible ? "실행 중 - 화면에 표시됨" : "실행 중 - 숨겨짐";
        if (socketIOManager != null && socketIOManager.isConnected()) {
            contentText += " (서버 연결됨)";
        } else {
            contentText += " (서버 미연결)";
        }
        String toggleButtonText = isOverlayVisible ? "숨기기" : "보이기";
        int notificationIcon = R.mipmap.ic_launcher;

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("가상 컨트롤러")
                .setContentText(contentText)
                .setSmallIcon(notificationIcon)
                .addAction(new NotificationCompat.Action(0, toggleButtonText, togglePendingIntent))
                .addAction(new NotificationCompat.Action(0, "종료", stopPendingIntent))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true) // 알림 업데이트 시 소리/진동 한번만
                .build();
    }

    private void updateNotification() {
        // startServiceNotification() 내부에서 buildNotification()을 호출하고 startForeground를 하므로,
        // 이 메소드는 직접 startForeground를 호출할 필요 없이 startServiceNotification()를 호출해도 됨.
        // 또는 NotificationManager.notify()를 직접 사용해도 무방.
        // 여기서는 startServiceNotification()을 통해 일관되게 포그라운드 상태를 관리.
        startServiceNotification();
        Log.d(TAG, "알림 내용 업데이트 요청됨 (startServiceNotification 호출).");
    }

    // --- SocketIOManager.ConnectionListener 구현부 ---
    @Override
    public void onConnected() {
        Log.i(TAG, "ConnectionListener: Socket.IO 연결 성공!");
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 성공 (Socket.IO)", Toast.LENGTH_SHORT).show());
        updateNotification();
    }

    @Override
    public void onDisconnected(String reason) {
        Log.i(TAG, "ConnectionListener: Socket.IO 연결 끊김: " + reason);
        mainHandler.post(() -> Toast.makeText(OverlayService.this, "서버 연결 끊김: " + reason, Toast.LENGTH_LONG).show());
        updateNotification();
        // 자동 재연결 시도 로직 (필요 시 활성화)
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
        super.onDestroy(); // 가장 먼저 호출 권장
        Log.i(TAG, "================ onDestroy 시작 ================");

        // 1. 핸들러의 모든 콜백 및 메시지 제거 (가장 중요할 수 있음)
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            Log.d(TAG, "onDestroy: mainHandler의 모든 콜백 및 메시지 제거됨.");
        }

        // 2. 오버레이 뷰 제거
        hideOverlay(); // WindowManager에서 뷰를 확실히 제거
        if (overlayView != null) {
            // overlayView = null; // hideOverlay 내부에서 isOverlayVisible = false 처리됨. 참조는 여기서 null 처리.
            Log.d(TAG, "onDestroy: overlayView 관련 처리 완료 (hideOverlay 호출).");
        }
        // 실제 뷰 참조를 null로 설정
        overlayView = null;


        // 3. SocketIOManager 정리
        if (socketIOManager != null) {
            Log.d(TAG, "onDestroy: SocketIOManager 정리 및 연결 해제 호출");
            socketIOManager.cleanup();
            socketIOManager = null;
            Log.d(TAG, "onDestroy: socketIOManager 참조 해제됨.");
        }

        // 4. 알림 명시적 취소 (stopForeground(REMOVE)가 이미 처리하지만, 방어적으로)
        // customStopService()에서 이미 stopForeground(REMOVE)를 호출했다면 이 부분은 중복일 수 있으나,
        // onDestroy가 다른 경로로 호출될 가능성을 대비.
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.d(TAG, "onDestroy: 알림 명시적 취소됨 (ID: " + NOTIFICATION_ID + ")");
        }

        Log.i(TAG, "================ onDestroy 종료 ================");
    }
}
