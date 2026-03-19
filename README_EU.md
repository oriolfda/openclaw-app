# OpenClaw App

> **OHARRA:** App hau eraikuntza fasean dago. Erabili zure ardurapean.

**OpenClaw App Android aplikazioa da, zure OpenClaw laguntzailearekin komunikatzeko** testu, audio, irudi eta bideo bidez.

Biltegi hau erabiltzaile ez-teknikoentzat pentsatuta dago:
1. Aplikazioak zer egiten duen ulertzeko.
2. Gizaki gisa behar den minimoa prestatzeko.
3. OpenClaw laguntzaileari APK pertsonalizatua eraikitzeko argibideak emateko.

## Zer egin behar duzu zuk (gizakia)

### 1. pausoa — Erabaki nolakoa nahi duzun appa
- Apparen izena
- Ikonoa (PNG karratua, gomendatua 1024x1024)
- UI hizkuntza lehenetsia
- Kolore gaia
- Audio hobespenak:
  - STT transkripzioa txatean ikusgai nahi duzun ala ez
  - TTS modua: `auto` (ahotsa hizkuntzaren arabera) edo `ahots zehatza`
  - `auto` bada: hizkuntzak + hizkuntza bakoitzeko ahotsa
  - `ahots zehatza` bada: ahots zehatza eta noiz aplikatu
- Internet bidez (LANetik kanpo) erabili nahi baduzu: domeinua/azpidomeinua + reverse proxy (adib. DuckDNS + nginx)
- Android sinadura (keystore/token):
  - AI laguntzaileak sortzen du build/sign prozesuan
  - beharrezkoa da etorkizuneko eguneraketetarako
  - ez argitaratu sinadura materiala repoan
- Gizakia↔AI karpeta partekatua APK, pantaila-argazki, log eta akats txostenetarako

### 2. pausoa — Sortu ZUK repoa eta eman sarbide segurua laguntzaileari
Gomendioa: biltegia zurea izatea (zure GitHub), ez laguntzailearena.

- **2.1** Sortu repo berria zure GitHub-en (adib. `openclaw-app`)
- **2.2** Sortu deploy key espezifikoa repo horretarako
  - laguntzaileak commit/push egin behar badu, idazketa baimena aktibatu

GitHub adibide azkarra:
1. Repository → **Settings** → **Deploy keys** → **Add deploy key**
2. Izenburua, adib. `openclaw-app-assistant`
3. Itsatsi gako publikoa (`*.pub`)
4. Aktibatu **Allow write access** laguntzaileak aldaketak igo behar baditu
5. Gorde

- **2.3** Partekatu laguntzailearekin:
  - zure GitHub-en sortu berri duzun repoaren URL-a
  - SSH gakoaren bidea/kokapena (laguntzailea exekutatzen den hostean)

### 3. pausoa — Eman repo hau zure OpenClaw laguntzaileari
Partekatu repoaren URL-a eta eskatu `docs/OPENCLAW_AI_REPLICA.md` jarraitzea.

Aukeran, esan laguntzaileari galdetzeko zein karpeta partekatu nahi duzun APK emateko eta erroreak jakinarazteko.

### 4. pausoa — Probatu APK eta eman feedbacka
Instalatu, endpoint+token konfiguratu, testu/audio/irudi/bideo probatu eta arazo bisual/funtzionalak jakinarazi.

## Zer egingo du OpenClaw laguntzaileak
- Beharrezko informazioaren bilketa interaktiboa
- Karpeta partekatuari buruzko galdera aukerakoa
- Android setup-a
- OpenClaw bridge setup-a
- STT/TTS konfigurazioa zure hobespenen arabera
- APK build/sign
- Marka/gai/hizkuntza pertsonalizazioa
- Balidazio funtzionala

## Dokumentazio nagusia
- `docs/OPENCLAW_AI_REPLICA.md`
- `docs/LOCALIZATION.md`
- `docs/templates/ui-locale-template.json`

---

## Oinarrizko lege-oharra
Aplikazio hau AI laguntzaile batekin komunikatzeko interfazea da. Erabilera, konfigurazioa eta laguntzailearen bidez exekutatutako ekintzak appa zabaltzen eta erabiltzen duen pertsonaren ardurapean daude.

Biltegiko egile/kolaboratzaileek ez dute erantzukizunik hartzen erabilera desegokia, datu-galera, segurtasun-gorabeherak edo apparen instalazio/konfigurazio/erabileratik eratorritako kalteengatik.

App hau instalatu edo erabiltzen baduzu, termino hauek onartzen dituzula ulertzen da.
