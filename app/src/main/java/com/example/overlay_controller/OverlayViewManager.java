package com.example.overlay_controller; // 실제 패키지 이름으로 변경하세요

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button; // 예시로 추가, 실제 사용하는 위젯으로 변경
import android.widget.FrameLayout; // 동적 버튼 추가를 위한 부모 레이아웃 예시
import android.widget.TextView;  // 예시로 추가

import java.util.List;

public class OverlayViewManager {

    private static final String TAG = "OverlayViewManager";

    private Context context;
    private WindowManager windowManager;
    private LayoutInflater layoutInflater;
    private View overlayView; // 정적 레이아웃을 포함한 전체 오버레이 뷰
    private ViewGroup customButtonContainer; // 동적 커스텀 버튼이 추가될 컨테이너
    private WindowManager.LayoutParams params;
    private boolean overlayVisible = false;

    private KeyInputListener keyInputListener; // 키 입력 처리를 위한 리스너

    // KeyInputListener 인터페이스 정의 (KeyInputHandler가 이를 구현하거나, Service가 직접 구현)
    public interface KeyInputListener {
        void onKeyEvent(String keyName, MotionEvent event); // 어떤 키, 어떤 액션인지 전달
    }

    public OverlayViewManager(Context context, WindowManager windowManager, LayoutInflater layoutInflater, KeyInputListener keyInputListener) {
        this.context = context;
        this.windowManager = windowManager;
        this.layoutInflater = layoutInflater;
        this.keyInputListener = keyInputListener;

        // WindowManager.LayoutParams 초기화
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, // 초기 너비 (컨텐츠에 맞춤)
                WindowManager.LayoutParams.WRAP_CONTENT, // 초기 높이 (컨텐츠에 맞춤)
                getOverlayType(),
                // <<<< 핵심 플래그 설정 >>>>
                // 오버레이가 포커스를 갖지 않도록 하여 뒤의 창이 터치 이벤트를 받을 수 있게 함.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT // 배경 투명 처리
        );
        params.gravity = Gravity.TOP | Gravity.START; // 오버레이 초기 위치 (예: 좌측 상단)
        params.x = 0; // 초기 X 좌표
        params.y = 100; // 초기 Y 좌표 (상태 표시줄 아래 등)
    }

    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // TYPE_PHONE 은 API 26부터 Deprecated 되었으나 하위 호환성을 위해 사용될 수 있음
            // TYPE_SYSTEM_ALERT 는 더 많은 권한이 필요할 수 있음
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    /**
     * 정적 레이아웃을 기반으로 오버레이 뷰를 생성합니다.
     * @param staticLayoutResId R.layout.controller_layout 같은 정적 레이아웃 리소스 ID
     * @return 뷰 생성 성공 여부
     */
    public boolean createOverlayView(int staticLayoutResId) {
        if (overlayView != null) {
            Log.w(TAG, "Overlay view already exists.");
            return true; // 이미 생성되어 있다면 성공으로 간주
        }
        try {
            overlayView = layoutInflater.inflate(staticLayoutResId, null);
            if (overlayView == null) {
                Log.e(TAG, "Failed to inflate layout: " + staticLayoutResId);
                return false;
            }

            // 동적으로 커스텀 버튼을 추가할 컨테이너를 찾습니다. (ID는 실제 레이아웃에 맞게)
            // 예를 들어 controller_layout.xml 내에 <FrameLayout android:id="@+id/custom_button_area"/> 같은 것이 있어야 함.
            customButtonContainer = overlayView.findViewById(R.id.custom_button_area
            );
            if (customButtonContainer == null) {
                Log.w(TAG, "Custom button container (e.g., R.id.custom_button_area) not found in the layout. Custom buttons might not be displayed.");
                // 컨테이너가 없더라도 기본 오버레이는 작동할 수 있도록 여기서 false를 반환하지 않을 수 있음.
                // 다만, displayCustomButtons에서 NPE가 발생하지 않도록 주의.
            }

            // (선택 사항) 오버레이 뷰 전체에 대한 터치 리스너 설정 (드래그하여 이동 등)
            // overlayView.setOnTouchListener(new View.OnTouchListener() {
            //     private int initialX;
            //     private int initialY;
            //     private float initialTouchX;
            //     private float initialTouchY;
            //
            //     @Override
            //     public boolean onTouch(View v, MotionEvent event) {
            //         switch (event.getAction()) {
            //             case MotionEvent.ACTION_DOWN:
            //                 initialX = params.x;
            //                 initialY = params.y;
            //                 initialTouchX = event.getRawX();
            //                 initialTouchY = event.getRawY();
            //                 return true; // 이벤트를 소비하여 다른 리스너로 전달되지 않도록 함
            //             case MotionEvent.ACTION_MOVE:
            //                 params.x = initialX + (int) (event.getRawX() - initialTouchX);
            //                 params.y = initialY + (int) (event.getRawY() - initialTouchY);
            //                 if (overlayVisible) { // 뷰가 WindowManager에 추가된 상태에서만 업데이트
            //                     windowManager.updateViewLayout(overlayView, params);
            //                 }
            //                 return true;
            //         }
            //         return false; // 그 외 이벤트는 처리하지 않음
            //     }
            // });


            // params.flags는 생성자에서 이미 FLAG_NOT_FOCUSABLE로 설정되었습니다.
            // 만약 여기서 오버레이의 크기 등에 따라 플래그를 변경해야 한다면 이 부분에서 수정합니다.
            // 예: 만약 특정 조건에서 오버레이가 화면 전체를 덮고 모달처럼 행동해야 한다면,
            // params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN; // (다른 플래그 조합)
            // 하지만 현재 문제 상황에서는 FLAG_NOT_FOCUSABLE이 유지되어야 합니다.

            Log.i(TAG, "Overlay view created successfully with flags: " + params.flags);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error creating overlay view from layout resource " + staticLayoutResId, e);
            overlayView = null; // 실패 시 참조 제거
            return false;
        }
    }

    /**
     * 주어진 버튼 설정 목록에 따라 커스텀 버튼들을 오버레이에 동적으로 추가하고 표시합니다.
     * @param configs 표시할 커스텀 버튼 설정 리스트
     */
    public void displayCustomButtons(List<CustomButtonConfig> configs) {
        if (overlayView == null) {
            Log.e(TAG, "Overlay view is not created. Cannot display custom buttons.");
            return;
        }
        if (customButtonContainer == null) {
            Log.e(TAG, "Custom button container is null. Cannot display custom buttons.");
            return;
        }

        customButtonContainer.removeAllViews(); // 기존 커스텀 버튼들 제거

        if (configs == null || configs.isEmpty()) {
            Log.i(TAG, "No custom buttons to display or configs list is null/empty.");
            // 필요하다면 컨테이너의 visibility를 GONE으로 설정할 수 있습니다.
            // customButtonContainer.setVisibility(View.GONE);
            return;
        }
        // customButtonContainer.setVisibility(View.VISIBLE);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        Log.i(TAG, "Displaying " + configs.size() + " custom buttons. Screen: " + screenWidth + "x" + screenHeight);

        for (CustomButtonConfig config : configs) {
            try {
                // 커스텀 버튼으로 사용할 뷰 생성 (예: Button 또는 TextView)
                // 실제로는 item_custom_overlay_button.xml 같은 별도 레이아웃을 인플레이트하는 것이 더 유연함
                Button customButton = new Button(context); // 또는 TextView, ImageButton 등
                customButton.setText(config.getLabel());
                // customButton.setBackgroundResource(R.drawable.custom_button_background); // 커스텀 배경 적용

                // 위치와 크기 계산 (비율을 실제 픽셀 값으로 변환)
                int buttonWidth = (int) (screenWidth * config.getWidthPercent());
                int buttonHeight = (int) (screenHeight * config.getHeightPercent());
                int buttonX = (int) (screenWidth * config.getXPositionPercent());
                int buttonY = (int) (screenHeight * config.getYPositionPercent());

                // FrameLayout.LayoutParams를 사용하여 컨테이너 내에서의 위치와 크기 설정
                FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(buttonWidth, buttonHeight);
                buttonParams.leftMargin = buttonX;
                buttonParams.topMargin = buttonY;
                // buttonParams.gravity = Gravity.TOP | Gravity.START; // 이미 마진으로 위치 설정

                customButton.setLayoutParams(buttonParams);

                // 커스텀 버튼 터치 리스너 설정
                customButton.setOnTouchListener((v, event) -> {
                    if (keyInputListener != null) {
                        keyInputListener.onKeyEvent(config.getKeyName(), event);
                    }
                    // true를 반환하면 이벤트가 여기서 소비됨 (다른 리스너로 전달 안됨)
                    // false를 반환하면 다른 리스너(예: 뷰의 기본 클릭 리스너)도 호출될 수 있음
                    // 보통은 true로 하여 명확하게 처리.
                    return true;
                });

                customButtonContainer.addView(customButton);
                Log.d(TAG, "Added custom button: " + config.getLabel() + " at (" + buttonX + "," + buttonY + ") size (" + buttonWidth + "," + buttonHeight + ")");

            } catch (Exception e) {
                Log.e(TAG, "Error creating or adding custom button: " + config.getLabel(), e);
            }
        }
        // 레이아웃 변경 사항을 반영하기 위해 overlayView를 업데이트해야 할 수도 있음
        // 하지만 버튼 추가/제거는 컨테이너 내부에서 일어나므로, 컨테이너가 이미 윈도우에 있다면 자동으로 반영될 수 있음.
        // 문제가 있다면 overlayView에 대해 requestLayout() 이나 invalidate() 호출 고려.
    }


    public void showOverlay() {
        if (overlayView == null) {
            Log.e(TAG, "Overlay view is null, cannot show. Call createOverlayView() first.");
            return;
        }
        if (overlayVisible) {
            Log.w(TAG, "Overlay is already visible.");
            return;
        }
        try {
            // params.flags 는 생성자 또는 createOverlayView 에서 설정된 값을 사용합니다.
            // 여기서 다시 설정할 필요는 일반적으로 없습니다.
            // params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE; (이미 설정됨)

            windowManager.addView(overlayView, params);
            overlayVisible = true;
            Log.i(TAG, "Overlay view added to window manager.");
        } catch (Exception e) {
            Log.e(TAG, "Error adding overlay view to window", e);
            overlayVisible = false; // 실패 시 상태 업데이트
        }
    }

    public void hideOverlay() {
        if (overlayView != null && overlayVisible) {
            try {
                windowManager.removeView(overlayView);
                overlayVisible = false;
                Log.i(TAG, "Overlay view removed from window manager.");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay view from window", e);
                // removeView 실패 시 overlayVisible 상태를 어떻게 할지 고려.
                // 보통은 제거된 것으로 간주.
            }
        } else {
            Log.w(TAG, "Overlay view is null or not visible, cannot hide.");
        }
    }

    public boolean isOverlayVisible() {
        return overlayVisible;
    }

    /**
     * 서비스 종료 등 리소스 정리 시 호출됩니다.
     */
    public void cleanup() {
        hideOverlay(); // 뷰가 추가되어 있다면 제거
        if (customButtonContainer != null) {
            customButtonContainer.removeAllViews();
        }
        overlayView = null;
        customButtonContainer = null;
        context = null; // Context 참조 해제
        windowManager = null; // WindowManager 참조 해제 (서비스가 관리하므로 여기서 직접 해제할 필요는 없을 수 있음)
        layoutInflater = null;
        keyInputListener = null;
        Log.i(TAG, "OverlayViewManager cleaned up.");
    }
}

