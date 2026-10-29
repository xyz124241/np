# NeptunPets

Pets nur für OPs. Befehl: `/np` (Alias `/neptunpets`). Paper 1.21.x, Java 21.

## Befehle
| Befehl | Funktion |
|---|---|
| `/np` | Menü mit allen Mobs (Seiten, Suche) |
| `/np <Suche>` | z. B. `/np blue axolotl` – bei genau einem Treffer direkt in den Editor |
| `/np name` | Pet, das du anschaust, per Amboss umbenennen |
| `/np remove` | Pet, das du anschaust, entfernen |

## Editor
- **Variante**: alle Varianten werden automatisch erkannt (Axolotl, Katze, Pferd, Wolf, Frosch, Panda-Gene, Schaf-Farbe ...). Nochmal klicken = zufällig. Bei Tieren gibt es zusätzlich die Zeile **Alter** (Erwachsen oder Baby, wächst in 5 / 20 / 60 min).
- **Effekte**: Trankeffekte an/aus, Stärke, Dauer (oder unendlich).
- **Einstellungen**: folgt dir, teleportiert sich, greift Spieler an, kann sich bewegen, Leine, andere dürfen füttern / schlagen / schubsen, kann älter werden (aus = Alter eingefroren).
- **Name**: Amboss mit Papier (nicht entnehmbar), Name per MiniMessage, z. B. `<gradient:red:gold>Bello</gradient>`.

## Regeln
- Pets gehören dem Spieler, der sie spawnt. Verliert er OP, sterben seine Pets (online innerhalb 1 s, offline beim Laden des Chunks).
- Pets droppen nichts.

## Bauen
`mvn -B package` → `target/NeptunPets-1.0.0.jar` (oder GitHub Actions → Artifacts).
`paper.version` in der `pom.xml` an deine Server-Version anpassen.

## Hinweis
Nicht gegen die Paper-API kompiliert und nicht auf einem Server getestet.
