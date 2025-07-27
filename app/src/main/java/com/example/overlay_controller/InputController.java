// InputController.java
package com.example.overlay_controller; // 본인의 패키지 이름

import android.content.Context;
import android.util.Log;
// import android.widget.Toast; // UI 피드백이 필요하면 Context를 받아 사용

public class InputController {

    private static final String TAG = "InputController";
    private WebSocketManager webSocketManager;
    // private Context context; // Toast 등 UI 피드백을 위해 필요하다면

    // 생성자에서 WebSocketManager 인스턴스를 주입받음
    public InputController(WebSocketManager manager /*, Context context */) {
        this.webSocketManager = manager;
        // this.context = context;
    }

    // OverlayService의 버튼 클릭 리스너에서 이 메소드를 호출
    public void processButtonPress(String buttonIdentifier) {
        Log.d(TAG, "버튼 입력 처리 시작: " + buttonIdentifier);

        // TODO: buttonIdentifier에 따라 실제 서버로 보낼 메시지 생성 로직 구현
        String messageToSend = "";

        switch (buttonIdentifier) {
            case "UP":
                messageToSend = "INPUT:KEY_PRESS:ARROW_UP";
                break;
            case "DOWN":
                messageToSend = "INPUT:KEY_PRESS:ARROW_DOWN";
                break;
            case "LEFT":
                messageToSend = "INPUT:KEY_PRESS:ARROW_LEFT";
                break;
            case "RIGHT":
                messageToSend = "INPUT:KEY_PRESS:ARROW_RIGHT";
                break;
            case "ACTION_A": // 예시: '액션' 버튼을 'A'로 명명
                messageToSend = "INPUT:KEY_PRESS:ACTION_A";
                break;
            // TODO: 필요한 다른 버튼들에 대한 case 추가
            // TODO: 사용자 정의 키 매핑 로직 추가 (SharedPreferences 등에서 설정값 로드)
            // TODO: 매크로 기능 로직 추가
            default:
                Log.w(TAG, "알 수 없는 버튼 식별자: " + buttonIdentifier);
                return; // 처리할 수 없는 버튼이면 여기서 종료
        }

        if (!messageToSend.isEmpty()) {
            boolean success = webSocketManager.sendMessage(messageToSend);
            if (success) {
                Log.i(TAG, "메시지 전송 요청 성공: " + messageToSend);
                // if (context != null) Toast.makeText(context, "전송: " + buttonIdentifier, Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "메시지 전송 요청 실패: " + messageToSend);
                // if (context != null) Toast.makeText(context, "전송 실패: " + buttonIdentifier, Toast.LENGTH_SHORT).show();
                // 여기서 연결 재시도 등을 webSocketManager를 통해 간접적으로 요청할 수도 있음
                // 또는 webSocketManager의 ConnectionListener를 통해 연결 상태를 확인하고 UI에 피드백
            }
        }
    }

    // 향후 사용자 정의 키 설정 로드/저장 메소드 추가 가능
    // public void loadKeyMappings() { ... }
    // public void saveKeyMappings() { ... }
}
