package com.example.overlay_controller;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // activity_main.xml 레이아웃 파일을 이 액티비티의 UI로 설정합니다.
        // 만약 activity_main.xml 파일이 없다면 이 부분에서 오류가 발생할 수 있습니다.
        setContentView(R.layout.activity_main);
    }
}
