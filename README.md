# ÉnOttVoltam 📱⚡

Az **ÉnOttVoltam** egy natív Android alkalmazás, amely automatizálja a BME (Budapesti Műszaki és Gazdaságtudományi Egyetem) Moodle jelenléti QR-kódjainak beolvasását, szerver felé történő továbbítását, valamint a BME Címtáras (Shibboleth) automatikus bejelentkezést.

![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blue?logo=kotlin)
![CameraX](https://img.shields.io/badge/CameraX-ML%20Kit-orange)
![Release](https://img.shields.io/github/v/release/Kelmopirin/EnOttVoltam?label=Release)

---

## 📥 Letöltés

A legfrissebb telepíthető alkalmazás (**EnOttVoltam.apk**) elérhető a GitHub Releases oldalon:

👉 **[Legújabb Kiadás Letöltése (Releases)](https://github.com/Kelmopirin/EnOttVoltam/releases/latest)**  
📦 **[Közvetlen APK Letöltés (v1.0.0)](https://github.com/Kelmopirin/EnOttVoltam/releases/download/v1.0.0/EnOttVoltam.apk)**

---

## 🌟 Fő Funkciók

- **📷 Kamera & QR-kód Beolvasó**:
  - Kamera kép élő megjelenítése (CameraX + ML Kit).
  - Kétujjas nagyítás (**Pinch-to-zoom**) és gombos gyorszoom (`1x`, `2x`, `4x`).
  - Vaku (világítás) ki- és bekapcsolás.
  - Vágólapra másolási és böngészős megnyitási lehetőségek.

- **🔑 Automatikus BME Címtár Bejelentkezés (Auto-Login)**:
  - Beágyazott in-app **WebView** segítségével automatikusan kitölti a Címtáras felhasználónevet és jelszót.
  - Automatikusan átlép a Moodle Címtár gombján (`login-identityprovider-btn`), megadja a belépési adatokat, és rögzíti a jelenlétet.
  - Sikeres bejelentkezés esetén rendszerértesítést (*"Sikeres bejelentkezés! ✅"*) és Toast visszajelzést küld.

- **📡 Szerver Szinkronizáció & Broadcast (SSE)**:
  - A beolvasott QR-kódokat azonnal továbbítja a központi szerverre (`POST /qr`).
  - **Háttérben futó Foreground Service** (`SseForegroundService`): feliratkozik a szerver Server-Sent Events (`GET /events`) csatornájára.
  - **Távoli beolvasások fogadása**: Ha a hálózathoz csatlakozott bármelyik másik eszköz beolvas egy QR-kódot, az app **még zárt állapotban is** fogadja azt, értesítést küld róla, és automatikusan végrehajtja a belépést.

- **⚙️ Beállítások & Munkamenet**:
  - Biztonságosan eltárolt munkamenet (`SharedPreferences`).
  - Egyedi szerver host cím megadása (Alapértelmezett: `https://csalas.onrender.com`).
  - Kijelentkezési lehetőség.

---

## 🛠️ Technológiai Stóc

- **Nyelv**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Kamera & Vonalkód**: CameraX API, Google ML Kit Vision Barcode Scanning
- **Hálózat**: OkHttp 4.12.0, Server-Sent Events (SSE), Kotlin Coroutines
- **Háttérszolgáltatás**: Android Foreground Service (`foregroundServiceType="dataSync"`)
- **Webes Automatizáció**: Android `WebView` + JavaScript injection

---

## 📱 Használat

1. **Indítás & Bejelentkezés**:
   - Nyisd meg az alkalmazást.
   - Add meg a **Címtáras felhasználónevedet** és a **Jelszavadat**.
   - Szükség esetén módosítsd a Szerver Host címét (alapértelmezett: `https://csalas.onrender.com`).
   - Kattints a **Bejelentkezés & Csatlakozás** gombra.

2. **Jelenlét Rögzítése**:
   - **Helyi beolvasással**: Irányítsd a kamerát az órán megjelenő QR-kódra. Az app automatikusan elküldi a szervernek és elvégzi a bejelentkezést.
   - **Távoli beolvasással**: Ha egy szaktársad beolvassa a QR-kódot és a szerver elküldi a broadcastot, az appod háttérben is fogadja, és automatikusan bejelentkeztet!

---

## 🚀 Forráskód Buildelése

Ha saját magad szeretnéd lefordítani a projektet:

```bash
# Debug APK fordítása
./gradlew app:assembleDebug

# Aláírt Release APK fordítása
./gradlew app:assembleRelease
```

---

## 📄 Licenc

Ez a projekt oktatási és kényelmi célokat szolgál.
