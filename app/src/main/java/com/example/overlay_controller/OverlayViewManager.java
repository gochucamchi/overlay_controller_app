package com.example.overlay_controller;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverlayViewManager {

    private static final String TAG = "OverlayViewManager";

    private Context context;
    private WindowManager windowManager;
    private LayoutInflater layoutInflater;

    private Map<CustomButtonConfig, View> buttonViews = new HashMap<>();
    private View staticMenuView;
    private ImageButton addButton;
    private boolean isOverlayVisible = false;

    private KeyInputListener keyInputListener;
    private OverlayInteractionListener overlayInteractionListener;

    public interface KeyInputListener { void onKeyEvent(String keyName, MotionEvent event); }

    public interface OverlayInteractionListener {
        void onEditModeChanged(EditMode newMode);
        EditMode getCurrentEditMode();
        void onButtonUpdated(CustomButtonConfig updatedConfig);
        void onButtonDeleted(CustomButtonConfig configToDelete);
        void onNewButtonRequested();
        void onKeyAssignRequested(CustomButtonConfig config);
    }

    public OverlayViewManager(Context context, WindowManager windowManager, LayoutInflater layoutInflater, KeyInputListener keyInputListener, OverlayInteractionListener overlayInteractionListener) {
        this.context = context;
        this.windowManager = windowManager;
        this.layoutInflater = layoutInflater;
        this.keyInputListener = keyInputListener;
        this.overlayInteractionListener = overlayInteractionListener;

        this.staticMenuView = layoutInflater.inflate(R.layout.controller_layout, null);
        createAddButton();
        setupMenuClickListeners();
    }

    private void createAddButton() {
        addButton = new ImageButton(context);
        addButton.setImageResource(android.R.drawable.ic_input_add);
        addButton.setOnClickListener(v -> {
            if (overlayInteractionListener != null) {
                overlayInteractionListener.onNewButtonRequested();
            }
        });
    }

    private int getOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    private void setupMenuClickListeners() {
        final ImageButton toggleButton = staticMenuView.findViewById(R.id.button_toggle_edit_menu);
        final LinearLayout editMenu = staticMenuView.findViewById(R.id.edit_mode_menu);

        toggleButton.setOnClickListener(v -> {
            if (editMenu.getVisibility() == View.VISIBLE) {
                editMenu.setVisibility(View.GONE);
                if (overlayInteractionListener != null) {
                    overlayInteractionListener.onEditModeChanged(EditMode.NORMAL);
                }
            } else {
                editMenu.setVisibility(View.VISIBLE);
            }
        });

        staticMenuView.findViewById(R.id.button_mode_normal).setOnClickListener(v -> {
            if (overlayInteractionListener != null)
                overlayInteractionListener.onEditModeChanged(EditMode.NORMAL);
            editMenu.setVisibility(View.GONE);
        });
        staticMenuView.findViewById(R.id.button_mode_add_move).setOnClickListener(v -> {
            if (overlayInteractionListener != null)
                overlayInteractionListener.onEditModeChanged(EditMode.ADD_MOVE);
        });
        staticMenuView.findViewById(R.id.button_mode_resize).setOnClickListener(v -> {
            if (overlayInteractionListener != null)
                overlayInteractionListener.onEditModeChanged(EditMode.RESIZE);
        });
        staticMenuView.findViewById(R.id.button_mode_assign_key).setOnClickListener(v -> {
            if (overlayInteractionListener != null)
                overlayInteractionListener.onEditModeChanged(EditMode.ASSIGN_KEY);
        });
        staticMenuView.findViewById(R.id.button_mode_delete).setOnClickListener(v -> {
            if (overlayInteractionListener != null)
                overlayInteractionListener.onEditModeChanged(EditMode.DELETE);
        });
    }

    public void displayCustomButtons(List<CustomButtonConfig> configs) {
        for (View view : buttonViews.values()) {
            try {
                if (view != null && view.getWindowToken() != null) windowManager.removeView(view);
            } catch (Exception e) { /* ignore */ }
        }
        buttonViews.clear();

        if (configs == null || !isOverlayVisible) return;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        for (CustomButtonConfig config : configs) {
            Button customButton = new Button(context);
            customButton.setText(config.getLabel());

            WindowManager.LayoutParams buttonParams = new WindowManager.LayoutParams(
                    (int) (screenWidth * config.getWidthPercent()), (int) (screenHeight * config.getHeightPercent()),
                    (int) (screenWidth * config.getXPositionPercent()), (int) (screenHeight * config.getYPositionPercent()),
                    getOverlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            buttonParams.gravity = Gravity.TOP | Gravity.START;

            final float[] initialTouchPos = new float[2];
            final int[] initialButtonPos = new int[2];
            final int[] initialButtonSize = new int[2];
            final boolean[] isResizing = {false};

            // <<<< 멀티터치를 위해 OnTouchListener 로직을 완전히 새로 구성합니다 >>>>
            customButton.setOnTouchListener((v, event) -> {
                if (overlayInteractionListener == null) return false;
                EditMode currentMode = overlayInteractionListener.getCurrentEditMode();

                // NORMAL 모드일 때는 멀티터치를 지원하는 간단한 로직을 사용합니다.
                if (currentMode == EditMode.NORMAL) {
                    if (keyInputListener == null) return false;

                    // getAction() 대신 getActionMasked()를 사용합니다.
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_POINTER_DOWN:
                            v.setAlpha(0.5f); // 눌림 효과
                            keyInputListener.onKeyEvent(config.getKeyName(), event);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_POINTER_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1.0f); // 복구 효과
                            keyInputListener.onKeyEvent(config.getKeyName(), event);
                            break;
                    }
                    return true; // NORMAL 모드에서는 항상 이벤트를 소비
                }

                // 편집 모드들은 한 번에 하나만 조작하므로 기존 로직을 유지합니다.
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isResizing[0] = false;
                        if (currentMode == EditMode.RESIZE) {
                            if (event.getX() > v.getWidth() - 50 && event.getY() > v.getHeight() - 50) {
                                isResizing[0] = true;
                                initialTouchPos[0] = event.getRawX();
                                initialTouchPos[1] = event.getRawY();
                                initialButtonSize[0] = buttonParams.width;
                                initialButtonSize[1] = buttonParams.height;
                            }
                        } else if (currentMode == EditMode.ADD_MOVE) {
                            initialTouchPos[0] = event.getRawX();
                            initialTouchPos[1] = event.getRawY();
                            initialButtonPos[0] = buttonParams.x;
                            initialButtonPos[1] = buttonParams.y;
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (currentMode == EditMode.RESIZE && isResizing[0]) {
                            int newWidth = initialButtonSize[0] + (int) (event.getRawX() - initialTouchPos[0]);
                            int newHeight = initialButtonSize[1] + (int) (event.getRawY() - initialTouchPos[1]);
                            buttonParams.width = Math.max(newWidth, 100);
                            buttonParams.height = Math.max(newHeight, 100);
                            windowManager.updateViewLayout(v, buttonParams);
                        } else if (currentMode == EditMode.ADD_MOVE) {
                            buttonParams.x = initialButtonPos[0] + (int) (event.getRawX() - initialTouchPos[0]);
                            buttonParams.y = initialButtonPos[1] + (int) (event.getRawY() - initialTouchPos[1]);
                            windowManager.updateViewLayout(v, buttonParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (currentMode == EditMode.ASSIGN_KEY) {
                            overlayInteractionListener.onKeyAssignRequested(config);
                            return true;
                        }
                        if (currentMode == EditMode.DELETE) {
                            try { if (v.getWindowToken() != null) windowManager.removeView(v); } catch (Exception e) { /* ignore */ }
                            buttonViews.remove(config);
                            overlayInteractionListener.onButtonDeleted(config);
                            return true;
                        }
                        if (currentMode == EditMode.RESIZE && isResizing[0]) {
                            config.setWidthPercent((float) buttonParams.width / screenWidth);
                            config.setHeightPercent((float) buttonParams.height / screenHeight);
                            overlayInteractionListener.onButtonUpdated(config);
                        } else if (currentMode == EditMode.ADD_MOVE) {
                            config.setXPositionPercent((float) buttonParams.x / screenWidth);
                            config.setYPositionPercent((float) buttonParams.y / screenHeight);
                            overlayInteractionListener.onButtonUpdated(config);
                        }
                        isResizing[0] = false;
                        return true;
                }
                return false;
            });
            windowManager.addView(customButton, buttonParams);
            buttonViews.put(config, customButton);
        }
        if (overlayInteractionListener != null) {
            updateButtonVisuals(overlayInteractionListener.getCurrentEditMode());
        }
    }

    public void updateButtonVisuals(EditMode mode) {
        if (mode == EditMode.ADD_MOVE) {
            if (addButton.getWindowToken() == null) {
                WindowManager.LayoutParams addButtonParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        getOverlayType(),
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                );
                addButtonParams.gravity = Gravity.TOP | Gravity.END;
                addButtonParams.x = 16;
                addButtonParams.y = 16;
                windowManager.addView(addButton, addButtonParams);
            }
        } else {
            if (addButton.getWindowToken() != null) {
                windowManager.removeView(addButton);
            }
        }

        for (View view : buttonViews.values()) {
            if (view instanceof Button) {
                Button button = (Button) view;
                switch (mode) {
                    case NORMAL:
                        button.setAlpha(1.0f);
                        button.getBackground().clearColorFilter();
                        break;
                    case ADD_MOVE:
                    case RESIZE:
                    case ASSIGN_KEY:
                    case DELETE:
                        button.setAlpha(0.7f);
                        int color = Color.CYAN;
                        if (mode == EditMode.RESIZE) color = Color.GREEN;
                        if (mode == EditMode.ASSIGN_KEY) color = Color.YELLOW;
                        if (mode == EditMode.DELETE) color = Color.RED;
                        button.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
                        break;
                    default:
                        button.setAlpha(1.0f);
                        button.getBackground().clearColorFilter();
                        break;
                }
            }
        }
    }

    public void showOverlay() {
        if (!isOverlayVisible) {
            WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            menuParams.gravity = Gravity.TOP | Gravity.START;
            windowManager.addView(staticMenuView, menuParams);
            isOverlayVisible = true;
        }
    }

    public void hideOverlay() {
        if (isOverlayVisible) {
            cleanup();
            isOverlayVisible = false;
        }
    }

    public boolean isOverlayVisible() {
        return isOverlayVisible;
    }

    public void cleanup() {
        try {
            if (staticMenuView != null && staticMenuView.getWindowToken() != null) windowManager.removeView(staticMenuView);
            if (addButton != null && addButton.getWindowToken() != null) windowManager.removeView(addButton);
        } catch (Exception e) { /* ignore */ }
        for (View view : buttonViews.values()) {
            try {
                if (view != null && view.getWindowToken() != null) windowManager.removeView(view);
            } catch (Exception e) { /* ignore */ }
        }
        buttonViews.clear();
    }
}