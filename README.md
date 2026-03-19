# OpenClaw App

**OpenClaw App és una app Android per comunicar-te amb el teu assistent OpenClaw** de forma natural: text, àudio, imatges i vídeo, amb una interfície de xat moderna.

Aquest repositori està pensat perquè qualsevol persona (encara que no sigui tècnica) pugui:

1. Entendre què és l’app i què fa.
2. Preparar el mínim necessari com a humà.
3. Donar instruccions al seu assistent OpenClaw perquè construeixi i personalitzi l’APK.

---

## Què has de fer TU (com a humà)

### Pas 1 — Decideix com vols la teva app
Abans de res, pensa aquests punts:

- **Nom de l’app** (ex: “Aina Assistant”)
- **Icona** (png quadrat, idealment 1024x1024)
- **Idioma d’interfície per defecte**
- **Tema de colors** (fosc vermell, blau, verd, clar...)
- **Àudio**:
  - si vols **transcripció STT** visible al xat
  - si vols **TTS** per les respostes
  - quina **veu TTS** prefereixes (val la pena provar-ne 3-5 i triar per claredat + naturalitat)
- **Com publicar-la si la vols usar arreu** (fora de la LAN):
  - domini/subdomini cap a la IP pública de la teva app
  - exemple gratuït: **DuckDNS + nginx**
- **Signatura Android (keystore/token)**:
  - és clau per poder desplegar actualitzacions de la mateixa app
  - guarda credencials i fitxers de signatura de forma segura
- **Carpeta compartida humà ↔ assistent AI**:
  - crea una carpeta compartida de treball/comunicació (APK, captures, logs, errors)
  - és molt útil per descarregar APK des del mòbil i reportar incidències de prova

### Pas 2 — Dona aquest repo al teu assistent OpenClaw
Passa-li l’enllaç del teu fork (o d’aquest repo) i digues-li:

> “Vull una rèplica personalitzada de l’OpenClaw App. Segueix la guia `docs/OPENCLAW_AI_REPLICA.md`, canvia nom+icona+tema+idioma, compila APK release i deixa’m el fitxer llest per instal·lar.”

### Pas 3 — Prova l’APK i dona feedback
Quan l’assistent et passi l’APK:

- instal·la-la al mòbil
- obre Settings i posa endpoint+token del bridge
- prova text, àudio, imatge, vídeo
- si vols canvis visuals/funcionals, torna-li feedback

---

## Què farà el teu assistent OpenClaw

El teu assistent (no tu manualment) farà:

- recollida **interactiva** de la informació necessària (nom, icona, idioma, tema, STT/TTS, etc.)
- instal·lació de requisits Android (JDK/SDK)
- configuració del bridge OpenClaw
- configuració de STT/TTS segons preferències humanes (incloent veus triades)
- compilació APK release
- personalització (marca, tema, idioma)
- validació funcional

Guia completa per a l’assistent:

➡️ `docs/OPENCLAW_AI_REPLICA.md`

---

## Què inclou l’app

- Xat text
- Gravació i enviament d’àudio
- Adjunt d’imatge i vídeo
- Reproducció d’àudio al xat
- Render de contingut HTML/codi
- Temes i localització de la interfície

---

## Idiomes inicials suportats (UI)

- Català (`ca-ES`)
- Español (`es-ES`)
- English UK (`en-GB`)
- English US (`en-US`)
- Galego (`gl-ES`)
- Euskara (`eu-ES`)

---

## Documents útils

- `docs/OPENCLAW_AI_REPLICA.md` → guia operativa per l’assistent OpenClaw
- `docs/LOCALIZATION.md` → com afegir/traduir idiomes de la interfície
- `docs/templates/ui-locale-template.json` → plantilla de traduccions

---

## En una frase

**Tu decideixes com vols l’app. El teu assistent OpenClaw la construeix i la personalitza per tu.**
