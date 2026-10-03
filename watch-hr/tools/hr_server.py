"""
폰 수신 앱이 Wi-Fi로 보내는 심박을 받아 watch-hr/data/ 에 날짜별 CSV로 자동 저장한다.

실행 (watch-hr 폴더에서):
    python tools/hr_server.py            # 기본 포트 8765
    python tools/hr_server.py 9000       # 포트 바꾸기

실행하면 "폰 앱에 넣을 주소"가 출력된다. 폰 앱의 [PC 자동 저장] 칸에 그 주소를 넣고 [연결].
처음 실행 때 Windows 방화벽 창이 뜨면 "개인 네트워크"(필요하면 공용도) 허용을 눌러야 폰이 접속할 수 있다.

data/ 는 .gitignore 로 저장소에 올라가지 않는다 (심박은 개인 건강 정보).
파이썬 기본 라이브러리만 쓴다 (따로 설치할 것 없음).
"""
import csv
import json
import os
import socket
import sys
import threading
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
DATA_DIR = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data"))
HEADER = ["device", "id", "bpm", "measured_time", "received_time", "delay_ms",
          "measured_at", "received_at", "source_node", "valid", "baseline", "saved_time"]

lock = threading.Lock()
seen = set()          # (device, id, received_at) — 같은 기록이 다시 와도 한 번만 저장
total = 0


def fmt(ms):
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def load_seen():
    """이미 저장된 기록을 읽어 둔다 (프로그램을 껐다 켜도 중복 저장 방지)"""
    if not os.path.isdir(DATA_DIR):
        return
    for name in os.listdir(DATA_DIR):
        if name.startswith("hr_") and name.endswith(".csv"):
            with open(os.path.join(DATA_DIR, name), encoding="utf-8-sig", newline="") as f:
                for r in csv.DictReader(f):
                    seen.add((r["device"], r["id"], r["received_at"]))


def save(device, rows):
    global total
    os.makedirs(DATA_DIR, exist_ok=True)
    saved = 0
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    with lock:
        by_file = {}
        for r in rows:
            key = (device, str(r["id"]), str(r["receivedAt"]))
            if key in seen:
                continue
            seen.add(key)
            day = datetime.fromtimestamp(r["receivedAt"] / 1000).strftime("%Y%m%d")
            by_file.setdefault(day, []).append([
                device, r["id"], r["bpm"], fmt(r["at"]), fmt(r["receivedAt"]),
                r["receivedAt"] - r["at"], r["at"], r["receivedAt"], r.get("source", ""),
                1 if r.get("valid") else 0, 1 if r.get("baseline") else 0, now,
            ])
        for day, lines in by_file.items():
            path = os.path.join(DATA_DIR, f"hr_{day}.csv")
            new = not os.path.exists(path)
            # utf-8-sig: 엑셀로 열어도 한글이 깨지지 않게
            with open(path, "a", encoding="utf-8-sig" if new else "utf-8", newline="") as f:
                w = csv.writer(f)
                if new:
                    w.writerow(HEADER)
                w.writerows(lines)
            saved += len(lines)
        total += saved
    return saved


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/hr":
            self.send_error(404)
            return
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
            rows = body.get("rows", [])
            saved = save(body.get("device", "unknown"), rows)
        except Exception as e:  # 잘못된 요청이어도 서버는 계속 돈다
            self.send_error(400, str(e))
            return
        if rows:
            last = rows[-1]
            print(f"[{datetime.now():%H:%M:%S}] {self.client_address[0]} → {len(rows)}건 받음, "
                  f"새로 저장 {saved}건 (누적 {total}) · 마지막 {last['bpm']} bpm", flush=True)
        self._json({"saved": saved, "total": total})

    def do_GET(self):
        # 브라우저나 폰에서 http://주소/ 로 접속 확인용
        self._json({"ok": True, "total": total, "data_dir": DATA_DIR})

    def _json(self, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):  # 요청마다 찍히는 기본 로그는 끈다
        pass


def local_ips():
    ips = set()
    try:  # 실제로 밖으로 나가는 인터페이스의 주소 (패킷은 보내지 않음)
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ips.add(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
        ip = info[4][0]
        if not ip.startswith(("127.", "169.254.")):
            ips.add(ip)
    return sorted(ips)


if __name__ == "__main__":
    load_seen()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print("심박 수신 서버 실행 중 (끄려면 Ctrl+C)")
    print(f"  저장 폴더: {DATA_DIR}")
    print("  폰 앱에 넣을 주소:", ", ".join(f"{ip}:{PORT}" for ip in local_ips()))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n종료")
