# Localització de la interfície (UI)

Aquesta app separa:
- **Idioma de la interfície** (UI): texts de botons/menus.
- **Idioma de resposta/TTS**: lògica del bridge/assistent (configurable a part).

## Idiomes UI preparats
- `en-GB` (EN_EN)
- `en-US`
- `ca-ES`
- `es-ES`
- `gl-ES`
- `eu-ES` (basc)

## Fitxers Android
Cada idioma viu a:
- `app/src/main/res/values-<locale>/strings.xml`

Exemples:
- `values-en-rGB/strings.xml`
- `values-ca-rES/strings.xml`

## Afegir un idioma nou
1. Crea un directori nou `values-xx-rYY`.
2. Copia `app/src/main/res/values/strings.xml`.
3. Tradueix els valors, mantenint **les mateixes claus**.
4. Afegeix el locale a `LocaleManager.supportedUiLocales`.
5. Afegeix l'opció al spinner de `SettingsActivity`.
6. Compila APK.

## Plantilla de traduccions
Pots usar `docs/templates/ui-locale-template.json` com guia humana.
Després passa el contingut al `strings.xml` del locale corresponent.
