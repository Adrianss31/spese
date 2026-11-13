# Spese

App Android personale per tracciare le spese, in Kotlin e Jetpack Compose. Le scelte di implementazione sono in [DECISIONS.md](DECISIONS.md).

## Compilare

Servono JDK 17, Android SDK Platform 37.2 e Build Tools 37.0.0. Configura `ANDROID_HOME` o `sdk.dir` in `local.properties`, poi dalla root:

```sh
./gradlew assembleDebug
./gradlew test
```

## CI e release

- `.github/workflows/build.yml` compila l'APK debug ed esegue i test a ogni push; l'APK resta tra gli artifact dell'esecuzione.
- `.github/workflows/release.yml` parte a ogni tag `v*`: compila, testa, firma con il keystore dei secrets `SPESE_KEYSTORE_BASE64` e `SPESE_KEYSTORE_PASSWORD`, poi pubblica la GitHub Release con `spese.apk`, `spese.apk.sha256` e `version.json`. Un tag con trattino (es. `v1.1.0-beta1`) diventa una pre-release per il canale Beta.

Per una nuova versione: alza `versionCode` e `versionName` in `app/build.gradle.kts`, scrivi `docs/CHANGELOG-<versione>.md` (diventa la nota di rilascio mostrata nell'app), poi crea e pubblica il tag `v<versione>`.

Il keystore di firma non è nel repository: perderlo o rigenerarlo impedisce di installare gli aggiornamenti sopra la versione esistente.
