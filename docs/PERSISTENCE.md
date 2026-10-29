# Persistence boundary

The logical village is authoritative. NPC entity IDs, position records, stock, resource deposits, construction cursor and reserved materials persist in SQLite snapshots. Container inventories and menus are views, not separate warehouse copies.

Trading, deposits and payouts plan the complete result before mutation, reserve the player and village, journal the before/after inventory and village snapshot, then apply and commit. Repeated clicks and competing transactions cannot reuse the same reservation. On reconnect/restart, matching before/after inventories allow deterministic resolution; mismatched state is quarantined. Administrator recovery must inspect the actual player inventory and operation rather than blindly issuing another payout. Player files and SQLite cannot share one atomic transaction; server crashes, external inventory plugins and administrator item duplication cannot be made transactionally equivalent by this plugin.

Free uses the released village JSON format 15 and SQLite schema migrations through 3. A raw JSON guard rejects populated Premium ownership, leadership, diplomacy, armies, animal/rare-production records, upgraded buildings and unsupported cultures before Gson ignores fields. Empty legacy metadata fields are accepted. A separate folder marker rejects Premium databases before normal content loading. The JSON guard and marker are compatibility protections, not DRM.

Do not share a live database between servers. Stop the server before taking a consistent folder backup. Ordinary server shutdown saves logical state and removes physical NPC projections; restart restores living records in loaded chunks and does not treat unload as death. Simulated deaths remain authoritative.
