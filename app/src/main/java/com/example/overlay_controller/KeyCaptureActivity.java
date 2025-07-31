package com.example.overlay_controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class KeyCaptureActivity extends AppCompatActivity {

    public static final String ACTION_KEY_CAPTURED = "com.example.overlay_controller.ACTION_KEY_CAPTURED";
    public static final String EXTRA_CAPTURED_KEY = "CAPTURED_KEY";
    public static final String EXTRA_BUTTON_LABEL = "BUTTON_LABEL";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_capture);

        EditText editText = findViewById(R.id.editText_key_capture);

        // 키보드를 바로 띄우기
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 글자가 입력되면 (길이가 1 이상이면)
                if (s.length() > 0) {
                    // 입력된 키(가장 마지막 글자)를 가져옴
                    String capturedKey = String.valueOf(s.charAt(s.length() - 1));

                    // 원래 버튼의 라벨을 Intent로부터 가져옴
                    String buttonLabel = getIntent().getStringExtra(EXTRA_BUTTON_LABEL);

                    // 결과를 Broadcast로 OverlayService에 전송
                    Intent resultIntent = new Intent(ACTION_KEY_CAPTURED);
                    resultIntent.putExtra(EXTRA_CAPTURED_KEY, capturedKey.toUpperCase()); // 대문자로 변환
                    resultIntent.putExtra(EXTRA_BUTTON_LABEL, buttonLabel);
                    sendBroadcast(resultIntent);

                    // 액티비티 종료
                    finish();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
}