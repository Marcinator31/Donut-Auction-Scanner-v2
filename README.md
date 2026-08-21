# DonutMarket

[![Build](https://github.com/USER/donutmarket/actions/workflows/build.yml/badge.svg)](https://github.com/USER/donutmarket/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)
![Loader](https://img.shields.io/badge/Fabric-0.19.3%2B-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

Client-Mod für **Minecraft 26.2 / Fabric**. Sagt dir Bescheid, wenn ein Item im
DonutSMP-Auktionshaus deinen Wunschpreis erreicht — mit Ton, Chat-Meldung und
Preisverlauf-Graph.

Rein lesend. Die Mod kauft nichts, verkauft nichts und schickt keine Befehle an
den Server. Sie fragt nur die offizielle DonutSMP-Public-API ab.

---

## Das typische Beispiel

Du hast eine Elytra und willst sie verkaufen, sobald sie 400 Mio wert ist:

```
/auctionscanner
```

Dann in der Oberfläche: Gegenstand wählen, `400m` eintragen (oder auf die
Schnellwahl tippen), Karte **„Preis erreicht oder übersteigt"** anklicken,
speichern. Darunter steht die ganze Zeit im Klartext, was passieren wird:

> Meldung, sobald Elytra 400.000.000 $ oder mehr kostet.

Ab jetzt prüft die Mod im Hintergrund, was die **billigste Elytra im
Auktionshaus** kostet. Sobald dieser Preis 400 Mio erreicht, gibt es:

- drei Plings mit steigender Tonhöhe
- eine Chat-Meldung: `[DonutMarket] VERKAUFEN Elytra — 400.000.000 $ / Stück`
- denselben Text kurz über der Hotbar

Warum der billigste und nicht irgendeiner? Weil das der Preis ist, den du selbst
unterbieten müsstest. Steht die billigste Elytra bei 400 Mio, bringt deine
ungefähr dasselbe.

Umgekehrt geht genauso: die andere Karte, „Preis erreicht oder fällt darunter",
meldet sich sobald ein Totem für 250k oder billiger zu haben ist.

---

## Installation

1. **Fabric Loader 0.19.3+** für Minecraft 26.2 installieren
2. **Fabric API 0.158.0+26.2** in den `mods`-Ordner
3. DonutMarket-JAR aus den [Releases](../../releases) daneben legen
4. Ingame auf DonutSMP `/api` ausführen
   *(Discord-Account muss verknüpft sein, sonst gibt der Server keinen Key raus)*
5. `/auctionscanner` -> Einstellungen -> Schlüssel einfügen -> Speichern

**Taste M** öffnet die Oberfläche ebenfalls.

### Selbst bauen

```bash
git clone https://github.com/USER/donutmarket.git
cd donutmarket
./gradlew build
# → build/libs/donutmarket-1.0.0.jar
```

Braucht **JDK 25**. Gradle bringt der Wrapper selbst mit.

---

## Bedienung

Ein Befehl, mehr nicht:

| Befehl | Wirkung |
|---|---|
| `/auctionscanner` | Oberfläche öffnen |
| `/scanner` | Kurzform, dasselbe |

Taste **M** öffnet sie auch. Alles Weitere — API-Schlüssel, Gegenstand,
Zielpreis, Richtung, Ton, Prüfintervall — wird in der Oberfläche eingestellt.
Keine Chat-Befehle, keine Datei von Hand bearbeiten.

**Preisformate** im Eingabefeld:

```
400m   250k   250.000   250,000   1,5m   2b   $250k
```

Direkt unter dem Feld steht, als welche Zahl das gelesen wurde — so fällt eine
verrutschte Null sofort auf. Sechs Schnellwahl-Knöpfe (250k, 1m, 10m, 100m,
400m, 1b) sparen das Tippen.

### Oberfläche

- **Preis prüfen** — Suchfeld oben in der Kopfzeile. Gegenstand eintippen,
  anklicken, fertig: du siehst sofort den günstigsten Preis pro Stück, die
  Stapelgröße des Angebots, den Verkäufer und wie viele Seiten dafür geprüft
  wurden. Kein Alarm nötig. Aus dem Ergebnis heraus lässt sich mit einem Klick
  ein Scan anlegen — Gegenstand und Preis sind dann schon eingetragen.
- **Scanner** — Liste aller beobachteten Gegenstände. Pro Zeile: Symbol, Regel
  im Klartext („ab 400m", „bis 250k"), aktueller Preis und die Zielskala.
- **Zielskala** — die Mitte ist dein Ziel, die Markierung der Ist-Preis. Links
  davon heißt darunter, rechts darüber. Ein Blick genügt.
- **Verlauf** — Preisgraph mit 1h / 6h / 24h / 7d.
- **Einstellungen** — Schlüssel, Intervall, Ton, Filter. Der Knopf
  „API prüfen" holt eine Test-Antwort und schreibt sie ins Log.

### Gestaltung

Die Oberfläche benutzt bewusst keine Vanilla-Widgets — kein Kisten-Look. Alles
ist selbst gezeichnet, ausschließlich mit `fill()`, `text()` und `item()`.

Palette aus dem Thema statt Standard-Dunkelblau: geröstetes Pflaumenbraun als
Grund, Keramik-Creme als Text. Die zwei Akzente tragen Information —
**Himbeer** heißt immer „verkaufen / Preis darüber", **Pistazie** immer
„kaufen / Preis darunter". Farbe einmal gelernt, Label nicht mehr nötig.

Minecraft hat nur eine Schrift in einer Größe. Als Ersatz für eine
Schriftpaarung bleiben zwei Achsen: Schatten an oder aus als „fett", und
gesperrte Großbuchstaben für Kleinlabels.

## Wie der Preis bestimmt wird

Immer der **günstigste Preis pro Stück**, der aktuell im AH steht.

Dabei steckt eine Falle, an der viele Tools scheitern: Die API sortiert bei
`lowest_price` nach dem **Gesamtpreis eines Angebots**, nicht nach Preis pro
Stück. Ein Stack mit 64 Totems für 12 Mio steht dadurch weit hinten in der
Liste, kostet pro Totem aber nur 187k — also deutlich weniger als das
Einzeltotem für 250k ganz oben auf Seite 1.

`MarketService.scanCheapest()` nimmt deshalb **nicht** den ersten Treffer,
sondern rechnet alles auf Preis pro Stück um, führt ein laufendes Minimum mit
und blättert weiter. Abgebrochen wird erst, wenn zwei Seiten in Folge nichts
Besseres mehr liefern.

---

## Bekannte Grenzen

### Kein Order-Endpunkt

Die offizielle Public-API kennt genau zwei Auktions-Endpunkte:

```
GET /v1/auction/list/{page}          aktive Angebote
GET /v1/auction/transactions/{page}  abgeschlossene Verkäufe
```

Beide Alarmrichtungen vergleichen deshalb gegen den AH-Preis — für den
Elytra-Fall oben ist genau das die richtige Referenz. Falls DonutSMP irgendwann
Orders freigibt: `OrderSource` implementieren, mit
`MarketService.setOrderSource()` einhängen, fertig. Dann nutzt der
Verkaufs-Alarm automatisch das höchste Kaufgebot.

### Der Graph füllt sich erst

Die API liefert keine Preishistorie. Zwei Quellen speisen den Graphen:

- **blau** — günstigstes Angebot, von der Mod selbst gemessen, nur während du
  spielst
- **pink** — echte Verkäufe aus `/auction/transactions`, per Button
  „Verkäufe laden" nachgeholt

Am ersten Tag ist der Graph also dünn. Das ist kein Fehler.

Die Feldnamen des Transactions-Endpunkts sind nicht dokumentiert. `DonutApi`
probiert rund zehn Kandidaten durch (`timestamp`, `time`, `sold_at`,
`createdAt`, `epoch` …) und normalisiert Sekunden auf Millisekunden. Kommt
nichts an: in den Einstellungen auf „API prüfen" tippen und die Namen in
`DonutApi.parseSale()` ergänzen — oder ein
[Issue](../../issues/new?template=api_field_mismatch.yml) aufmachen.

### Beschädigte Items

Die Mod vergleicht Item-ID und Verzauberungen, aber keine Haltbarkeit. Bei
Elytren und Werkzeugen kann die billigste Auktion also eine fast kaputte sein.
Für einen groben Marktpreis reicht das; für den exakten Wert deiner eigenen
Elytra lieber kurz selbst nachschauen.

### Noch nicht gegen 26.2 kompiliert

Die GUI-Schicht wurde in 26.1 umbenannt. Diese Stellen sind die
wahrscheinlichsten Nachbesserungspunkte:

| Alt (1.21.x) | Neu (26.x) | Wo |
|---|---|---|
| `GuiGraphics` | `GuiGraphicsExtractor` | alle Screens |
| `Screen#render` | `extractRenderState` | alle Screens |
| `Screen#renderBackground` | `extractBackground` | alle Screens |
| `drawString` / `drawCenteredString` | `text` / `centeredText` | `Theme` |
| `renderItem` | `item` | Screens |
| `Minecraft#setScreen` | `Minecraft#gui#setScreen` | überall |
| `Minecraft#screen` | `Minecraft#gui#screen()` | `DonutMarketClient` |
| `Gui#setOverlayMessage` | `Gui#hud#setOverlayMessage` | `Notifier` |
| `ResourceLocation` | `Identifier` | `ItemIndex`, `DonutMarketClient` |
| `KeyMapping(…, String)` | `KeyMapping(…, KeyMapping.Category)` | `DonutMarketClient` |

Das Risiko ist bewusst klein gehalten: **alles Gezeichnete — auch der komplette
Graph — benutzt nur `fill()`, `text()`, `centeredText()` und `item()`.** Kein
Blit, kein Scissor, keine Linien-API, keine Sprites, keine Mixins. Scrolllisten
clippen manuell, Tooltips sind selbst gebaut.

Der CI-Build sagt dir sofort, ob etwas nicht passt — und in welcher Phase.
Wenn er rot ist: [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

---

## Aufbau

```
api/       DonutApi (GET mit JSON-Body), toleranter Parser, RateLimiter
market/    Alert, MarketService (Scan + Polling), HistoryStore,
           ItemIndex, PriceUtil, OrderSource
notify/    Notifier — Chat, Overlay, Ding-Ding-Ding
ui/        Theme + MarketScreen, ItemPickerScreen, AlertEditScreen,
           GraphScreen, SettingsScreen
command/   /auctionscanner
config/    DonutConfig
```

**Threading:** Netzwerk läuft auf einem Daemon-Thread (`DonutMarket-Poller`).
Alle fünf Sekunden wird höchstens *ein* fälliger Alarm geprüft — so verteilt
sich die Last, statt alle 60 Sekunden 30 Requests auf einmal zu feuern. Nichts
im Netzwerk-Thread fasst Minecraft-Zustand an; das geht ausschließlich über
`Minecraft#execute`.

**Rate-Limit:** DonutSMP erlaubt 250 Requests/Minute pro Key. Standard hier ist
100, damit andere Mods mit demselben Key nicht ausgesperrt werden.

---

## Dateien

```
config/donutmarket/config.json    Einstellungen inkl. API-Key
config/donutmarket/alerts.json    Alarme
config/donutmarket/history.json   Preishistorie
```

> Der API-Key steht im Klartext in `config.json`. Nicht weitergeben, beim
> Streamen nicht aufmachen. `.gitignore` schließt den Ordner bereits aus.

---

## CI/CD

| Workflow | Auslöser | Tut |
|---|---|---|
| `build.yml` | Push auf `main`, PRs, manuell | baut mit JDK 25, lädt das JAR als Artifact hoch |
| `release.yml` | Tag `v*`, manuell | baut und legt ein GitHub-Release mit JAR an |

Release veröffentlichen:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Optional zusätzlich auf Modrinth: Secret `MODRINTH_TOKEN` und Variable
`MODRINTH_PROJECT_ID` in den Repo-Settings hinterlegen — der Schritt aktiviert
sich dann von selbst.

---

## Lizenz

MIT — siehe [LICENSE](LICENSE).

Nicht mit Mojang, Microsoft oder DonutSMP affiliiert.
