# Spese

App Android nativa in Kotlin e Jetpack Compose. La Fase 1 contiene le fondamenta grafiche e la logica del tastierino.

Per compilare servono JDK 17, Android SDK Platform 37.2 e Android SDK Build Tools 37.0.0. Configura `ANDROID_HOME` o `sdk.dir` in `local.properties`, poi esegui dalla root:

```sh
./gradlew assembleDebug
./gradlew test
```

L'APK in `app/build/outputs/apk/debug/` apre un'anteprima interattiva. Il salvataggio e Annulla operano sullo stato in memoria dell'anteprima. Le scelte di implementazione sono in [DECISIONS.md](DECISIONS.md).
