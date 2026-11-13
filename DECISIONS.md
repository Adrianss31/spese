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

## Fase 4

- Gli ultimi sei mesi terminano al mese selezionato e includono a zero i mesi senza spese.

## Fase 5

- Il Budget mostra il mese corrente. Il limite suggerito usa un minimo di 50 euro e scatti di 50 euro.
- Le notifiche delle soglie condividono il canale e la pianificazione introdotti con i ricorrenti.

## Fase 6

- La generazione aggiorna il cursore del ricorrente nella stessa transazione delle spese. Al cambio di frequenza il cursore impedisce di riscrivere le occorrenze storiche.
- Il lavoro giornaliero si ripianifica alle 00:05 locali. WorkManager può ritardarlo secondo le condizioni del dispositivo.

## Fase 7

- Il popup riusa l'editor delle spese. L'origine dell'animazione usa i limiti del widget se il launcher li fornisce.
- Glance usa i font di sistema per il totale e un secondo aggiornamento per rimuovere la spunta.

## Fase 8

- Gli intervalli includono entrambi gli estremi e l'export esclude gli importi da confermare. Nel CSV le celle di testo che possono essere interpretate come formule vengono protette con un apostrofo.

## Fase 9

- Il repository GitHub è privato. Per gli aggiornamenti in app è previsto un token GitHub in sola lettura, protetto dall'Android Keystore.
- Il download verifica dimensione, SHA-256, package, versione e certificato prima di consegnare l'APK all'installatore di Android.
