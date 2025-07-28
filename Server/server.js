// 1. 필요한 라이브러리들을 불러옵니다.
const http = require('http');
const express = require('express');
const { Server } = require('socket.io');

// 2. Express 앱, HTTP 서버, Socket.IO 인스턴스를 생성합니다.
const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: { // 안드로이드 앱, PC 클라이언트 등 어디서든 접속할 수 있도록 CORS 허용
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// 3. 서버 포트를 설정합니다.
const PORT = 8079; // 안드로이드 앱에 설정된 포트와 동일하게 맞췄습니다.

// 4. 클라이언트들을 관리할 객체
const controllerClients = {}; // 제어하는 클라이언트 (안드로이드 앱)
const pcClients = {};       // 제어받는 클라이언트 (PC 프로그램)

// 5. 안드로이드 앱의 키 이름을 PC 클라이언트용 이름으로 변환하는 맵
const keyMapping = {
    "ARROW_UP": "up",
    "ARROW_DOWN": "down",
    "ARROW_LEFT": "left",
    "ARROW_RIGHT": "right",
    "ACTION_A": "a" // 예시: 안드로이드의 'ACTION_A'를 PC의 'a' 키로 매핑
    // 필요한 다른 키들을 여기에 추가하세요. (예: "ACTION_B": "b")
};

// 6. Socket.IO 연결 처리
io.on('connection', (socket) => {
    console.log(`[연결] 새로운 클라이언트 접속: ${socket.id}`);

    // PC 클라이언트를 등록하는 부분 (기존 코드와 유사)
    socket.on('register', (clientType) => {
        if (clientType === 'pc' || clientType === 'exe') { // 'pc' 또는 'exe' 타입으로 등록
            pcClients[socket.id] = socket;
            console.log(`[등록] PC 클라이언트: ${socket.id}`);
        }
    });

    // 안드로이드 앱에서 보낸 메시지 처리 ('message' 이벤트 또는 기본 이벤트)
    // 안드로이드의 OkHttp WebSocket은 'message'라는 기본 이벤트로 데이터를 보냅니다.
    socket.on('message', (data) => {
        // 이 소켓이 PC 클라이언트로 등록되지 않았다면 컨트롤러(안드로이드)로 간주
        if (!pcClients[socket.id]) {
            if (!controllerClients[socket.id]) {
                controllerClients[socket.id] = socket;
                console.log(`[등록] 안드로이드 컨트롤러: ${socket.id}`);
            }
            console.log(`[수신] 안드로이드 -> 서버: ${data}`);

            // 수신한 데이터를 파싱 (예: "INPUT:KEY_PRESS:ARROW_UP")
            const parts = data.toString().split(':');
            if (parts.length === 3 && parts[0] === 'INPUT' && parts[1] === 'KEY_PRESS') {
                const keyFromAndroid = parts[2];
                const keyForPC = keyMapping[keyFromAndroid] || keyFromAndroid.toLowerCase();

                // PC 클라이언트가 이해하는 JSON 형식으로 변환
                const command = {
                    action: "keyDown", // 안드로이드에서는 버튼 누르는 것만 있으므로 keyDown/keyUp을 함께 보낼 수 있습니다.
                    key: keyForPC,
                    mode: "game" // 기본 모드를 'game'으로 설정 (pydirectinput 사용)
                };

                // 모든 연결된 PC 클라이언트에게 변환된 데이터 전송
                forwardToPC('control', command);

                // 짧은 시간 후 자동으로 keyUp 이벤트를 보내 버튼을 뗀 효과를 줌
                setTimeout(() => {
                    const upCommand = { ...command, action: "keyUp" };
                    forwardToPC('control', upCommand);
                }, 50); // 50ms 후에 keyUp 실행
            }
        }
    });

    // 연결 종료 시 각 클라이언트 목록에서 제거
    socket.on('disconnect', () => {
        if (controllerClients[socket.id]) {
            delete controllerClients[socket.id];
            console.log(`[연결 종료] 안드로이드 컨트롤러: ${socket.id}`);
        } else if (pcClients[socket.id]) {
            delete pcClients[socket.id];
            console.log(`[연결 종료] PC 클라이언트: ${socket.id}`);
        }
    });
});

/**
 * 모든 PC 클라이언트에게 데이터를 전송하는 함수
 * @param {string} eventName - 이벤트 이름 (예: 'control')
 * @param {object} data - 전송할 데이터
 */
function forwardToPC(eventName, data) {
    if (Object.keys(pcClients).length > 0) {
        console.log(`[송신] 서버 -> PC: ${JSON.stringify(data)}`);
        // 연결된 모든 PC 클라이언트에게 데이터를 보냄
        for (const id in pcClients) {
            pcClients[id].emit(eventName, data);
        }
    } else {
        console.log("[경고] 제어 신호를 전달할 PC 클라이언트가 연결되어 있지 않습니다.");
    }
}

// 7. 서버 실행
server.listen(PORT, () => {
    console.log(`🚀 컨트롤러 서버가 포트 ${PORT}에서 실행 중입니다.`);
    console.log(`안드로이드 앱과 PC 클라이언트에서 접속을 기다립니다...`);
});
