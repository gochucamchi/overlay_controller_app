package com.example.overlay_controller; // 패키지 이름은 실제 프로젝트에 맞게 확인

import java.util.Objects;

public class CustomButtonConfig {
    private String label;
    private String keyName; // 서버로 전송될 키 값 (예: "KEY_A", "SPACE", "MOUSE_LEFT_CLICK")
    private float xPositionPercent; // 화면 너비에 대한 X 좌표 비율 (0.0 ~ 1.0)
    private float yPositionPercent; // 화면 높이에 대한 Y 좌표 비율 (0.0 ~ 1.0)
    private float widthPercent;     // 화면 너비에 대한 버튼 너비 비율 (0.0 ~ 1.0)
    private float heightPercent;    // 화면 높이에 대한 버튼 높이 비율 (0.0 ~ 1.0)
    // 필요하다면 고유 ID를 위한 필드 추가 가능
    // private String id;

    // 기본 생성자 (JSON 직렬화/역직렬화를 위해 필요할 수 있음)
    public CustomButtonConfig() {
    }

    // <<<< 문제의 생성자 >>>>
    // 이 생성자가 있는지, 매개변수 타입과 순서가 일치하는지 확인하세요.
    public CustomButtonConfig(String label, String keyName, float xPositionPercent, float yPositionPercent, float widthPercent, float heightPercent) {
        this.label = label;
        this.keyName = keyName;
        this.xPositionPercent = xPositionPercent;
        this.yPositionPercent = yPositionPercent;
        this.widthPercent = widthPercent;
        this.heightPercent = heightPercent;
        // if (id == null) { // ID를 사용한다면 여기서 초기화
        //     this.id = java.util.UUID.randomUUID().toString();
        // }
    }

    // Getter 메소드들
    public String getLabel() {
        return label;
    }

    public String getKeyName() {
        return keyName;
    }

    public float getXPositionPercent() {
        return xPositionPercent;
    }

    public float getYPositionPercent() {
        return yPositionPercent;
    }

    public float getWidthPercent() {
        return widthPercent;
    }

    public float getHeightPercent() {
        return heightPercent;
    }

    // Setter 메소드들 (필요에 따라 추가)
    public void setLabel(String label) {
        this.label = label;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public void setXPositionPercent(float xPositionPercent) {
        this.xPositionPercent = xPositionPercent;
    }

    public void setYPositionPercent(float yPositionPercent) {
        this.yPositionPercent = yPositionPercent;
    }

    public void setWidthPercent(float widthPercent) {
        this.widthPercent = widthPercent;
    }

    public void setHeightPercent(float heightPercent) {
        this.heightPercent = heightPercent;
    }

    // (선택 사항) ID를 사용한다면 Getter/Setter 추가
    // public String getId() { return id; }
    // public void setId(String id) { this.id = id; }


    // (선택 사항) equals() and hashCode() 구현 - 리스트에서 객체 비교 시 유용
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomButtonConfig that = (CustomButtonConfig) o;
        return Float.compare(that.xPositionPercent, xPositionPercent) == 0 &&
                Float.compare(that.yPositionPercent, yPositionPercent) == 0 &&
                Float.compare(that.widthPercent, widthPercent) == 0 &&
                Float.compare(that.heightPercent, heightPercent) == 0 &&
                Objects.equals(label, that.label) &&
                Objects.equals(keyName, that.keyName);
        // Objects.equals(id, that.id); // ID를 사용한다면 비교에 추가
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, keyName, xPositionPercent, yPositionPercent, widthPercent, heightPercent); // ID를 사용한다면 해시코드에 추가
    }

    // (선택 사항) toString() 구현 - 디버깅 시 유용
    @Override
    public String toString() {
        return "CustomButtonConfig{" +
                "label='" + label + '\'' +
                ", keyName='" + keyName + '\'' +
                ", xPos=" + xPositionPercent +
                ", yPos=" + yPositionPercent +
                ", width=" + widthPercent +
                ", height=" + heightPercent +
                // ", id='" + id + '\'' + // ID를 사용한다면 추가
                '}';
    }
}
