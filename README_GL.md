# OpenClaw App

> **AVISO:** Esta app está en fase de construción. Úsaa baixo a túa propia responsabilidade.

**OpenClaw App é unha app Android para comunicarte co teu asistente OpenClaw** con texto, audio, imaxes e vídeo.

Este repositorio está pensado para que unha persoa non técnica poida:
1. Entender que fai a app.
2. Preparar o mínimo necesario como humano.
3. Dar instrucións ao seu asistente OpenClaw para construír e personalizar o APK.

## Que fas ti (humano)

### Paso 1 — Decide como queres a túa app
- Nome da app
- Icona (PNG cadrado, ideal 1024x1024)
- Idioma UI por defecto
- Tema de cor
- Preferencias de audio:
  - transcrición STT visible no chat ou non
  - modo TTS: `auto` (voz por idioma) ou `voz específica`
  - se é `auto`: idiomas + voz por idioma
  - se é `voz específica`: voz exacta e cando aplicala
- Se queres acceso desde Internet (fóra da LAN): dominio/subdominio + proxy inverso (ex. DuckDNS + nginx)
- Sinatura Android (keystore/token):
  - xéraa o asistente AI durante build/sign
  - é necesaria para futuras actualizacións
  - non publiques nunca o material de sinatura no repo
- Cartafol compartido humano↔AI para APK, capturas, logs e reportes

### Paso 2 — Crea TI o repo e dá acceso seguro ao asistente
Recomendación: que o repositorio sexa teu (o teu GitHub), non do asistente.

- **2.1** Crea un repositorio novo no teu GitHub (ex. `openclaw-app`)
- **2.2** Crea unha deploy key específica para ese repositorio
  - se esperas que o asistente faga commits/push, activa escritura

Exemplo rápido (GitHub):
1. Repository → **Settings** → **Deploy keys** → **Add deploy key**
2. Título, por exemplo `openclaw-app-assistant`
3. Pega a chave pública (`*.pub`)
4. Activa **Allow write access** se o asistente ten que subir cambios
5. Garda

- **2.3** Comparte co asistente:
  - URL do repositorio recén creado no teu GitHub
  - ruta/localización da chave SSH no host onde corre o asistente

### Paso 3 — Entrega este repo ao teu asistente OpenClaw
Pásalle a URL do repo e indícalle que siga `docs/OPENCLAW_AI_REPLICA.md`.

Opcionalmente, pídelle que che pregunte que cartafol compartido prefires para entregar APK e reportar incidencias.

### Paso 4 — Proba o APK e dá feedback
Instala, configura endpoint+token, proba texto/audio/imaxe/vídeo e reporta incidencias visuais ou funcionais.

## Que fará o teu asistente OpenClaw
- Recollida interactiva de información
- Pregunta opcional sobre cartafol compartido
- Setup Android
- Setup bridge OpenClaw
- Configuración STT/TTS segundo as túas preferencias
- Build/sign APK
- Personalización de marca/tema/idioma
- Validación funcional

## Documentación principal
- `docs/OPENCLAW_AI_REPLICA.md`
- `docs/LOCALIZATION.md`
- `docs/templates/ui-locale-template.json`

---

## Aviso legal básico
Esta app é unha interface de comunicación cun asistente AI. O uso, a configuración e as accións executadas a través do asistente son responsabilidade da persoa que desprega e usa a app.

Autores/colaboradores do repositorio non asumen responsabilidade por uso indebido, perda de datos, incidentes de seguridade ou danos derivados da instalación, configuración ou uso da app.

Se decides instalar ou usar esta app, enténdese que aceptas estes termos.
