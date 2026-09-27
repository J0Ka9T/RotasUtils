# RPG admin framework (Phase 3)

The RPG Content Studio is available from `/rotas admin` > RPG Content Studio.
It extends the existing LenlorUI administration menu. Existing quest, board,
skill and level editors remain available. Specialized monster, boss, merchant
and graph editors will build on this backend in later phases.

## Draft workflow

Create a draft, open an existing definition or start a new one, save the edited
JSON to the draft, validate, inspect the preview, then apply. Saving a draft does
not change live content. Invalid references or definitions reject apply and keep
the draft for repair. The editor confirms abandoning unsaved local text and
restoring a historical revision. Drafts saved on the server survive disconnect
and restart; unsaved text exists only in the current screen.

The editor uses the same ContentRegistry, ConditionEngine, ActionEngine and
stat validation as disk packs. Definitions retain their layer; new definitions
start in HOTFIX. Replacing a definition cannot silently change its kind.
The generic JSON editor provides the foundation before specialized forms exist.
The browse control cycles through pages of 32 definitions. Selecting a revision
opens confirmation before rollback. Small GUI layouts share tested bounds.

Each actor has a private draft with a base revision and edit generation. Apply
revalidates the captured draft on the loader worker, then checks its generation,
current live revision and permission again on the server thread. A stale draft
must be recreated from live content. It is not silently merged or overwritten.
At most one content preparation runs at a time; it never mutates world state on
the loader worker. Profile balances and progress are outside configuration rollback.

## Commands

- `/rotas admin status`: live revision, hash, diagnostics and your draft status.
- `/rotas admin draft create`: copy the active effective definitions.
- `/rotas admin draft put <JSON>`: add/replace one draft definition.
- `/rotas admin draft remove <id>`: remove from the draft, subject to validation.
- `/rotas admin draft validate`: compile without publishing.
- `/rotas admin draft preview`: bounded added/changed/removed ID summary.
- `/rotas admin draft apply`: revalidate and publish if still current/authorized.
- `/rotas admin draft discard`: remove your saved draft, retaining live content.
- `/rotas admin history`: recent retained revisions, parents, actor and time.
- `/rotas admin rollback <revision>`: validate historical content and publish it
  as a new revision, preserving the revision chain.
- `/rotas admin show <id>`: inspect live JSON; long output is capped for chat.
- `/rotas admin audit`: recent existing Rotas audit entries.
- `/rotas reload`: explicitly import disk packs into a new revision if changed.

Console/command-block drafts use actor `server`; players use their UUID. Long
definitions are supported through the chunked editor transport or server console;
vanilla chat command length limits still apply to commands typed in chat.

## Permissions and networking

RotasPermissions defines view, edit, apply, rollback, reload and audit capabilities
with `rotas.admin.*` nodes. Defaults require at least OP 2 and respect a stricter
existing admin OP setting. Addons may register a permission adapter during mod
initialization; no permission mod is required. Legacy editor permissions retain
their existing rules. The new backend checks every request and rechecks permission
before asynchronous publication, including disconnects for network requests.

Admin requests and responses are isolated from public profile/content sync. Only
the requesting authorized admin receives the selected definition, their draft and
bounded list/history pages. Mutation requests carry the expected live revision
and draft generation; completed transport IDs cannot be replayed in that session.
Unrecognized actions are rejected. Packets are <=24 KiB payload chunks with a
512 KiB assembly cap, a single ordered transfer and a 10-second deadline. A
session admits 48 chunks per 10 seconds; at most 32 admin sessions are retained.
Logout/shutdown clears server transport state. GUI requests use a screen UUID so
late replies cannot overwrite another editor instance. Text edits are disabled
while saving; resize preserves the current buffer.

## Revisions and persistence

ContentHistory lives inside the existing RotasData SavedData. Active content,
revision metadata and drafts share that world save. Startup restores the last
active revision; it does not overwrite rollback with files from disk. Use
`/rotas validate` to check disk packs and `/rotas reload` to import them explicitly.
The first startup without history bootstraps from disk. Disk pack files are never
rewritten by draft application or rollback.

Revisions contain references to SHA-256-addressed definition documents. Unchanged
documents are shared across history and drafts; unreferenced documents are
collected. Retention is 64 revisions and 32 drafts with a 32 MiB content-history
budget. At that byte cap new changes fail closed; discard unused drafts or reduce
retained content with an offline backed-up migration before retrying. Arbitrary
older revisions are not recoverable once evicted. Revision numbering remains
monotonic across restart. Normal SavedData save timing applies; this is not a
separate fsync journal. Future schemas and document integrity failures are rejected.

Audit uses the existing bounded 500-line audit store; successful revisions also
retain actor, reason, parent, content hash and timestamp. Kernel reload diagnostics
retain bounded errors. Player refresh failures are reported separately after a
content publication; they do not falsely report the published revision as rejected.

## Verification

51 unit tests and common/Forge/Fabric build tasks pass. Added checks cover invalid
draft isolation, two-editor conflicts, mutation during validation, revision/draft
NBT persistence, rollback/restart, document integrity, retention/deduplication,
large admin packet round trips, malformed/oversized packets, stale client state
and layout bounds from 240x200 to 1920x1080 GUI pixels.

The Java 17 / Forge 47.4.22 console smoke exercises authorized editing, non-OP
denial, asynchronous permission revocation, validation, apply, invalid apply,
rollback, saved unfinished drafts and restart. See `build/rpg-console-smoke.log`.
The development-only test mod is excluded from distributable jars.

GUI rendering/interaction and authenticated client packet exchange have not been
runtime verified. No GUI automation was used, as required by the brief. Those
gates and full PROJECT-RC compatibility remain open. The Phase 3 backend checks
do not establish completion of the full RPG platform.


## Finding your way around the admin menu

The sidebar is grouped rather than flat: **Content** (quests, monsters, items, bosses, merchants),
**In the world** (NPCs, quest boards), **Players** (progression, skills, player records) and
**Server** (content health, rules, files, audit). Fourteen equal-weight entries read as a wall; four
groups read as a map.

Overview is a running order, not a menu of everything. It lists the four steps that produce a quest
players can actually take - write it, post it on a board, or give it to an NPC, then check content
health - each showing whether that step is done. Tuning and operations sit below it, unnumbered,
because they are not part of getting started.
