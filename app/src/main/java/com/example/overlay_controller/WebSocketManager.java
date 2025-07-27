// WebSocketManager.java
package com.example.overlay_controller; // 본인의 패키지 이름

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
// import okio.ByteString; // 필요하다면 ByteString도 처리

public class WebSocketManager {

    private static final String TAG = "WebSocketManager";

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private String serverUrl;
    private ConnectionListener connectionListener; // 연결 상태를 알릴 리스너

    // 싱글톤으로 만들 수도 있지만, 여기서는 OverlayService가 관리하도록 단순하게 구성
    // public static WebSocketManager getInstance() { ... }

    public WebSocketManager() {
        this.httpClient = new OkHttpClient.Builder()
                // .pingInterval(30, TimeUnit.SECONDS) // 필요시 핑 설정
                .build();
    }

    // 연결 상태를 외부로 알리기 위한 인터페이스
    public interface ConnectionListener {
        void onConnected();
        void onMessageReceived(String message);
        void onDisconnected(String reason); // code, reason 등을 더 자세히 전달 가능
        void onError(String error);
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void connect(String url) {
        if (webSocket != null) {
            Log.d(TAG, "이미 연결되어 있거나 연결 시도 중입니다.");
            // 이미 연결되어 있다면 onConnected 콜백을 바로 호출해줄 수도 있음
            if (connectionListener != null) {
                // 실제 연결 상태를 확인하는 로직이 더 정교해야 함
                // 여기서는 단순 예시로, 이미 webSocket 객체가 있다면 연결된 것으로 간주
                // connectionListener.onConnected();
            }
            return;
        }
        this.serverUrl = url;
        Request request = new Request.Builder().url(serverUrl).build();
        Log.i(TAG, "WebSocket 연결 시도: " + serverUrl);

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                super.onOpen(ws, response);
                Log.i(TAG, "WebSocket 연결 성공!");
                if (connectionListener != null) {
                    connectionListener.onConnected();
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                super.onMessage(ws, text);
                Log.i(TAG, "서버로부터 메시지 수신: " + text);
                if (connectionListener != null) {
                    connectionListener.onMessageReceived(text);
                }
            }

            // onMessage (ByteString bytes) 도 필요하면 추가

            @Override
            public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
                super.onClosing(ws, code, reason);
                Log.i(TAG, "WebSocket 연결 닫히는 중: " + code + " / " + reason);
                ws.close(1000, null);
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
                super.onFailure(ws, t, response);
                String errorMessage = (t != null && t.getMessage() != null) ? t.getMessage() : "Unknown error";
                Log.e(TAG, "WebSocket 연결 실패: " + errorMessage, t);
                webSocket = null; // 실패 시 참조 해제
                if (connectionListener != null) {
                    connectionListener.onError("연결 실패: " + errorMessage);
                }
                // 여기서 재연결 로직을 넣을 수도 있음
            }

            @Override
            public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                super.onClosed(ws, code, reason);
                Log.i(TAG, "WebSocket 연결 닫힘: " + code + " / " + reason);
                webSocket = null; // 닫힌 후 참조 해제
                if (connectionListener != null) {
                    connectionListener.onDisconnected("연결 닫힘: " + reason + " (code: " + code + ")");
                }
            }
        });
    }

    public boolean sendMessage(String message) {
        if (webSocket != null) {
            Log.d(TAG, "서버로 메시지 전송 시도: " + message);
            return webSocket.send(message);
        } else {
            Log.w(TAG, "WebSocket이 연결되어 있지 않아 메시지 전송 불가: " + message);
            if (connectionListener != null) {
                // connectionListener.onError("서버 미연결 상태로 메시지 전송 시도");
            }
            return false;
        }
    }

    public void disconnect() {
        if (webSocket != null) {
            Log.i(TAG, "WebSocket 연결 종료 시도...");
            webSocket.close(1000, "User initiated disconnect"); // 1000: 정상 종료
            // webSocket = null; // onClosed 콜백에서 null로 설정됨
        }
    }

    public boolean isConnected() {
        return webSocket != null; // 더 정확하게는 WebSocket 내부 상태를 확인해야 하지만, 일단 단순하게
    }

    // OkHttpClient 리소스 정리는 OverlayService onDestroy에서 httpClient 자체를 관리하며 처리하거나,
    // WebSocketManager가 싱글톤이고 앱 전체 생명주기를 따른다면 여기에 추가할 수 있음
    public void cleanup() {
        Log.d(TAG, "WebSocketManager 리소스 정리 시도");
        disconnect(); // 현재 연결이 있다면 종료
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
            // httpClient.cache()?.close(); // 코틀린 스타일, 자바에서는 if (httpClient.cache() != null) httpClient.cache().close();
            try {
                if (httpClient.cache() != null) {
                    httpClient.cache().close();
                }
            } catch (Exception e) {
                Log.e(TAG, "OkHttp 캐시 닫기 오류", e);
            }
            Log.d(TAG, "OkHttpClient 리소스 정리 완료");
        }
    }
}
