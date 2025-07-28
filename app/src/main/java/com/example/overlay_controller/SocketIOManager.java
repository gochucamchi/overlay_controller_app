// SocketIOManager.java
package com.example.overlay_controller; // 본인의 패키지 이름

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URISyntaxException;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketIOManager {

    private static final String TAG = "SocketIOManager";

    private Socket mSocket;
    private ConnectionListener connectionListener;
    private String serverUrl; // 서버 URL 저장

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected(String reason);
        void onError(String error);
        // 메시지 수신은 이제 특정 이벤트 기반으로 처리하거나,
        // 서버가 일반 메시지(socket.send)를 보낼 경우를 대비해 남겨둘 수 있습니다.
        // void onMessageReceived(String message);
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public SocketIOManager() {
        // OkHttpClient는 Socket.IO 내부에서 관리되므로 직접 생성할 필요가 줄어듭니다.
    }

    public void connect(String url) {
        if (mSocket != null && mSocket.connected()) {
            Log.d(TAG, "이미 연결되어 있습니다.");
            if (connectionListener != null) {
                connectionListener.onConnected(); // 이미 연결된 상태임을 알림
            }
            return;
        }
        this.serverUrl = url;
        try {
            // Socket.IO는 URL에 http 또는 https를 사용합니다.
            // 서버의 io 생성 시 cors 설정을 했으므로 http로 시작해도 됩니다.
            // 예: "http://gocam.p-e.kr:8079"
            IO.Options opts = new IO.Options();
            // opts.forceNew = true; // 필요시 새 연결 강제
            // opts.reconnection = true; // 자동 재연결 활성화 (기본값 true)
            // opts.transports = new String[]{"websocket"}; // 특정 전송 방식 강제 시

            mSocket = IO.socket(serverUrl, opts);

            mSocket.on(Socket.EVENT_CONNECT, args -> {
                Log.i(TAG, "Socket.IO 서버에 연결되었습니다!");
                if (connectionListener != null) {
                    connectionListener.onConnected();
                }
                // 연결 성공 후 서버에 안드로이드 컨트롤러로 등록
                // "type"을 추가하여 어떤 종류의 클라이언트인지 명시
                try {
                    JSONObject registerData = new JSONObject();
                    registerData.put("type", "android_controller");
                    mSocket.emit("register", registerData); // 서버의 'register' 이벤트로 전송
                    Log.d(TAG, "서버에 'android_controller'로 등록 시도.");
                } catch (JSONException e) {
                    Log.e(TAG, "등록 데이터 생성 실패", e);
                }
            });

            mSocket.on(Socket.EVENT_DISCONNECT, args -> {
                Log.i(TAG, "Socket.IO 서버와 연결이 끊어졌습니다. 이유: " + (args.length > 0 ? args[0].toString() : "N/A"));
                if (connectionListener != null) {
                    connectionListener.onDisconnected(args.length > 0 ? args[0].toString() : "서버 연결 끊김");
                }
            });

            mSocket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                String errorMsg = "Socket.IO 연결 오류";
                if (args.length > 0 && args[0] instanceof Exception) {
                    errorMsg += ": " + ((Exception)args[0]).getMessage();
                } else if (args.length > 0) {
                    errorMsg += ": " + args[0].toString();
                }
                Log.e(TAG, errorMsg, (args.length > 0 && args[0] instanceof Throwable) ? (Throwable)args[0] : null);
                if (connectionListener != null) {
                    connectionListener.onError(errorMsg);
                }
            });

            // 서버에서 보낼 수 있는 사용자 정의 이벤트를 리스닝할 수 있습니다.
            // 예: 서버가 mSocket.emit("server_message", "hello from server"); 를 호출한 경우
            /*
            mSocket.on("server_message", args -> {
                if (args.length > 0 && args[0] instanceof String) {
                    String message = (String) args[0];
                    Log.i(TAG, "서버로부터 메시지 수신 (server_message): " + message);
                    // if (connectionListener != null) connectionListener.onMessageReceived(message);
                }
            });
            */

            Log.i(TAG, "Socket.IO 연결 시도: " + serverUrl);
            mSocket.connect();

        } catch (URISyntaxException e) {
            Log.e(TAG, "잘못된 서버 URL입니다.", e);
            if (connectionListener != null) {
                connectionListener.onError("잘못된 서버 URL: " + e.getMessage());
            }
        }
    }

    /**
     * 서버로 제어 명령 (JSON 객체)을 전송합니다.
     * @param eventName 서버에서 리스닝할 이벤트 이름 (예: "androidControl")
     * @param data      전송할 JSONObject 데이터
     * @return 전송 성공 여부
     */
    public boolean sendCommand(String eventName, JSONObject data) {
        if (mSocket != null && mSocket.connected()) {
            Log.d(TAG, "서버로 데이터 전송 (" + eventName + "): " + data.toString());
            mSocket.emit(eventName, data);
            return true;
        } else {
            Log.w(TAG, "Socket.IO가 연결되어 있지 않아 데이터 전송 불가: " + data.toString());
            if (connectionListener != null) {
                // connectionListener.onError("서버 미연결 상태로 메시지 전송 시도");
            }
            return false;
        }
    }

    /**
     * 안드로이드 앱에서 키 이벤트를 서버로 전송하는 예시 메소드
     * @param keyEventType "KEY_DOWN" 또는 "KEY_UP"
     * @param androidKeyName 안드로이드 앱 내부에서 사용하는 키 이름 (예: "ARROW_UP")
     */
    public void sendKeyEvent(String keyEventType, String androidKeyName) {
        try {
            JSONObject keyEventData = new JSONObject();
            keyEventData.put("type", "INPUT");      // 데이터의 종류 (예: 입력, 설정 등)
            keyEventData.put("event", keyEventType); // 이벤트 종류 (예: KEY_DOWN, KEY_UP, MOUSE_MOVE)
            keyEventData.put("key", androidKeyName); // 실제 키 값
            // keyEventData.put("mode", "game"); // 필요시 모드 지정
            sendCommand("androidControl", keyEventData); // 서버의 'androidControl' 이벤트로 전송
        } catch (JSONException e) {
            Log.e(TAG, "키 이벤트 JSON 생성 실패", e);
        }
    }


    public void disconnect() {
        if (mSocket != null) {
            Log.i(TAG, "Socket.IO 연결 종료 시도...");
            mSocket.disconnect();
            // mSocket.off(); // 모든 리스너 제거 (필요시)
            // mSocket = null; // onDisconnected 콜백 이후에 null로 설정하는 것이 안전할 수 있음
        }
    }

    public boolean isConnected() {
        return mSocket != null && mSocket.connected();
    }

    // cleanup 메소드는 Socket.IO에서는 OkHttp처럼 직접 관리할 부분이 적어지므로
    // 주로 disconnect() 호출이 중요해집니다.
    public void cleanup() {
        Log.d(TAG, "SocketIOManager 리소스 정리 시도");
        disconnect();
    }
}
