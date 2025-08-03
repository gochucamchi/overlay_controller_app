package com.example.overlay_controller;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class KeyCaptureActivity extends AppCompatActivity {

    // <<<< 1. 현재 실행 중인 액티비티 인스턴스를 저장할 static 변수 추가 >>>>
    public static KeyCaptureActivity instance;

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

        // <<<< 2. onCreate에서 자기 자신을 static 변수에 할당 >>>>
        instance = this;

        selectedKeyTextView = findViewById(R.id.tv_selected_key);
        Button confirmButton = findViewById(R.id.btn_confirm);

        ViewGroup keyboardContainer = findViewById(R.id.keyboard_container);
        setKeyListeners(keyboardContainer);

        confirmButton.setOnClickListener(v -> sendResultAndFinish());
    }

    // <<<< 3. 외부에서 이 액티비티를 닫을 수 있도록 static 메소드 추가 >>>>
    public static void finishActivity() {
        if (instance != null) {
            instance.finish();
            instance = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // <<<< 4. 액티비티가 소멸될 때 static 변수를 null로 만들어 메모리 누수 방지 >>>>
        if (instance == this) {
            instance = null;
        }
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

        if (OverlayService.keyCaptureListener != null) {
            String buttonLabel = getIntent().getStringExtra(EXTRA_BUTTON_LABEL);
            OverlayService.keyCaptureListener.onKeyCaptured(buttonLabel, capturedKey);
        }

        finish();
    }
}