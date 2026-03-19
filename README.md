# OpenClaw App

**OpenClaw App és una app Android per comunicar-te amb el teu assistent OpenClaw** de forma natural: text, àudio, imatges i vídeo, amb una interfície de xat moderna.

Aquest repositori està pensat perquè qualsevol persona (encara que no sigui tècnica) pugui:

1. Entendre què és l’app i què fa.
2. Preparar el mínim necessari com a humà.
3. Donar instruccions al teu assistent OpenClaw perquè construeixi i personalitzi l’APK.

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
  - per al **TTS**, tria el tractament:
    - **auto** (veu segons idioma)
    - **veu específica** (una veu fixa)
  - si tries **auto**, defineix:
    - quins idiomes vols cobrir
    - quina veu s’ha d’usar a cada idioma
  - si tries **veu específica**, indica la veu exacta i en quins casos s’ha d’aplicar
  - recomanat: provar 3-5 veus i triar per claredat + naturalitat
- **Com publicar-la si la vols usar arreu** (fora de la LAN):
  - domini/subdomini cap a la IP pública de la teva app
  - exemple gratuït: **DuckDNS + nginx**
- **Signatura Android (keystore/token)**:
  - la genera l’assistent AI durant el procés de build/sign
  - és clau per poder desplegar actualitzacions de la mateixa app
  - no la publiquis mai al repo
  - guarda credencials i fitxers de signatura de forma segura
- **Carpeta compartida humà ↔ assistent AI**:
  - crea una carpeta compartida de treball/comunicació (APK, captures, logs, errors)
  - és molt útil per descarregar APK des del mòbil i reportar incidències de prova

### Pas 2 — Crea TU el repo i dona accés segur a l’assistent
Recomanació: que el **repo sigui teu** (GitHub de l’humà) i no de l’assistent.

Flux recomanat:
- **2.1** Crea un repo nou al teu GitHub (p. ex. `openclaw-app`)
- **2.2** Crea una **deploy key** específica per aquest repo (amb escriptura si l’assistent ha de fer push)

Exemple breu (GitHub), just després del pas 2.2:
1. Repository → **Settings** → **Deploy keys** → **Add deploy key**
2. Títol: p. ex. `openclaw-app-assistant`
3. Enganxa la clau pública (`*.pub`)
4. Activa **Allow write access** si vols que l’assistent pugi canvis
5. Desa la key

- **2.3** Comparteix amb l’assistent:
  - **URL del repo acabat de crear al teu GitHub**
  - ruta/localització de la clau SSH (al host on treballa l’assistent)

### Pas 3 — Dona aquest repo (aquest, el nostre) al teu assistent OpenClaw
Passa-li l’URL del repo i indica-li simplement que segueixi `docs/OPENCLAW_AI_REPLICA.md`.

També li pots dir que et demani, de forma opcional, la **carpeta compartida** on vols rebre l’APK i comunicar incidències.

### Pas 4 — Prova l’APK i dona feedback
Quan l’assistent et passi l’APK:

- instal·la-la al mòbil
- obre Settings i posa endpoint+token del bridge
- prova text, àudio, imatge, vídeo
- si vols canvis visuals/funcionals, torna-li feedback

---

## Què farà el teu assistent OpenClaw

El teu assistent (no tu manualment) farà:

- recollida **interactiva** de la informació necessària (nom, icona, idioma, tema, STT/TTS, etc.)
- pregunta opcional sobre la **carpeta compartida** on deixar APKs i on rebre errors/captures
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
