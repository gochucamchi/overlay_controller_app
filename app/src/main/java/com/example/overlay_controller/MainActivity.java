package com.example.overlay_controller;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startOverlayButton = findViewById(R.id.start_overlay_button);
        Button stopOverlayButton = findViewById(R.id.stop_overlay_button);

        checkOverlayPermission(); // 앱 시작 시 권한 확인

        startOverlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(MainActivity.this)) {
                        startOverlayService();
                    } else {
                        requestOverlayPermission();
                        Toast.makeText(MainActivity.this, "오버레이 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // M 미만 버전은 매니페스트 권한으로 충분 (이론상)
                    startOverlayService();
                }
            }
        });

        stopOverlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopOverlayService();
            }
        });
    }

    private void startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 다시 한 번 권한 확인 (사용자가 설정을 변경했을 수도 있음)
            Toast.makeText(this, "오버레이 권한이 없습니다. 설정에서 허용해주세요.", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        // 서비스가 이미 실행 중인지 확인 (선택 사항, 중복 실행 방지)
        // if (isServiceRunning(OverlayService.class)) {
        // Toast.makeText(this, "오버레이 서비스가 이미 실행 중입니다.", Toast.LENGTH_SHORT).show();
        // return;
        // }
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent); // Android Oreo 이상에서는 Foreground Service로 시작해야 할 수 있음
        } else {
            startService(intent);
        }
        Toast.makeText(this, "오버레이 서비스 시작", Toast.LENGTH_SHORT).show();
    }

    private void stopOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        stopService(intent);
        Toast.makeText(this, "오버레이 서비스 중지", Toast.LENGTH_SHORT).show();
    }


    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission();
            }
        }
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "오버레이 권한이 허용되었습니다. 시작 버튼을 눌러주세요.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "오버레이 권한이 거부되었습니다. 앱 사용을 위해 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // 선택 사항: 서비스 실행 여부 확인 메소드
        /*
        private boolean isServiceRunning(Class<?> serviceClass) {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
            return false;
        }
        */
}
