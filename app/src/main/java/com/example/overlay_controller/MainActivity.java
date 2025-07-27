// MainActivity.java
package com.example.overlay_controller; // 본인의 패키지 이름

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable; // Nullable import 추가
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                    startOverlayService();
                } else {
                    Toast.makeText(this, "알림 권한이 거부되었습니다. 일부 기능이 제한될 수 있습니다.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 수정된 버튼 ID 사용
        Button startButton = findViewById(R.id.start_overlay_button); // 여기 수정
        startButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                } else {
                    checkAndRequestNotificationPermission();
                }
            } else {
                startOverlayService();
            }
        });

        // 수정된 버튼 ID 사용
        Button stopButton = findViewById(R.id.stop_overlay_button); // 여기 수정
        stopButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, OverlayService.class);
            stopService(intent);
            Toast.makeText(this, "오버레이 컨트롤러 중지", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                startOverlayService();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Toast.makeText(this, "알림을 받으려면 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            startOverlayService();
        }
    }

    private void startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "다른 앱 위에 표시 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }

        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "오버레이 컨트롤러 시작", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "다른 앱 위에 표시 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                    checkAndRequestNotificationPermission();
                } else {
                    Toast.makeText(this, "다른 앱 위에 표시 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
