# Usability overhaul implementation ledger

Approved plan: full administrator and player usability overhaul, equal in-game/file workflows, explicit review/apply, all existing systems.

Backup: `backup/usability-20260908-003009.zip`. No Git repository; changes remain in the supplied workspace. Installed artifacts remain unchanged.

## Tasks

- Shared legacy controls and metadata: in progress (legacy_forms).
- Guided kernel forms/templates: in progress (guided_editor).
- Configuration drafts, file exchange, permissions, navigation: in progress (root).
- Player hub and merchant quantities: in progress (player_usability).
- Integration review, common tests, Forge/Fabric builds, runtime validation: pending.

## Decisions

- Preserve the current parchment theme found in the live source, including HUD and inventory geometry.
- Existing configuration domains retain their stores; new draft/history state lives in RotasData, never player records.
- File imports are explicit, drafts are private, stale applies reject without destroying edits.
- Export snapshots use new server-side directories and do not rewrite source packs.
