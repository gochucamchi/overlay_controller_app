package com.example.overlay_controller;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout; // 간단한 레이아웃으로 FrameLayout 사용

import androidx.annotation.Nullable;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // 다른 컴포넌트와 바인딩을 사용하지 않을 경우 null 반환
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        // 오버레이에 표시될 뷰 생성 (여기서는 간단한 FrameLayout)
        overlayView = new FrameLayout(this);
        // FrameLayout의 크기와 배경색 설정 (테스트용)
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                200, // 너비 (픽셀 단위)
                200  // 높이 (픽셀 단위)
        );
        overlayView.setLayoutParams(layoutParams);
        overlayView.setBackgroundColor(Color.RED); // 빨간색 배경

        // WindowManager.LayoutParams 설정
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android Oreo (API 26) 이상에서는 TYPE_APPLICATION_OVERLAY 사용
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // 이전 버전에서는 TYPE_PHONE 또는 TYPE_SYSTEM_ALERT 등 사용 (상황에 따라 다름)
            // TYPE_PHONE은 더 이상 권장되지 않으며, 일부 기기에서 문제 발생 가능성 있음
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | // 포커스를 받지 않도록 설정 (다른 앱 사용에 방해되지 않도록)
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | // 이 뷰 바깥의 터치 이벤트를 막지 않음
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // 전체 화면을 기준으로 레이아웃
                PixelFormat.TRANSLUCENT); // 반투명 배경 허용

        params.gravity = Gravity.TOP | Gravity.START; // 화면 왼쪽 상단에 위치
        params.x = 0; // x 좌표
        params.y = 0; // y 좌표

        // WindowManager에 뷰 추가
        try {
            if (overlayView.getWindowToken() == null && overlayView.getParent() == null) {
                windowManager.addView(overlayView, params);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 서비스가 시작될 때 호출됨
        // 여기서 오버레이 뷰에 대한 초기화 또는 업데이트 수행 가능
        return START_STICKY; // 서비스가 시스템에 의해 종료될 경우 재시작
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 서비스가 종료될 때 오버레이 뷰 제거
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            overlayView = null;
        }
    }
}
