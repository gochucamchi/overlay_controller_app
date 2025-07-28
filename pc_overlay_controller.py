import socketio
import pydirectinput
import pyautogui
import time
import threading
import tkinter as tk
from tkinter import font as tkFont  # 폰트 관리를 위해 import
import os

# --- 기존 코드 (handle_input 함수 등)는 그대로 둡니다 ---
# 초기 설정
pydirectinput.FAILSAFE = False
pyautogui.FAILSAFE = False

# PC를 제어하는 핵심 로직 함수
def handle_input(command):
    # (이 안의 내용은 이전과 동일하므로 생략)
    action = command.get("action")
    mode = command.get("mode", "game")

    try:
        if action == "mouseMove":
            pydirectinput.moveRel(command.get("x"), command.get("y"), relative=True)
        elif action == "mouseMoveTo":
            screen_width, screen_height = pyautogui.size()
            target_x = int(command.get("x") / command.get("width") * screen_width)
            target_y = int(command.get("y") / command.get("height") * screen_height)
            pyautogui.moveTo(target_x, target_y)
        elif action == "keyDown":
            pydirectinput.keyDown(command.get("key")) if mode == "game" else pyautogui.keyDown(command.get("key"))
        elif action == "keyUp":
            pydirectinput.keyUp(command.get("key")) if mode == "game" else pyautogui.keyUp(command.get("key"))
        elif action == "mouseDown":
            pydirectinput.mouseDown(button=command.get("button")) if mode == "game" else pyautogui.mouseDown(button=command.get("button"))
        elif action == "mouseUp":
            pydirectinput.mouseUp(button=command.get("button")) if mode == "game" else pyautogui.mouseUp(button=command.get("button"))
        elif action == "scroll":
            delta = command.get("delta")
            scroll_amount = -1 if delta > 0 else 1
            pydirectinput.scroll(scroll_amount)
    except Exception as e:
        print(f"Error handling action '{action}': {e}")


# 🔽🔽🔽 GUI 생성 함수가 아래와 같이 수정됩니다 🔽🔽🔽

def create_gui():
    """사용자 인터페이스(GUI)를 생성하고 실행하는 함수"""
    window = tk.Tk()
    window.title("원격 제어 클라이언트")
    window.geometry("400x220") # 창 크기 조정
    window.resizable(False, False)

    # 폰트 설정
    default_font = tkFont.Font(family="맑은 고딕", size=10)
    status_font = tkFont.Font(family="맑은 고딕", size=12, weight="bold")

    # --- 💡 1. 서버 주소 입력 파트 ---
    frame_connect = tk.Frame(window, pady=10)
    frame_connect.pack(fill="x", padx=20)

    label_server = tk.Label(frame_connect, text="서버 주소:", font=default_font)
    label_server.pack(side="left")

    # 주소 입력을 위한 Entry 위젯
    entry_server = tk.Entry(frame_connect, font=default_font, width=30)
    # 기본값 설정
    entry_server.insert(0, "http://gocam.p-e.kr:8079/")
    entry_server.pack(side="left", padx=5, expand=True, fill="x")

    # --- 💡 2. 상태 표시 및 정보 파트 ---
    label_status = tk.Label(window, text="✅ 연결 대기 중...", font=status_font, pady=10)
    label_status.pack()

    # --- 💡 3. 연결 시작 함수 및 연결 버튼 ---
    def start_connection():
        """연결 버튼을 눌렀을 때 호출될 함수 (네트워크 연결은 별도 스레드에서 실행)"""
        server_url = entry_server.get()
        if not server_url:
            label_status.config(text="❌ 서버 주소를 입력하세요.", fg="red")
            return

        # 연결 시도 중에는 입력창과 버튼을 비활성화
        entry_server.config(state="disabled")
        button_connect.config(state="disabled")
        label_status.config(text=f"⏳ 서버 연결 시도 중...", fg="orange")

        # 실제 연결 로직을 별도 스레드에서 실행 (GUI 멈춤 방지)
        threading.Thread(target=connect_to_server, args=(server_url,), daemon=True).start()

    button_connect = tk.Button(window, text="연결 시작", command=start_connection, font=default_font, width=15)
    button_connect.pack(pady=5)


    label_info = tk.Label(window, text="연결 후 이 창이 켜져 있는 동안 원격 제어가 활성화됩니다.\n창을 닫으면 프로그램이 종료됩니다.", font=("맑은 고딕", 9), pady=10)
    label_info.pack()

    # --- 💡 4. 실제 서버에 연결하는 함수 ---
    def connect_to_server(url):
        """Socket.IO 서버에 연결을 시도하는 함수"""
        try:
            sio.connect(url, transports=['websocket'])
            sio.wait()
        except Exception as e:
            print(f"연결 실패: {e}")
            # GUI 스레드에서 UI 업데이트
            window.after(0, on_connection_failed)

    def on_connection_failed():
        """연결 실패 시 GUI를 업데이트하는 함수"""
        label_status.config(text="❌ 연결 실패. 주소를 확인하고 다시 시도하세요.", fg="red")
        entry_server.config(state="normal") # 입력창 다시 활성화
        button_connect.config(state="normal") # 버튼 다시 활성화

    # --- 💡 5. 창 닫기 이벤트 처리 ---
    def on_closing():
        """창이 닫힐 때 호출될 함수"""
        print("프로그램을 종료합니다.")
        if sio.connected:
            sio.disconnect()
        os._exit(0) # 프로그램 전체를 강제 종료

    window.protocol("WM_DELETE_WINDOW", on_closing)

    # --- 💡 6. Socket.IO 이벤트 핸들러 ---
    @sio.on('connect')
    def on_connect():
        print("✅ 서버에 성공적으로 연결되었습니다!")
        sio.emit('register', 'exe')
        # window.after를 사용해 메인 GUI 스레드에서 UI를 안전하게 변경
        window.after(0, lambda: label_status.config(text="🚀 서버에 연결되었습니다!", fg="blue"))

    @sio.on('disconnect')
    def on_disconnect():
        print("❌ 서버와의 연결이 끊어졌습니다.")
        window.after(0, on_connection_failed) # 연결 실패 UI 업데이트 재사용

    window.mainloop()

# Socket.IO 클라이언트 생성
sio = socketio.Client()

@sio.on('control')
def on_control(data):
    handle_input(data)

# 메인 로직 실행
if __name__ == '__main__':
    # GUI를 실행하면 모든 로직이 GUI 내부에서 처리됨
    create_gui()
