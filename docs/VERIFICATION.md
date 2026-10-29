# Free 0.1 verification — 2026-10-29

The standalone source project was built with Java 25.0.5 and Gradle 9.1.0. The native test server was Paper 26.3 build 223. These are tested versions, not a claim about future compatibility or the latest available server build.

## Automated checks

151 Free tests pass with no failures or skipped tests. Coverage includes integer money arithmetic, finite stock and treasury, food reserves, material reservations, demand/crafting dependencies, finite deposits, hunger/death, logical loaded/background consistency, construction cursor persistence, recovery journals, trust and the source/edition boundary. The three shipped start layouts reference only included Free blueprints; all 19 blueprint data records load.

A clean source-archive rebuild is required to reproduce the production and QA JAR hashes recorded in the release manifest. `scripts/check_distribution.py` checks the source tree and production JAR: no selectable edition resource, no paid implementation classes or paid resource files, and no developer check classes in the public JAR. Only the Free code is published under MIT. The ordinary JAR and QA JAR have identical runtime entries after removing the explicitly identified `ServerChecks` test fixtures.

## Native Paper checks

A disposable flat test world, accelerated server simulation, loaded test chunks and peace mode were used. No resources were granted after the normal village creation supplies.

- A new village physically built its three initial buildings and spawned five real NPC entities.
- Native coin metadata rejected plain nuggets and a renamed sunflower. Denominations, stack compatibility, exact change and vanilla appearance were checked.
- Actual Bukkit inventory menus were opened and clicked through explicit Player proxies: warehouse, needs/tasks, trade, construction, communal catalogue, council and production chains. Russian and English status text were checked. These proxies are not connected Minecraft clients.
- An unfinished communal project was saved with its precise block cursor, then the server was stopped. The next boot retained that cursor, completed the project, built a working grove, produced logs and crafted planks.
- A third boot restored living NPCs and retained paused days and warehouse stock.
- The ordinary production JAR was also started without the developer classes.
- A copied previous Free 0.1 world/database was loaded; village/world identity, names, culture, simulation days, treasury, stock, resident identities/life state and construction cursors were compared.
- A Premium-marked database was refused before normal content loading. Existing database, marker and content files were compared by SHA-256 and retained unchanged.

## Limits

This is a bounded release check, not proof that every server setup is bug-free. No connected-client visual acceptance, large multiplayer load measurement, adversarial player-file crash campaign or complete matrix of protection-plugin versions was run for this split. Uneven terrain, player interference, simultaneous clients, uncertain operation reconciliation and integrations need their own server acceptance tests. No public-server performance figures are promised. Folia and resource packs are not part of this build.

No GitHub repository was created or pushed automatically. Publish only this project's source archive/folder; older full-source archives and the parent private project contain Premium material.
