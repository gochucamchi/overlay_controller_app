package com.example.overlay_controller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class NotificationCreator {

    private static final String TAG = "NotificationCreator";
    private Context context;
    private String channelId;
    private NotificationManager notificationManager;

    public NotificationCreator(Context context, String channelId) {
        this.context = context;
        this.channelId = channelId;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void createNotificationChannel(String channelName, String channelDescription) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager == null) {
                Log.e(TAG, "NotificationManager is null. Cannot create channel.");
                return;
            }
            // 채널이 이미 생성되었는지 확인 (선택 사항이지만, 중복 생성을 피할 수 있음)
            if (notificationManager.getNotificationChannel(channelId) == null) {
                CharSequence name = channelName;
                int importance = NotificationManager.IMPORTANCE_LOW; // 중요도를 낮게 설정하여 사용자 방해 최소화
                NotificationChannel channel = new NotificationChannel(channelId, name, importance);
                channel.setDescription(channelDescription);
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created: " + channelId);
            } else {
                Log.d(TAG, "Notification channel already exists: " + channelId);
            }
        }
    }

    public Notification buildNotification(String contentTitle,
                                          String contentText,
                                          int smallIconResId,
                                          PendingIntent toggleVisibilityPendingIntent,
                                          String toggleActionText,
                                          PendingIntent stopServicePendingIntent,
                                          String stopActionText) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(smallIconResId) // 알림 아이콘
                .setOngoing(true) // 사용자가 스와이프로 지울 수 없도록 설정
                .setPriority(NotificationCompat.PRIORITY_LOW) // 중요도 낮음
                .setOnlyAlertOnce(true); // 알림 업데이트 시 소리/진동 한 번만

        // 액션 버튼 추가 (null이 아닐 경우에만)
        if (toggleVisibilityPendingIntent != null && toggleActionText != null) {
            builder.addAction(0, toggleActionText, toggleVisibilityPendingIntent); // 아이콘 리소스 ID는 0으로 설정 가능
        }
        if (stopServicePendingIntent != null && stopActionText != null) {
            builder.addAction(0, stopActionText, stopServicePendingIntent);
        }

        return builder.build();
    }

    /**
     * 포그라운드 서비스를 시작하고 알림을 표시/업데이트합니다.
     * @param service 서비스 인스턴스 (startForeground 호출용)
     * @param notificationId 알림 ID
     * @param notification 표시할 알림 객체
     */
    public void startOrUpdateNotification(Service service, int notificationId, Notification notification) {
        if (service == null || notification == null) {
            Log.e(TAG, "Service or Notification is null. Cannot start foreground service.");
            return;
        }
        try {
            service.startForeground(notificationId, notification);
            Log.d(TAG, "startForeground called with notification ID: " + notificationId);
        } catch (Exception e) {
            Log.e(TAG, "Error calling startForeground: " + e.getMessage(), e);
            // Toast.makeText(context, "알림 시작/업데이트 실패", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 알림을 명시적으로 취소합니다. (서비스가 포그라운드에서 해제될 때 필요)
     * @param notificationId 취소할 알림 ID
     */
    public void cancelNotification(int notificationId) {
        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
            Log.d(TAG, "Notification cancelled with ID: " + notificationId);
        }
    }
}
