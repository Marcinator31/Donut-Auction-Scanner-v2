# Changelog

Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

## [Unreleased]

### Added
- **Preis prüfen**: eigene Ansicht mit Suchfeld. Gegenstand eintippen,
  anklicken, und der aktuell günstigste Preis pro Stück im Auktionshaus wird
  sofort geholt - ohne dass man dafür einen Alarm anlegen muss
- Erreichbar über das Suchfeld in der Kopfzeile des Scanners
- Das Ergebnis zeigt auch Stapelgröße, Verkäufer, wie viele Seiten geprüft
  wurden und - falls vorhanden - die 24h-Spanne
- Jede Abfrage wandert in die Preishistorie und füttert damit die Verlaufsgraphen
- „Scan anlegen" übernimmt Gegenstand und gefundenen Preis direkt in den Editor

### Changed
- **Alles wird jetzt in der Oberfläche eingestellt.** Die Befehle zum Anlegen,
  Auflisten und Konfigurieren sind weg. Übrig bleibt `/auctionscanner`
  (Kurzform `/scanner`), der nur das Fenster öffnet
- Oberfläche komplett neu: keine Vanilla-Widgets mehr, kein Kisten-Look.
  Eigene Knöpfe, Schalter, Karten und Chips, gezeichnet nur mit `fill()`,
  `text()` und `item()`
- Neue Palette aus dem Thema (Pflaumenbraun / Keramik-Creme) statt
  Standard-Dunkelblau. Himbeer und Pistazie kodieren jetzt die Alarmrichtung
- Richtung wird über zwei große Auswahlkarten gesetzt statt über einen
  Umschaltknopf, bei dem man den Gegenzustand erraten muss
- Unter der Preiseingabe steht die Regel im Klartext:
  „Meldung, sobald Elytra 400.000.000 $ oder mehr kostet."
- `/dm raw` ersetzt durch den Knopf „API prüfen" in den Einstellungen

### Added
- `SystemBeep`: Alarmton wird selbst synthetisiert und direkt über die
  Soundkarte gespielt, an Minecrafts Audio-Engine vorbei. Kommt damit auch
  alt-getabbt, bei pausierter Singleplayer-Welt und im Hauptmenü an
  (`useSystemSound`, Standard: an)
- Schalter „Nur auf DonutSMP scannen" (`onlyWhileOnDonutSmp`, Standard: aus)
- Statuszeile in den Einstellungen: zeigt, ob und warum gerade gescannt wird
- `ConnectionState`: einzige Stelle, die den aktuellen Server abfragt

### Fixed
- Benachrichtigungen wurden still verworfen, wenn kein Spieler existierte
  (Hauptmenü, Ladebildschirm, Serverwechsel). Sie werden jetzt gemerkt,
  ins Log geschrieben und beim nächsten Weltbetreten nachgereicht
- Overlay-Meldung wird nicht mehr ohne HUD aufgerufen
- Kein doppelter Ton mehr, wenn beide Tonquellen aktiv wären

## [1.0.0] - 2026-08-21

### Added
- Preis-Alarme auf beliebige Items, beide Richtungen (kaufen/verkaufen)
- Auktions-Scan, der immer den günstigsten Preis **pro Stück** findet, auch
  wenn der auf einer späteren Seite steht
- Preisverlauf-Graph mit 1h / 6h / 24h / 7d, Crosshair und exakten Werten
- Item-Auswahl mit Suche über alle Items im Spiel
- Ton-Benachrichtigung (drei Plings mit steigender Tonhöhe), Chat- und
  Overlay-Meldung, alles auf Deutsch
- Preis-Eingabe versteht `400m`, `250k`, `250.000`, `1,5m`, `$2b`
- Befehle `/dm`, `/dm sell`, `/dm buy`, `/dm add`, `/dm list`, `/dm graph`,
  `/dm check`, `/dm key`, `/dm raw`
- Lokale Preishistorie plus Nachladen echter Verkäufe aus der Transactions-API
- Rate-Limiter, standardmäßig 100 statt der erlaubten 250 Requests/Minute

### Known limitations
- Die DonutSMP-API hat keinen Order-Endpunkt (siehe `OrderSource`)
- Die API liefert keine Preishistorie; der Graph füllt sich erst im Betrieb
- Feldnamen von `/auction/transactions` sind nicht dokumentiert; der Parser
  probiert mehrere Kandidaten, `/dm raw` zeigt die echte Antwort
