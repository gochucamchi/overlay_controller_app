// /com/example/overlay_controller/ButtonConfigManager.java
package com.example.overlay_controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class ButtonConfigManager {

    private static final String TAG = "ButtonConfigManager";
    private static final String PREFS_NAME = "CustomButtonPrefs";
    private static final String KEY_BUTTON_CONFIGS = "button_configs_json";

    private final SharedPreferences sharedPreferences;
    private final Gson gson;
    private List<CustomButtonConfig> currentConfigs;

    public ButtonConfigManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        currentConfigs = loadButtonConfigsInternal();
    }

    private List<CustomButtonConfig> loadButtonConfigsInternal() {
        String json = sharedPreferences.getString(KEY_BUTTON_CONFIGS, null);
        if (json == null || json.isEmpty()) {
            return new CopyOnWriteArrayList<>();
        }
        try {
            Type type = new TypeToken<ArrayList<CustomButtonConfig>>() {}.getType();
            List<CustomButtonConfig> loadedConfigs = gson.fromJson(json, type);
            return new CopyOnWriteArrayList<>(loadedConfigs != null ? loadedConfigs : new ArrayList<>());
        } catch (Exception e) {
            return new CopyOnWriteArrayList<>();
        }
    }

    private void saveButtonConfigsInternal() {
        try {
            String json = gson.toJson(currentConfigs);
            sharedPreferences.edit().putString(KEY_BUTTON_CONFIGS, json).apply();
        } catch (Exception e) {
            // Handle save exception
        }
    }

    public List<CustomButtonConfig> getAllButtonConfigs() {
        return new ArrayList<>(currentConfigs);
    }

    public void addButtonConfig(CustomButtonConfig config) {
        if (config == null) return;
        currentConfigs.add(config);
        saveButtonConfigsInternal();
    }

    public boolean updateButtonConfig(int index, CustomButtonConfig newConfig) {
        if (newConfig == null || index < 0 || index >= currentConfigs.size()) {
            return false;
        }
        currentConfigs.set(index, newConfig);
        saveButtonConfigsInternal();
        return true;
    }

    public boolean deleteButtonConfig(int index) {
        if (index < 0 || index >= currentConfigs.size()) {
            return false;
        }
        currentConfigs.remove(index);
        saveButtonConfigsInternal();
        return true;
    }

    // 추가된 메소드
    public boolean deleteButtonConfigByLabel(String label) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        for (int i = 0; i < currentConfigs.size(); i++) {
            if (Objects.equals(currentConfigs.get(i).getLabel(), label)) {
                currentConfigs.remove(i);
                saveButtonConfigsInternal();
                return true;
            }
        }
        return false;
    }
}