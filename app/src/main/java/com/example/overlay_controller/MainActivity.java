// /com/example/overlay_controller/MainActivity.java
package com.example.overlay_controller;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        startOverlayService();
                    } else {
                        Toast.makeText(this, "오버레이 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                    }
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                checkOverlayPermissionAndStartService();
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonConfigManager = new ButtonConfigManager(this);

        recyclerView = findViewById(R.id.recyclerView_custom_buttons);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        currentConfigs = buttonConfigManager.getAllButtonConfigs();
        adapter = new CustomButtonAdapter(this, currentConfigs, this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_button);
        fab.setOnClickListener(view -> showAddEditButtonDialog(null, -1));

        Button startServiceButton = findViewById(R.id.button_start_service);
        startServiceButton.setOnClickListener(v -> checkNotificationPermissionAndStart());

        Button stopServiceButton = findViewById(R.id.button_stop_service);
        stopServiceButton.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            serviceIntent.setAction(OverlayService.ACTION_STOP);
            startService(serviceIntent);
            Toast.makeText(this, "서비스 중지 요청됨", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                checkOverlayPermissionAndStartService();
            }
        } else {
            checkOverlayPermissionAndStartService();
        }
    }


    private void checkOverlayPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            startOverlayService();
        }
    }

    private void startOverlayService() {
        Intent serviceIntent = new Intent(this, OverlayService.class);
        serviceIntent.setAction(OverlayService.ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "가상 컨트롤러 서비스 시작됨", Toast.LENGTH_SHORT).show();
    }


    private void refreshButtonList() {
        currentConfigs = buttonConfigManager.getAllButtonConfigs();
        adapter.updateData(currentConfigs);
    }


    private void showAddEditButtonDialog(final CustomButtonConfig existingConfig, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_edit_button, null);
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
                if (existingConfig == null) { // 새로 추가하는 경우
                    String label = "새 버튼 " + (currentConfigs.size() + 1);

                    CustomButtonConfig newConfig = new CustomButtonConfig(
                            label, "미지정", 0.4f, 0.4f, 0.1f, 0.1f);

                    buttonConfigManager.addButtonConfig(newConfig);
                    Toast.makeText(MainActivity.this, "'" + label + "' 버튼 추가됨", Toast.LENGTH_SHORT).show();

                } else { // 기존 버튼을 수정하는 경우
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
                    if (xPos < 0 || xPos > 1 || yPos < 0 || yPos > 1 || width <= 0 || width > 1 || height <= 0 || height > 1) {
                        Toast.makeText(MainActivity.this, "위치/크기 값은 0.0과 1.0 사이여야 합니다.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    CustomButtonConfig updatedConfig = new CustomButtonConfig(label, keyName, xPos, yPos, width, height);
                    buttonConfigManager.updateButtonConfig(position, updatedConfig);
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
                    buttonConfigManager.deleteButtonConfig(position);
                    Toast.makeText(MainActivity.this, "'" + config.getLabel() + "' 버튼 삭제됨", Toast.LENGTH_SHORT).show();
                    refreshButtonList();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshButtonList();
    }
}