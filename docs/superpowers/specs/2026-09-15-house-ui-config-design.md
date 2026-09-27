# Housing UI Configuration Design

## Purpose

Add a complete administrative UI for creating and configuring rental houses and global housing rules. The screens must use the established RotasUtils cream, copper, and parchment theme and remain separate from monster level zones.

## Existing Foundation

Housing already has independent domain models, persistence, a House Wand, world outlines, protection, billing, commands, and client-safe house summaries. `HouseDefinition` owns administrator-authored house identity and bounds. `HouseConfig` owns global economic rules and tiers. `HouseTenancy` remains runtime ownership state. Monster `ZoneDef`, `ZoneService`, and the Zone Wand are outside this feature.

## Screen Structure

### Admin Housing Overview

Add a `HOUSES` section to `AdminMenuScreen`. Its main area contains searchable house rows showing display name, ID, dimension, size, tier, enabled state, and rental status. The action column contains:

- Get House Wand.
- Create from current selection.
- Open Rental Settings.

Selecting a house opens its editor. An empty state explains the two-corner House Wand workflow. Status tags reuse the existing semantic colors: available is green, active is blue/accent, overdue is warning orange, bought-out is purple, and disabled is muted.

### Create House Screen

The creation form collects a normalized ID, display name, and tier. It displays a read-only summary of the server-held House Wand selection, including dimension, coordinates, size, and volume. The client never submits authoritative selection coordinates.

Submitting sends the text fields and selected tier only. The server reads the sender's current House Wand selection, validates permission, identifier, tier, volume, and overlap, creates the house, clears the selection after success, records an audit entry, and resynchronizes content.

### House Editor

The editor exposes display name, tier, enabled state, current bounds summary, and a Replace Bounds action. Save submits the current definition revision. Replace Bounds tells the server to consume the administrator's current House Wand selection and validates the new bounds exactly as creation does.

Removal uses a themed `HouseRemoveConfirmScreen` built on `RotasScreen`. The server rejects removal when the house has an owner or members. The UI cannot override this protection. Disabling an occupied house is also rejected because removing protection while a tenancy exists would expose the owner's property.

### Rental Settings

The settings screen edits one local draft containing:

- currency ID;
- payment interval;
- reminder lead;
- overdue grace period;
- buyout multiplier;
- base member limit;
- member-slot price;
- maximum purchased member slots;
- tier ID, deposit, and maintenance price.

Times are entered as human-readable days, hours, and minutes and converted to milliseconds by a pure validated presentation model. Raw persisted milliseconds are not exposed as the primary input.

At least one tier must remain. Tier IDs are stable identifiers. A tier referenced by any house cannot be removed until those houses use another tier. Duplicate IDs, negative prices, overflow, invalid currency IDs, reminder values outside the payment interval, and values outside domain limits block submission with field-specific feedback.

## Theme and Layout

All new screens extend `RotasScreen`, inheriting the shared window frame and opening transition. They use `Ui`, `PixelUi`, `RotasButton`, the readable UI font, four-pixel spacing, cream surfaces, copper accents, semantic status colors, and existing sound cues. No new font, gradient, glass effect, or independent palette is introduced.

Layouts use calculated bounds and scroll panels rather than fixed lists. The overview and editors must fit the established design canvas at GUI scales 2 and 3. Long IDs, translated labels, tier lists, validation messages, and narrow windows must truncate, wrap, or scroll without overlapping controls.

English and Thai translation keys are added together. Administrative copy remains direct and action-specific.

## Client and Server Data Flow

The client holds presentation snapshots only. Admin snapshots include complete house definitions, config, tiers, current revisions, status summaries, and a sanitized description of the requesting administrator's current House Wand selection. Non-admin snapshots retain the existing reduced house data and do not gain configuration details.

Each mutation packet has a bounded action name and bounded NBT payload. Server handlers execute on the server thread and recheck administrator permission. Definition edits include the expected definition revision. Global settings include a configuration revision added to persisted housing configuration state. A stale request fails without mutation and triggers a fresh synchronization.

Successful changes update `RotasData`, mark it dirty, add an audit record, rebuild any affected house lookup or billing indexes, and synchronize clients. Failed changes preserve both persisted data and the screen's typed draft.

## Validation and Failure Handling

Validation is shared by commands and UI handlers so both entry paths enforce identical rules. The server checks:

- administrator permission;
- payload type, length, and collection limits;
- known house and expected revision;
- valid and unique house or tier IDs;
- known tier references;
- valid currency and numeric ranges;
- selection ownership, dimension, volume, and overlap;
- occupied-house deletion safety;
- tier removal references;
- arithmetic overflow.

No partial update is allowed. A failed global-settings submission does not update any field or tier. A failed bounds replacement leaves the original definition and wand selection intact.

## Testing

Use test-driven development for each behavior slice. Automated tests cover:

- create/edit/config packet parsing and bounds;
- permission rejection;
- stale definition and configuration revisions;
- server-held selection use and successful selection clearing;
- overlap and volume rejection;
- occupied-house removal rejection;
- tier add, edit, duplicate, removal, and reference checks;
- configuration round trips and revision persistence;
- time-field conversion, range validation, and overflow;
- layout calculations for GUI scales 2 and 3;
- English/Thai key parity;
- unchanged monster-zone state during housing mutations.

The final automated gate is `:common:test :forge:build :fabric:build`. Runtime verification opens the overview, create form, house editor, confirmation, and rental settings at GUI scales 2 and 3 in an isolated world; creates and edits a house from the House Wand; restarts the world; and confirms monster zones are unchanged. Build success alone is not visual or gameplay proof.

## Scope Boundaries

This work adds administrator housing configuration UI and the network/state support it requires. It does not merge houses with monster zones, add ownership transfer or selling, move blocks or inventories, redesign unrelated screens, or change rental prices and billing defaults.

## Acceptance Criteria

An authorized administrator can use the themed Admin UI to create a house from the current House Wand selection, browse houses, edit name/tier/enabled state/bounds, safely remove an unoccupied house, configure every global rental rule, and add/edit/remove valid tiers. All mutations are server-authoritative, revision checked, persisted, audited, and synchronized. English and Thai UI remain readable at GUI scales 2 and 3, and monster zone behavior is unchanged.
