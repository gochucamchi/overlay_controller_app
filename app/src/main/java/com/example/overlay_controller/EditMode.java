package com.example.overlay_controller;

public enum EditMode {
    NORMAL,      // 일반 모드 (키 입력 전송)
    ADD_MOVE,    // 추가 및 이동 모드
    ASSIGN_KEY,  // 키 지정 모드
    RESIZE,      // 크기 조절 모드
    DELETE       // 삭제 모드
}