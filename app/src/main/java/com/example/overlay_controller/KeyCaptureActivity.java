package com.example.overlay_controller;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class KeyCaptureActivity extends AppCompatActivity {

    // Service와 통신하기 위한 콜백 인터페이스
    public interface KeyCaptureListener {
        void onKeyCaptured(String originalButtonLabel, String newKey);
    }

    public static final String EXTRA_BUTTON_LABEL = "BUTTON_LABEL";

    private TextView selectedKeyTextView;
    private String capturedKey = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_capture);

        selectedKeyTextView = findViewById(R.id.tv_selected_key);
        Button confirmButton = findViewById(R.id.btn_confirm);

        ViewGroup keyboardContainer = findViewById(R.id.keyboard_container);
        setKeyListeners(keyboardContainer);

        confirmButton.setOnClickListener(v -> sendResultAndFinish());
    }

    private void setKeyListeners(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof Button) {
                Button keyButton = (Button) child;
                keyButton.setOnClickListener(v -> {
                    capturedKey = keyButton.getText().toString();
                    selectedKeyTextView.setText(capturedKey);
                });
            } else if (child instanceof ViewGroup) {
                setKeyListeners((ViewGroup) child);
            }
        }
    }

    private void sendResultAndFinish() {
        if (capturedKey.isEmpty()) {
            finish();
            return;
        }

        // OverlayService에 등록된 리스너가 있다면 직접 호출
        if (OverlayService.keyCaptureListener != null) {
            String buttonLabel = getIntent().getStringExtra(EXTRA_BUTTON_LABEL);
            OverlayService.keyCaptureListener.onKeyCaptured(buttonLabel, capturedKey);
        }

        finish();
    }
}