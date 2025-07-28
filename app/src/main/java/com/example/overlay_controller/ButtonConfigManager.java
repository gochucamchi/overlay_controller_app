package com.example.overlay_controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // CustomButtonConfig에 ID를 추가한다면 사용 가능
import java.util.concurrent.CopyOnWriteArrayList;

public class ButtonConfigManager {

    private static final String TAG = "ButtonConfigManager";
    private static final String PREFS_NAME = "CustomButtonPrefs";
    private static final String KEY_BUTTON_CONFIGS = "button_configs_json";

    private SharedPreferences sharedPreferences;
    private Gson gson;
    private List<CustomButtonConfig> currentConfigs; // 메모리에 캐시된 설정 리스트

    public ButtonConfigManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        currentConfigs = loadButtonConfigsInternal(); // 앱 시작 시 SharedPreferences에서 로드
        if (currentConfigs == null) {
            currentConfigs = new CopyOnWriteArrayList<>(); // 로드 실패 또는 최초 실행 시 빈 리스트
        }
    }

    // 내부적으로 SharedPreferences에서 설정을 로드하는 메소드
    private List<CustomButtonConfig> loadButtonConfigsInternal() {
        String json = sharedPreferences.getString(KEY_BUTTON_CONFIGS, null);
        if (json == null || json.isEmpty()) {
            Log.d(TAG, "SharedPreferences에 저장된 버튼 설정 없음.");
            return new CopyOnWriteArrayList<>(); // 저장된 설정이 없으면 빈 리스트 반환
        }
        try {
            Type type = new TypeToken<ArrayList<CustomButtonConfig>>() {}.getType();
            // TypeToken을 사용하여 제네릭 타입 정보를 Gson에 전달
            List<CustomButtonConfig> loadedConfigs = gson.fromJson(json, type);
            Log.d(TAG, "SharedPreferences에서 " + (loadedConfigs != null ? loadedConfigs.size() : 0) + "개의 버튼 설정을 로드했습니다.");
            // CopyOnWriteArrayList로 감싸서 동시성 문제에 더 안전하게 만듦 (선택 사항)
            return new CopyOnWriteArrayList<>(loadedConfigs != null ? loadedConfigs : new ArrayList<>());
        } catch (Exception e) {
            Log.e(TAG, "JSON에서 버튼 설정 로드 중 오류 발생", e);
            return new CopyOnWriteArrayList<>(); // 오류 발생 시 빈 리스트 반환
        }
    }

    // 내부적으로 현재 설정을 SharedPreferences에 저장하는 메소드
    private void saveButtonConfigsInternal() {
        try {
            String json = gson.toJson(currentConfigs); // 현재 currentConfigs 리스트를 JSON 문자열로 변환
            sharedPreferences.edit().putString(KEY_BUTTON_CONFIGS, json).apply(); // 비동기 저장
            Log.d(TAG, "SharedPreferences에 " + currentConfigs.size() + "개의 버튼 설정을 저장했습니다.");
        } catch (Exception e) {
            Log.e(TAG, "JSON으로 버튼 설정 저장 중 오류 발생", e);
        }
    }

    /**
     * 현재 저장된 모든 버튼 설정을 반환합니다.
     * 반환되는 리스트는 외부에서 직접 수정해도 내부 currentConfigs에 영향을 주지 않는 복사본입니다.
     * @return 버튼 설정 리스트의 복사본
     */
    public List<CustomButtonConfig> getAllButtonConfigs() {
        // 외부에서의 수정을 방지하기 위해 항상 새로운 리스트(복사본)를 반환
        return new ArrayList<>(currentConfigs);
    }

    /**
     * 새로운 버튼 설정을 추가합니다.
     * @param config 추가할 버튼 설정
     */
    public void addButtonConfig(CustomButtonConfig config) {
        if (config == null) {
            Log.w(TAG, "null 설정을 추가하려고 시도했습니다.");
            return;
        }
        // CustomButtonConfig에 고유 ID가 있다면 여기서 ID 생성 및 할당 로직 추가 가능
        // 예: config.setId(UUID.randomUUID().toString());

        currentConfigs.add(config); // 메모리 내 리스트에 추가
        saveButtonConfigsInternal(); // 변경 사항을 SharedPreferences에 저장
        Log.i(TAG, "버튼 설정 추가됨: " + config.getLabel());
    }

    /**
     * 지정된 인덱스의 버튼 설정을 업데이트합니다.
     * @param index 업데이트할 설정의 리스트 내 인덱스
     * @param newConfig 새로운 버튼 설정 객체
     * @return 업데이트 성공 시 true, 실패 시 false
     */
    public boolean updateButtonConfig(int index, CustomButtonConfig newConfig) {
        if (newConfig == null) {
            Log.w(TAG, "null 설정으로 업데이트하려고 시도했습니다.");
            return false;
        }
        if (index >= 0 && index < currentConfigs.size()) {
            // CustomButtonConfig에 ID가 있다면, newConfig의 ID를 기존 config의 ID와 동일하게 설정하는 것이 좋을 수 있음
            // 또는 ID를 기준으로 찾아서 업데이트
            currentConfigs.set(index, newConfig); // 메모리 내 리스트 업데이트
            saveButtonConfigsInternal(); // 변경 사항을 SharedPreferences에 저장
            Log.i(TAG, "인덱스 " + index + "의 버튼 설정 업데이트됨: " + newConfig.getLabel());
            return true;
        }
        Log.w(TAG, "인덱스 " + index + "가 유효하지 않아 버튼 설정을 업데이트할 수 없습니다. 현재 크기: " + currentConfigs.size());
        return false;
    }

    /**
     * 특정 ID를 가진 버튼 설정을 삭제합니다. (CustomButtonConfig에 getId()가 있다고 가정)
     * 이 메소드는 ID 기반 삭제가 필요할 경우 사용합니다. 현재 MainActivity는 인덱스 기반 삭제를 사용 중입니다.
     * @param buttonId 삭제할 버튼의 ID
     * @return 삭제 성공 시 true, 해당 ID를 찾지 못하면 false
     */
    public boolean deleteButtonConfigById(String buttonId) {
        if (buttonId == null || buttonId.isEmpty()) {
            Log.w(TAG, "null 또는 빈 ID로 설정을 삭제하려고 시도했습니다.");
            return false;
        }
        for (int i = 0; i < currentConfigs.size(); i++) {
            // CustomButtonConfig에 getId() 메소드가 있어야 함. 없다면 다른 식별 방법 필요.
            // if (currentConfigs.get(i).getId().equals(buttonId)) {
            //     CustomButtonConfig removed = currentConfigs.remove(i);
            //     saveButtonConfigsInternal();
            //     Log.i(TAG, "ID '" + buttonId + "' 버튼 설정 삭제됨: " + removed.getLabel());
            //     return true;
            // }
        }
        // Log.w(TAG, "ID '" + buttonId + "'에 해당하는 버튼 설정을 찾지 못해 삭제할 수 없습니다.");
        return false; // ID 기반 삭제는 현재 CustomButtonConfig에 ID 필드가 없으므로 주석 처리 또는 수정 필요
    }


    /**
     * 지정된 인덱스의 버튼 설정을 삭제합니다.
     * @param index 삭제할 설정의 리스트 내 인덱스
     * @return 삭제 성공 시 true, 실패 시 false
     */
    public boolean deleteButtonConfig(int index) {
        if (index >= 0 && index < currentConfigs.size()) {
            CustomButtonConfig removed = currentConfigs.remove(index); // 메모리 내 리스트에서 삭제
            saveButtonConfigsInternal(); // 변경 사항을 SharedPreferences에 저장
            Log.i(TAG, "인덱스 " + index + "의 버튼 설정 삭제됨: " + removed.getLabel());
            return true;
        }
        Log.w(TAG, "인덱스 " + index + "가 유효하지 않아 버튼 설정을 삭제할 수 없습니다. 현재 크기: " + currentConfigs.size());
        return false;
    }

    /**
     * 특정 ID를 가진 버튼 설정을 가져옵니다. (CustomButtonConfig에 getId()가 있다고 가정)
     * @param buttonId 가져올 버튼의 ID
     * @return 찾은 버튼 설정 객체, 없으면 null
     */
    public CustomButtonConfig getButtonConfigById(String buttonId) {
        if (buttonId == null || buttonId.isEmpty()) return null;
        for (CustomButtonConfig config : currentConfigs) {
            // CustomButtonConfig에 getId() 메소드가 있어야 함.
            // if (config.getId().equals(buttonId)) {
            //     return config; // 주의: 반환된 객체를 직접 수정하면 currentConfigs도 변경될 수 있음
            // }
        }
        return null; // ID 기반 검색은 현재 CustomButtonConfig에 ID 필드가 없으므로 주석 처리 또는 수정 필요
    }

    /**
     * 모든 버튼 설정을 삭제합니다. (초기화 등에 사용)
     */
    public void clearAllButtonConfigs() {
        currentConfigs.clear(); // 메모리 내 리스트 비우기
        saveButtonConfigsInternal(); // 변경 사항 (빈 리스트)을 SharedPreferences에 저장
        Log.i(TAG, "모든 버튼 설정이 삭제되었습니다.");
    }
}
