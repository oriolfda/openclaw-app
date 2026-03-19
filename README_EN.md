# OpenClaw App

**OpenClaw App is an Android app to chat with your OpenClaw assistant** using text, voice, images and video.

This repository is designed so non-technical users can:
1. Understand what the app does.
2. Prepare the minimum needed as a human.
3. Ask their OpenClaw assistant to build and customize the APK.

## What you do (human)
1. Decide app name, icon, default UI language, color theme, and audio behavior.
2. Choose whether you want STT transcript + TTS replies, and test a few TTS voices to pick your favorite.
3. If you want internet-wide use, plan domain routing to your app IP (example: DuckDNS + nginx).
4. Understand keystore/token-signing importance for future update deployment continuity.
5. Give this repo/fork to your OpenClaw assistant.
6. Ask it to follow `docs/OPENCLAW_AI_REPLICA.md` and build a release APK.
7. Install APK and test.

## What your OpenClaw assistant does
- Interactive intake of required human inputs
- Android toolchain setup
- Bridge setup + STT/TTS configuration according to selected voices
- Branding + theme + language customization
- Build/sign release APK
- Functional validation

## Main docs
- `docs/OPENCLAW_AI_REPLICA.md`
- `docs/LOCALIZATION.md`
- `docs/templates/ui-locale-template.json`
