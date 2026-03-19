# OpenClaw App

> **AVISO:** Esta app está en fase de construcción. Úsala bajo tu propia responsabilidad.

**OpenClaw App es una app Android para comunicarte con tu asistente OpenClaw** por texto, audio, imágenes y vídeo.

Este repositorio está pensado para que una persona no técnica pueda:
1. Entender qué hace la app.
2. Preparar lo mínimo necesario como humano.
3. Dar instrucciones a su asistente OpenClaw para construir y personalizar el APK.

## Qué haces tú (humano)

### Paso 1 — Decide cómo quieres tu app
- Nombre de la app
- Icono (PNG cuadrado, ideal 1024x1024)
- Idioma UI por defecto
- Tema de color
- Preferencias de audio:
  - transcripción STT visible en chat o no
  - modo TTS: `auto` (voz por idioma) o `voz específica`
  - si es `auto`: idiomas + voz por idioma
  - si es `voz específica`: voz exacta y cuándo aplicarla
- Si quieres acceso desde Internet (fuera de LAN): dominio/subdominio + proxy inverso (ej. DuckDNS + nginx)
- Firma Android (keystore/token):
  - la genera el asistente AI durante build/sign
  - es necesaria para futuras actualizaciones
  - no publiques nunca el material de firma en el repo
- Carpeta compartida humano↔AI para APK, capturas, logs y reportes

### Paso 2 — Crea TU repo y da acceso seguro al asistente
Recomendación: que el repositorio sea tuyo (tu GitHub), no del asistente.

- **2.1** Crea un repositorio nuevo en tu GitHub (ej. `openclaw-app`)
- **2.2** Crea una deploy key específica para ese repositorio
  - si esperas que el asistente haga commits/push, habilita escritura

Ejemplo rápido (GitHub):
1. Repository → **Settings** → **Deploy keys** → **Add deploy key**
2. Título, por ejemplo `openclaw-app-assistant`
3. Pega la clave pública (`*.pub`)
4. Activa **Allow write access** si el asistente debe subir cambios
5. Guarda

- **2.3** Comparte con el asistente:
  - URL del repositorio recién creado en tu GitHub
  - ruta/localización de la clave SSH en el host donde corre el asistente

### Paso 3 — Entrega este repo a tu asistente OpenClaw
Pásale la URL del repo e indícale que siga `docs/OPENCLAW_AI_REPLICA.md`.

Opcionalmente, pídele que te pregunte qué carpeta compartida prefieres para entregar APK y reportar incidencias.

### Paso 4 — Prueba el APK y da feedback
Instala, configura endpoint+token, prueba texto/audio/imagen/vídeo y reporta cualquier incidencia visual o funcional.

## Qué hará tu asistente OpenClaw
- Recogida interactiva de información
- Pregunta opcional sobre carpeta compartida
- Setup Android
- Setup bridge OpenClaw
- Configuración STT/TTS según tus preferencias
- Build/sign APK
- Personalización de marca/tema/idioma
- Validación funcional

## Documentación principal
- `docs/OPENCLAW_AI_REPLICA.md`
- `docs/LOCALIZATION.md`
- `docs/templates/ui-locale-template.json`

---

## Aviso legal básico
Esta app es una interfaz de comunicación con un asistente AI. El uso, la configuración y las acciones ejecutadas a través del asistente son responsabilidad de la persona que despliega y usa la app.

Autores/colaboradores del repositorio no asumen responsabilidad por uso indebido, pérdida de datos, incidentes de seguridad o daños derivados de la instalación, configuración o uso de la app.

Si decides instalar o usar esta app, se entiende que aceptas estos términos.
