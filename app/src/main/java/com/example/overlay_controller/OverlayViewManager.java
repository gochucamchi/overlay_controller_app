// /com/example/overlay_controller/OverlayViewManager.java
package com.example.overlay_controller;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
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

    private final Context context;
    private final WindowManager windowManager;
    private final KeyInputHandler keyInputHandler;
    private final OverlayInteractionListener overlayInteractionListener;

    private final Map<CustomButtonConfig, View> buttonViews = new HashMap<>();
    private View staticMenuView;
    private ImageButton addButton;
    private boolean isOverlayVisible = false;

    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private final Map<View, Runnable> heldDownRunnables = new HashMap<>();
    private static final long REPEAT_INTERVAL_MS = 50;

    public interface OverlayInteractionListener {
        void onEditModeChanged(EditMode newMode);
        EditMode getCurrentEditMode();
        void onButtonUpdated(CustomButtonConfig updatedConfig);
        void onButtonDeleted(CustomButtonConfig configToDelete);
        void onNewButtonRequested();
        void onKeyAssignRequested(CustomButtonConfig config);
    }

    public OverlayViewManager(Context context, WindowManager windowManager, KeyInputHandler keyInputHandler, OverlayInteractionListener overlayInteractionListener) {
        this.context = context;
        this.windowManager = windowManager;
        this.keyInputHandler = keyInputHandler;
        this.overlayInteractionListener = overlayInteractionListener;

        createAddButton();
    }

    private int getOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    public void displayCustomButtons(List<CustomButtonConfig> configs) {
        hideOverlay();
        isOverlayVisible = true;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        final int FLAG_SLIPPERY_COMPAT = 0x20000000;

        for (CustomButtonConfig config : configs) {
            Button customButton = new Button(context);
            customButton.setText(config.getLabel());

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    (int) (screenWidth * config.getWidthPercent()),
                    (int) (screenHeight * config.getHeightPercent()),
                    getOverlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH |
                            FLAG_SLIPPERY_COMPAT,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = (int) (screenWidth * config.getXPositionPercent());
            params.y = (int) (screenHeight * config.getYPositionPercent());

            setupFullTouchListener(customButton, config, params, screenWidth, screenHeight);

            try {
                windowManager.addView(customButton, params);
                buttonViews.put(config, customButton);
            } catch (Exception e) {}
        }

        addStaticMenuView();
        updateButtonVisuals();
    }

    private void setupFullTouchListener(Button button, CustomButtonConfig config, WindowManager.LayoutParams params, int screenWidth, int screenHeight) {
        final float[] initialTouchPos = new float[2];
        final int[] initialViewPos = new int[2];
        final int[] initialViewSize = new int[2];

        button.setOnTouchListener((v, event) -> {
            EditMode currentMode = overlayInteractionListener.getCurrentEditMode();

            if (currentMode != EditMode.NORMAL) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (currentMode == EditMode.ASSIGN_KEY) {
                            overlayInteractionListener.onKeyAssignRequested(config);
                        } else if (currentMode == EditMode.DELETE) {
                            overlayInteractionListener.onButtonDeleted(config);
                        } else {
                            initialTouchPos[0] = event.getRawX();
                            initialTouchPos[1] = event.getRawY();
                            initialViewPos[0] = params.x;
                            initialViewPos[1] = params.y;
                            initialViewSize[0] = params.width;
                            initialViewSize[1] = params.height;
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (currentMode == EditMode.ADD_MOVE) {
                            params.x = initialViewPos[0] + (int) (event.getRawX() - initialTouchPos[0]);
                            params.y = initialViewPos[1] + (int) (event.getRawY() - initialTouchPos[1]);
                            windowManager.updateViewLayout(v, params);
                        } else if (currentMode == EditMode.RESIZE) {
                            params.width = initialViewSize[0] + (int) (event.getRawX() - initialTouchPos[0]);
                            params.height = initialViewSize[1] + (int) (event.getRawY() - initialTouchPos[1]);
                            windowManager.updateViewLayout(v, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (currentMode == EditMode.ADD_MOVE) {
                            config.setXPositionPercent((float) params.x / screenWidth);
                            config.setYPositionPercent((float) params.y / screenHeight);
                            overlayInteractionListener.onButtonUpdated(config);
                        } else if (currentMode == EditMode.RESIZE) {
                            config.setWidthPercent((float) params.width / screenWidth);
                            config.setHeightPercent((float) params.height / screenHeight);
                            overlayInteractionListener.onButtonUpdated(config);
                        }
                        return true;
                }
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    keyInputHandler.onKeyEvent(config.getKeyName(), "KEY_DOWN");
                    v.setAlpha(0.5f);
                    Runnable repeater = () -> {
                        if (heldDownRunnables.containsKey(v)) {
                            keyInputHandler.onKeyEvent(config.getKeyName(), "KEY_DOWN");
                            repeatHandler.postDelayed(heldDownRunnables.get(v), REPEAT_INTERVAL_MS);
                        }
                    };
                    heldDownRunnables.put(v, repeater);
                    repeatHandler.postDelayed(repeater, REPEAT_INTERVAL_MS);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (heldDownRunnables.containsKey(v)) {
                        repeatHandler.removeCallbacks(heldDownRunnables.get(v));
                        heldDownRunnables.remove(v);
                    }
                    keyInputHandler.onKeyEvent(config.getKeyName(), "KEY_UP");
                    v.setAlpha(1.0f);
                    break;
            }
            return true;
        });
    }

    private void addStaticMenuView() {
        if (staticMenuView != null && staticMenuView.getWindowToken() != null) {
            try { windowManager.removeView(staticMenuView); } catch (Exception e) {}
        }
        staticMenuView = View.inflate(context, R.layout.controller_layout, null);

        WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;

        setupMenuClickListeners();
        try { windowManager.addView(staticMenuView, menuParams); } catch (Exception e) {}
    }

    private void createAddButton() {
        addButton = new ImageButton(context);
        addButton.setImageResource(android.R.drawable.ic_input_add);
        addButton.setBackgroundColor(Color.TRANSPARENT);
        addButton.setOnClickListener(v -> {
            if (overlayInteractionListener != null) {
                overlayInteractionListener.onNewButtonRequested();
            }
        });
    }

    private void setupMenuClickListeners() {
        if (staticMenuView == null) return;
        final LinearLayout editMenu = staticMenuView.findViewById(R.id.edit_mode_menu);

        staticMenuView.findViewById(R.id.button_toggle_edit_menu).setOnClickListener(v ->
                editMenu.setVisibility(editMenu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)
        );

        View.OnClickListener listener = v -> {
            overlayInteractionListener.onEditModeChanged(getEditModeFromId(v.getId()));
            if (v.getId() == R.id.button_mode_normal) {
                editMenu.setVisibility(View.GONE);
            }
        };

        staticMenuView.findViewById(R.id.button_mode_normal).setOnClickListener(listener);
        staticMenuView.findViewById(R.id.button_mode_add_move).setOnClickListener(listener);
        staticMenuView.findViewById(R.id.button_mode_resize).setOnClickListener(listener);
        staticMenuView.findViewById(R.id.button_mode_assign_key).setOnClickListener(listener);
        staticMenuView.findViewById(R.id.button_mode_delete).setOnClickListener(listener);
    }

    private EditMode getEditModeFromId(int id) {
        if (id == R.id.button_mode_add_move) return EditMode.ADD_MOVE;
        if (id == R.id.button_mode_resize) return EditMode.RESIZE;
        if (id == R.id.button_mode_assign_key) return EditMode.ASSIGN_KEY;
        if (id == R.id.button_mode_delete) return EditMode.DELETE;
        return EditMode.NORMAL;
    }

    public void updateButtonVisuals() {
        EditMode mode = overlayInteractionListener.getCurrentEditMode();

        if (mode == EditMode.ADD_MOVE) {
            if (addButton != null && addButton.getWindowToken() == null) {
                WindowManager.LayoutParams addButtonParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        getOverlayType(),
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );
                addButtonParams.gravity = Gravity.TOP | Gravity.END;
                addButtonParams.x = 16;
                addButtonParams.y = 16;
                windowManager.addView(addButton, addButtonParams);
            }
        } else {
            if (addButton != null && addButton.getWindowToken() != null) {
                windowManager.removeView(addButton);
            }
        }

        for (View view : buttonViews.values()) {
            if (view instanceof Button) {
                Button button = (Button) view;
                if (mode == EditMode.NORMAL) {
                    button.setAlpha(1.0f);
                    button.getBackground().clearColorFilter();
                } else {
                    button.setAlpha(0.7f);
                    int color = Color.CYAN;
                    if (mode == EditMode.RESIZE) color = Color.GREEN;
                    if (mode == EditMode.ASSIGN_KEY) color = Color.YELLOW;
                    if (mode == EditMode.DELETE) color = Color.RED;
                    button.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
                }
            }
        }
    }

    public void hideOverlay() {
        for (Runnable runnable : heldDownRunnables.values()) {
            repeatHandler.removeCallbacks(runnable);
        }
        heldDownRunnables.clear();

        if (staticMenuView != null && staticMenuView.getWindowToken() != null) {
            try { windowManager.removeView(staticMenuView); } catch (Exception e) {}
        }
        if (addButton != null && addButton.getWindowToken() != null) {
            try { windowManager.removeView(addButton); } catch (Exception e) {}
        }
        for (View view : buttonViews.values()) {
            if (view.getWindowToken() != null) {
                try { windowManager.removeView(view); } catch (Exception e) {}
            }
        }
        buttonViews.clear();
        isOverlayVisible = false;
    }

    public void cleanup() {
        hideOverlay();
    }
}