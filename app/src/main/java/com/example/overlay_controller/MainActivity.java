package com.example.overlay_controller;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CustomButtonAdapter.OnButtonConfigActionListener {

    private static final String TAG = "MainActivity";
    private ButtonConfigManager buttonConfigManager;
    private CustomButtonAdapter adapter;
    private List<CustomButtonConfig> currentConfigs;
    private RecyclerView recyclerView;

    // 오버레이 권한 요청을 위한 ActivityResultLauncher
    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        Log.i(TAG, "오버레이 권한이 부여되었습니다.");
                        Toast.makeText(this, "오버레이 권한이 부여되었습니다.", Toast.LENGTH_SHORT).show();
                        // 권한 부여 후 서비스 시작 시도
                        startOverlayService();
                    } else {
                        Log.w(TAG, "오버레이 권한이 거부되었습니다.");
                        Toast.makeText(this, "오버레이 권한이 필요합니다. 앱을 사용하려면 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                    }
                }
            });

    // 알림 권한 요청 (Android 13 이상)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.i(TAG, "알림 권한이 부여되었습니다.");
                    Toast.makeText(this, "알림 권한이 부여되었습니다.", Toast.LENGTH_SHORT).show();
                    checkOverlayPermissionAndStartService(); // 다음 권한 확인 또는 서비스 시작
                } else {
                    Log.w(TAG, "알림 권한이 거부되었습니다.");
                    Toast.makeText(this, "알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                    // 알림 권한이 없어도 오버레이는 시도할 수 있도록 다음 단계 진행
                    checkOverlayPermissionAndStartService();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonConfigManager = new ButtonConfigManager(this);

        recyclerView = findViewById(R.id.recyclerView_custom_buttons);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        currentConfigs = buttonConfigManager.getAllButtonConfigs();
        if (currentConfigs == null) {
            currentConfigs = new ArrayList<>();
        }
        adapter = new CustomButtonAdapter(this, currentConfigs, this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_button);
        fab.setOnClickListener(view -> showAddEditButtonDialog(null, -1));

        Button startServiceButton = findViewById(R.id.button_start_service);
        startServiceButton.setOnClickListener(v -> {
            Log.d(TAG, "서비스 시작 버튼 클릭됨");
            checkNotificationPermissionAndStart();
        });

        Button stopServiceButton = findViewById(R.id.button_stop_service);
        stopServiceButton.setOnClickListener(v -> {
            Log.d(TAG, "서비스 중지 버튼 클릭됨");
            Intent serviceIntent = new Intent(this, OverlayService.class);
            serviceIntent.setAction(OverlayService.ACTION_STOP_SERVICE);
            startService(serviceIntent); // 서비스에 중지 액션 전달
            Toast.makeText(this, "서비스 중지 요청됨", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33)
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Log.d(TAG, "알림 권한 요청 중...");
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Log.d(TAG, "알림 권한 이미 부여됨.");
                checkOverlayPermissionAndStartService();
            }
        } else {
            // Android 13 미만에서는 알림 권한이 설치 시 자동 부여 (일부 예외 제외)
            Log.d(TAG, "Android 13 미만, 알림 권한 필요 없음 또는 이미 처리됨.");
            checkOverlayPermissionAndStartService();
        }
    }


    private void checkOverlayPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.d(TAG, "오버레이 권한 요청 중...");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            Log.d(TAG, "오버레이 권한 이미 부여됨 또는 필요 없음.");
            startOverlayService();
        }
    }

    private void startOverlayService() {
        Log.d(TAG, "OverlayService 시작 시도...");
        Intent serviceIntent = new Intent(this, OverlayService.class);
        // 필요하다면 여기에 action이나 extra를 추가할 수 있습니다.
        // 예: serviceIntent.setAction("START_ACTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "가상 컨트롤러 서비스 시작됨", Toast.LENGTH_SHORT).show();
    }


    private void refreshButtonList() {
        currentConfigs = buttonConfigManager.getAllButtonConfigs();
        if (currentConfigs == null) {
            currentConfigs = new ArrayList<>();
        }
        adapter.updateData(currentConfigs);
        Log.d(TAG, "커스텀 버튼 목록 새로고침. 총 " + currentConfigs.size() + "개");

        // 설정 변경 후 서비스에 알림 (간단한 방법: 서비스 재시작 또는 특정 액션 전송)
        // 이 부분은 서비스와 통신하는 더 정교한 방법으로 개선될 수 있습니다.
        // 예: LocalBroadcastManager 또는 서비스에 바인딩하여 메소드 호출
        // 현재는 서비스가 시작될 때 로드하거나, 필요하다면 명시적으로 서비스 재시작
        // if (isOverlayServiceRunning()) { // 서비스 실행 중인지 확인하는 로직 필요
        //  Log.d(TAG, "설정 변경으로 서비스에 업데이트 알림 (예: 재시작)");
        //  startOverlayService(); // 간단하게는 재시작하여 새 설정을 로드하도록 함
        // }
    }


    private void showAddEditButtonDialog(final CustomButtonConfig existingConfig, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_edit_button, null); // 다이얼로그 레이아웃 (아래 생성)
        builder.setView(dialogView);

        final EditText editTextLabel = dialogView.findViewById(R.id.editText_label);
        final EditText editTextKeyName = dialogView.findViewById(R.id.editText_key_name);
        final EditText editTextXPos = dialogView.findViewById(R.id.editText_x_pos);
        final EditText editTextYPos = dialogView.findViewById(R.id.editText_y_pos);
        final EditText editTextWidth = dialogView.findViewById(R.id.editText_width);
        final EditText editTextHeight = dialogView.findViewById(R.id.editText_height);

        builder.setTitle(existingConfig == null ? "새 버튼 추가" : "버튼 수정");

        if (existingConfig != null) {
            editTextLabel.setText(existingConfig.getLabel());
            editTextKeyName.setText(existingConfig.getKeyName());
            editTextXPos.setText(String.valueOf(existingConfig.getXPositionPercent()));
            editTextYPos.setText(String.valueOf(existingConfig.getYPositionPercent()));
            editTextWidth.setText(String.valueOf(existingConfig.getWidthPercent()));
            editTextHeight.setText(String.valueOf(existingConfig.getHeightPercent()));
        }

        builder.setPositiveButton(existingConfig == null ? "추가" : "저장", (dialog, which) -> {
            try {
                String label = editTextLabel.getText().toString().trim();
                String keyName = editTextKeyName.getText().toString().trim();
                float xPos = Float.parseFloat(editTextXPos.getText().toString());
                float yPos = Float.parseFloat(editTextYPos.getText().toString());
                float width = Float.parseFloat(editTextWidth.getText().toString());
                float height = Float.parseFloat(editTextHeight.getText().toString());

                if (label.isEmpty() || keyName.isEmpty()) {
                    Toast.makeText(MainActivity.this, "레이블과 키 이름은 필수입니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 값 유효성 검사 (0.0 ~ 1.0 사이 등)
                if (xPos < 0 || xPos > 1 || yPos < 0 || yPos > 1 || width <= 0 || width > 1 || height <= 0 || height > 1) {
                    Toast.makeText(MainActivity.this, "위치/크기 값은 0.0과 1.0 사이여야 합니다 (크기는 0보다 커야 함).", Toast.LENGTH_LONG).show();
                    return;
                }


                CustomButtonConfig newConfig = new CustomButtonConfig(label, keyName, xPos, yPos, width, height);

                if (existingConfig == null) { // 추가 모드
                    // ID가 중복되지 않도록 설정 (ButtonConfigManager에서 처리하거나 여기서 UUID 사용)
                    // 현재 CustomButtonConfig는 id를 가지고 있지 않으므로, ButtonConfigManager가 내부적으로 관리
                    buttonConfigManager.addButtonConfig(newConfig);
                    Toast.makeText(MainActivity.this, "'" + label + "' 버튼 추가됨", Toast.LENGTH_SHORT).show();
                } else { // 수정 모드
                    // 기존 설정을 업데이트합니다. CustomButtonConfig가 식별자를 가지고 있다면 사용합니다.
                    // 현재는 ID가 없으므로, 해당 position의 객체를 삭제하고 새로 추가하거나,
                    // ButtonConfigManager에 update 메소드를 만들어야 합니다.
                    // 간단하게는 삭제 후 추가로 구현 (만약 CustomButtonConfig에 고유 ID가 없다면)
                    // buttonConfigManager.deleteButtonConfig(existingConfig); // 이 메소드가 있다면
                    // buttonConfigManager.addButtonConfig(newConfig);
                    // 또는 더 나은 방법:
                    buttonConfigManager.updateButtonConfig(position, newConfig); // 이 메소드를 ButtonConfigManager에 추가 필요
                    Toast.makeText(MainActivity.this, "'" + label + "' 버튼 수정됨", Toast.LENGTH_SHORT).show();
                }
                refreshButtonList();
            } catch (NumberFormatException e) {
                Toast.makeText(MainActivity.this, "숫자 입력이 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    // CustomButtonAdapter.OnButtonConfigActionListener 구현
    @Override
    public void onEditConfig(CustomButtonConfig config, int position) {
        showAddEditButtonDialog(config, position);
    }

    @Override
    public void onDeleteConfig(final CustomButtonConfig config, final int position) {
        new AlertDialog.Builder(this)
                .setTitle("삭제 확인")
                .setMessage("'" + config.getLabel() + "' 버튼을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    buttonConfigManager.deleteButtonConfig(position); // 위치 기반 삭제
                    Toast.makeText(MainActivity.this, "'" + config.getLabel() + "' 버튼 삭제됨", Toast.LENGTH_SHORT).show();
                    refreshButtonList();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 화면이 다시 활성화될 때 목록을 새로고침하여 다른 곳에서의 변경사항을 반영할 수 있음
        // (예: 설정 변경 후 서비스가 재시작되어 SharedPreferences가 변경된 경우)
        // refreshButtonList(); // 너무 빈번한 호출일 수 있으므로 필요에 따라 조절
    }
}
