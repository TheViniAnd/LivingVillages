# Reproducible checks

Run `./gradlew clean test build verificationJar`, then `python3 scripts/check_distribution.py build/libs/LivingVillages-0.1.jar`.

For native checks, prepare a disposable Paper 26.3 build 223 server with Java 25, a new flat plains world containing a bedrock layer, 24 stone, 2 dirt and 1 grass layer, and structures disabled. Accept Minecraft EULA independently. Bind the test server to localhost. Never run the QA JAR in a player world: it creates a settlement, loads a small test area and changes simulation speed.

Use only `build/verification/LivingVillages-Free-QA-0.1.jar`. Let the plugin create its defaults, stop the server, set `debug.server-self-test: true`, and start again. The checks create three real buildings and five real resident entities, exercise native coin items and proxy-driven inventory menus, then persist a partially applied communal construction project. Wait for `SERVER_FREE_CHECKPOINT`, then use `stop`.

Start the same test server again. The check verifies the saved block cursor, resumes the project and waits for a physical grove to produce logs and for planks to be crafted without post-creation resource grants. Wait for `SERVER_FREE_PASS`, then `stop`. A third start must print `SERVER_FREE_RELOAD_PASS`: paused logical stocks and days remain unchanged and living NPCs return. The world is retained for inspection. Each stage has a bounded failure timeout and prints `SERVER_FREE_FAIL` on assertion failure.

The suite uses actual Paper blocks, item metadata, entities and SQLite. Player menu events are driven by explicit test proxies; this is not a connected-client or player-file crash test. For manual acceptance, connect two ordinary clients, check NPC right-click and menus, discovery and local access, buy/sell/change with a full inventory, verify competing last-item purchases, restart during construction, inspect foundations, entrances and enclosures on uneven terrain, and test protection integrations separately.
