
const http = require('http');
const express = require('express');
const { Server } = require('socket.io');

// 2. Express 앱, HTTP 서버, Socket.IO 인스턴스를 생성합니다.
const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

// 3. 서버 포트를 설정합니다.
const PORT = 8079;

// 4. 클라이언트들을 관리할 객체
const controllerClients = {}; // 제어하는 클라이언트 (안드로이드 앱)
const pcClients = {};       // 제어받는 클라이언트 (PC 프로그램)

// 5. 안드로이드 앱의 키 이름을 PC 클라이언트용 이름으로 변환하는 맵
const keyMapping = {
    "ARROW_UP": "up",
    "ARROW_DOWN": "down",
    "ARROW_LEFT": "left",
    "ARROW_RIGHT": "right",
    "ACTION_A": "a",
    // 필요한 다른 키들을 여기에 추가하세요.
    "↑": "up",
    "↓": "down",
    "←": "left",
    "→": "right"
};

// 6. Socket.IO 연결 처리
io.on('connection', (socket) => {
    console.log(`[연결] 새로운 클라이언트 접속: ${socket.id}`);

    socket.on('register', (data) => { // data는 이제 객체일 수 있음
        let clientType = "";
        if (typeof data === 'string') { // 이전 Python 클라이언트 호환
            clientType = data;
        } else if (typeof data === 'object' && data && data.type) { // 안드로이드 JSON 형식
            clientType = data.type;
        }

        if (clientType === 'pc' || clientType === 'exe') {
            pcClients[socket.id] = socket;
            console.log(`[등록] PC 클라이언트 (${clientType}): ${socket.id}`);
        } else if (clientType === 'android_controller') {
            controllerClients[socket.id] = socket;
            console.log(`[등록] 안드로이드 컨트롤러: ${socket.id}`);
        } else {
            console.log(`[등록 시도] 알 수 없는 클라이언트 타입 (${clientType}): ${socket.id}`);
        }
    });

    // 안드로이드 앱에서 'androidControl' 이벤트로 JSON 데이터를 보낼 경우
    socket.on('androidControl', (commandData) => { // commandData는 JSON 객체여야 함
        if (controllerClients[socket.id]) { // 등록된 컨트롤러가 보낸 메시지만 처리
            console.log(`[수신] 안드로이드 -> 서버 (${socket.id}):`, commandData);

            try {
                // commandData가 이미 객체이므로 JSON.parse 불필요
                if (commandData && commandData.type === 'INPUT' && commandData.key) {
                    const keyFromAndroid = commandData.key;
                    const keyForPC = keyMapping[keyFromAndroid] || keyFromAndroid.toLowerCase();
                    let pcAction = "";

                    if (commandData.event === 'KEY_DOWN') {
                        pcAction = "keyDown";
                    } else if (commandData.event === 'KEY_UP') {
                        pcAction = "keyUp";
                    } else if (commandData.event === 'KEY_PRESS') { // KEY_PRESS는 keyDown 후 자동 keyUp
                        pcAction = "keyDown"; // 먼저 keyDown
                    } else {
                        console.log(`[경고] 알 수 없는 입력 이벤트: ${commandData.event}`);
                        return;
                    }

                    const pcCommand = {
                        action: pcAction,
                        key: keyForPC,
                        mode: commandData.mode || "game" // 안드로이드에서 mode를 보낼 수도 있음
                    };

                    forwardToPC('control', pcCommand);

                    // KEY_PRESS의 경우, 짧은 시간 후 자동으로 keyUp (안드로이드에서 KEY_DOWN/KEY_UP을 명확히 보내면 이 로직 불필요)
                    if (commandData.event === 'KEY_PRESS' && pcAction === "keyDown") {
                        setTimeout(() => {
                            const upCommand = { ...pcCommand, action: "keyUp" };
                            forwardToPC('control', upCommand);
                            console.log(`[자동 송신] KEY_PRESS에 대한 keyUp -> PC: ${JSON.stringify(upCommand)}`);
                        }, 50); // 50ms 후에 keyUp 실행
                    }

                } else if (commandData && commandData.type === 'MOUSE') { // 예시: 마우스 이벤트 처리
                    // 필요한 마우스 제어 로직 추가 (Python 클라이언트의 handle_input 참조)
                    // 예: commandData.event === 'MOUSE_MOVE', commandData.x, commandData.y 등
                    // forwardToPC('control', processedMouseCommand);
                    console.log(`[수신] 안드로이드 마우스 이벤트:`, commandData);
                } else {
                    console.log(`[경고] 처리할 수 없는 안드로이드 제어 데이터 형식:`, commandData);
                }
            } catch (e) {
                console.error(`[오류] 안드로이드 제어 데이터 처리 중 예외 발생 (${socket.id}):`, e);
                console.error("수신된 데이터:", commandData);
            }
        } else {
            console.log(`[경고] 미등록 클라이언트(${socket.id})로부터 'androidControl' 이벤트 수신:`, commandData);
        }
    });

    socket.on('disconnect', () => {
        if (controllerClients[socket.id]) {
            delete controllerClients[socket.id];
            console.log(`[연결 종료] 안드로이드 컨트롤러: ${socket.id}`);
        } else if (pcClients[socket.id]) {
            delete pcClients[socket.id];
            console.log(`[연결 종료] PC 클라이언트: ${socket.id}`);
        } else {
            console.log(`[연결 종료] 미등록 클라이언트: ${socket.id}`);
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
        console.log(`[송신] 서버 -> PC (${eventName}): ${JSON.stringify(data)}`);
        for (const id in pcClients) {
            pcClients[id].emit(eventName, data);
        }
    } else {
        console.log(`[경고] 제어 신호를 전달할 PC 클라이언트가 연결되어 있지 않습니다.`);
    }
}

// 7. 서버 실행
server.listen(PORT, () => {
    console.log(`🚀 컨트롤러 서버가 포트 ${PORT}에서 실행 중입니다.`);
    console.log(`안드로이드 앱과 PC 클라이언트에서 접속을 기다립니다...`);
});
