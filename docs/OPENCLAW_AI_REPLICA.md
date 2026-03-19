# OpenClaw AI Replica Guide

This guide is for an OpenClaw assistant that needs to replicate/customize this app for a human user.

> AI-facing instructions are intentionally written in English so any human reviewer can audit them easily.

## 0) Interactive intake (ask first, then implement)
Before changing code, ask the human for the required inputs in a short checklist format and wait for answers.

Minimum intake fields:
1. App name
2. App icon (path/file)
3. Default UI locale
4. Color theme preference
5. Bridge endpoint + token
6. Audio preferences:
   - STT transcription visible in chat? (yes/no)
   - TTS enabled for assistant replies? (yes/no)
   - Preferred TTS voice(s) by language
7. Deployment preference:
   - Local LAN only, or internet-accessible?
   - If internet-accessible: domain/subdomain plan (DuckDNS/custom domain), reverse proxy choice (e.g., nginx), TLS plan.

If data is missing, ask follow-up questions interactively instead of guessing.

## 1) Host requirements
- Linux
- Java 17
- Android SDK CLI
- OpenClaw CLI available
- Python 3
- (Optional) `edge-tts` for server-side spoken replies

## 2) Android setup
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk unzip
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
curl -fL -o commandlinetools-linux.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux.zip
mv cmdline-tools latest
export ANDROID_SDK_ROOT=~/Android/Sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## 3) Build APK
```bash
git clone <fork-url> openclaw-app
cd openclaw-app
cat > local.properties <<EOF
sdk.dir=$HOME/Android/Sdk
EOF
./gradlew assembleRelease
```

## 4) Bridge
Script: `scripts/openclaw_chat_bridge.py`
- `POST /chat`
- `GET /status`
- `GET /media/<file>`

Recommended environment variables:
- `OPENCLAW_APP_BRIDGE_HOST`
- `OPENCLAW_APP_BRIDGE_PORT`
- `OPENCLAW_APP_BRIDGE_TOKEN`
- `OPENCLAW_APP_BRIDGE_PUBLIC_BASE_URL`
- `OPENCLAW_APP_BRIDGE_MEDIA_DIR`
- `OPENCLAW_APP_BRIDGE_EDGE_TTS`

## 5) Token keystore (critical)
For release signing, the Android keystore is mandatory.

- `keystore.properties` and `.jks` hold signing credentials.
- Without the original keystore, you cannot ship updates to already-installed packages under the same app id/signature.
- If the keystore is lost, migration usually requires publishing a new app identity.
- Do not commit private keystore passwords in public repos.
- Keep secure backups of keystore material before distribution.

## 6) TTS/STT guidance
### STT transcription
- If enabled, display transcript text in chat for transparency/debugging.
- If disabled, keep voice-input UX minimal and privacy-oriented.

### TTS enablement
- Honor human-selected voice per language.
- If no voice is selected for a language, apply a documented fallback voice.
- Keep pronunciation settings/versioned presets explicit.

### Choosing a good TTS voice (human-facing advice)
Recommend the human test 3-5 voices and compare:
1. Naturalness (not robotic)
2. Clarity at normal speed
3. Pronunciation quality in target language
4. Fatigue over long listening sessions
5. Latency/response speed

## 7) Internet exposure (optional)
If the human wants to use the app from anywhere (not only LAN):
- Use a domain/subdomain pointing to the bridge public IP.
- A free baseline is DuckDNS + nginx reverse proxy.
- Add HTTPS/TLS before exposing production traffic.
- Validate token auth remains active at all layers.

## 8) Personalization
- Name: `app_name` in `strings.xml`
- Theme: `ThemeManager.kt` + drawables
- Icon: launcher resources
- UI locales: `values-xx-rYY/strings.xml`

## 9) Validation checklist
- text chat works
- image/video upload works
- audio send/playback works
- context/status updates work
- selected theme and UI locale work
- STT/TTS behavior matches human preferences
- remote access config (if enabled) works with domain + TLS
