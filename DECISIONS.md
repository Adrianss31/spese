# Decisioni

## Fase 1

- Un modulo Android, package `it.avanzi.spese`. Il codice condiviso sta in `ui/theme`, `ui/icons` e `ui/components`. La formattazione e il tastierino sono in `domain` per poterli testare sulla JVM.
- Compilazione con SDK 37.2, target 37 e minSdk 26. Gradle 9.6.0 e Android Gradle Plugin 9.4.0.
- La build debug apre un'anteprima interattiva dei componenti con stato in memoria. La schermata di avvio dell'app arriverà con la Home.
- Il foglio usa un dialog Compose a tutta finestra con scrim personalizzato e contenuto scrollabile per schermi bassi.
- Il toast usa 3,4 secondi come durata base e rispetta il timeout esteso configurato per l'accessibilità.

## Fase 2

- La prima versione del database esporta lo schema Room. I test DAO eseguono SQLite tramite Robolectric.
- Le date sono memorizzate come giorni dall'epoca e gli orari come nanosecondi dalla mezzanotte. Le spese conservano il riferimento storico a un ricorrente eliminato.

## Fase 3

- Home e Statistiche condividono il mese selezionato. La ricerca filtra le spese del mese per descrizione, categoria e importo.
- Una spesa con Ripeti viene collegata al nuovo ricorrente nella stessa transazione. La data scelta nel foglio determina il giorno dell'addebito.
