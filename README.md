# LivingVillages 0.1 — Free

Server-side settlements for Minecraft Java Edition, Paper 26.3. Players use an ordinary Minecraft client and standard textures. No resource pack or required gameplay plugin.

This is the **standalone MIT-licensed Free source project**. It contains one fictional culture, village levels 1–3, up to 12 residents per village, up to 5 villages per world and a communal building catalogue. Free includes slow food/wood/stone production, ordinary crafting, hunger, housing and immigration, gradual construction, physical coins, finite village treasuries, demand-based trading, personal trust, delivery tasks, NPC routines, local guards, maps and information boards. Production needs physical completed workplaces, workers, tools and verified resources. Resources and construction reservations share one persistent model.

Premium is distributed separately. Its other cultures, expanded architecture, building upgrades, private house orders, government, armies, wars, monuments and rare weapons are **absent from this source tree**. There is no Premium switch, key or downloadable Premium module here. Limits in open source can be modified; they are product defaults, not copy protection. Premium notices can be disabled in `config.yml`.

## Build

Install JDK 25, then run:

```sh
./gradlew clean test build
python3 scripts/check_distribution.py build/libs/LivingVillages-0.1.jar
```

Windows: use `gradlew.bat`. Gradle Wrapper 9.1.0 downloads Gradle and the build downloads dependencies on the first run. The output is `build/libs/LivingVillages-0.1.jar`. Minecraft server software, JDKs and dependency caches are not included in this repository.

## Install and language

1. Stop the server and back up its world and `plugins/LivingVillages` folder.
2. Put **one** LivingVillages JAR in `plugins`; remove older copies and the other edition.
3. Start Paper 26.3 with Java 25. Do not use Bukkit `/reload` to replace plugin binaries.
4. English is the default. In `plugins/LivingVillages/config.yml`, set `language: ru` for Russian, then use `/lv reload` or restart. `language: en` selects English.

Folia is not supported. WorldGuard and GriefPrevention are optional region integrations. Unknown plugins can conservatively block placement; configure the explicit allowlist only after checking their protection behavior. Automatic generation examines new chunks; administrative `/lv create` is available for a checked site on older terrain. It does not replace vanilla villages.

## Commands

| Command | Purpose |
| --- | --- |
| `/lv help` | Command descriptions |
| `/lv menu` | Personally discovered villages; village actions require being inside its territory |
| `/lv status` | Nearby village state and blocking reasons |
| `/lv claims` | Collect retained property/returns |
| `/lv edition` | Free limits and Premium information |
| `/lv admin` | Administrative village and trust controls |
| `/lv create polevichi [layout]` | Check and create a test settlement; administrator only |
| `/lv coins <amount> [player]` | Give test money, in bronze units; administrator only |
| `/lv pause`, `/lv npc status`, `/lv npc repair` | Simulation and NPC diagnostics; administrator only |
| `/lv bypass`, `/lv diagnostics`, `/lv generation`, `/lv operations` | Administrative protection, performance and recovery tools |
| `/lv reload` | Validate supported settings and reload |

`livingvillages.admin` grants administrative tools; `livingvillages.bypass` permits the explicit bypass toggle. Both default to operators. NPC right-click supports chat; special residents also provide their village/trade interface. Gift access starts at 50 trust; player-requested communal construction starts at 100. Delivery tasks remain available to newcomers, one active delivery at a time.

## Persistence and upgrades

SQLite data and the operation journal live in `plugins/LivingVillages`. One server owns the folder. This project supports the released Free 0.1 village format 15 and SQLite schema 3. Unsupported earlier formats and Premium saves are rejected; never change edition markers to force a conversion. Missing Premium fields are inspected **before** deserialization so unsupported property is not silently discarded. Back up before migration.

Server shutdown pauses simulation. Production uses elapsed running-server time rather than daylight or wall-clock time while stopped. Unloaded chunks do not require resident entities; construction still needs world application before a new workplace produces. A database transaction cannot atomically include Minecraft player files: ambiguous interrupted inventory operations are quarantined for administrator reconciliation. See [persistence details](docs/PERSISTENCE.md).

## Verification

`./gradlew test` tests money, finite stock/treasury, construction reservations, production/hunger and persistence. Optional `./gradlew verificationJar` creates a separate QA JAR containing `ServerChecks`; the ordinary JAR excludes those fixtures. See [server checks](docs/TESTING.md). Model tests do not establish client rendering, connected-player behavior or public-server capacity. No throughput guarantees are made.

See [Russian instructions](README.ru.md), [release verification](docs/VERIFICATION.md) and [third-party notices](THIRD_PARTY.md). The project code and original game data in this tree are MIT-licensed; dependencies retain their own licenses. Premium source and distribution are not licensed by this repository.
