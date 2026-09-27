# House Rental System Design

## Purpose

Add a server-authoritative house rental system to RotasUtils for Minecraft 1.20.1 on Forge and Fabric. Administrators define houses with a new House Wand. Players can rent an available house, maintain it through recurring payments, buy it out permanently, and manage members. House regions and their rules are entirely separate from monster level zones.

## Scope

The first release includes:

- A dedicated House Wand and house-region visualization.
- Admin commands for creating, inspecting, editing, enabling, and removing houses.
- Player commands and screens for renting, paying, buying out, viewing status, and managing members.
- Optional house signs that open the correct player flow.
- Persistent ownership, members, billing state, and configuration.
- Server-authoritative protection for block breaking, placement, containers, and door-like interactions.
- Real-time billing, reminders, an overdue grace period, repossession, and restart catch-up.
- Configurable tiers, prices, timings, currency, member limits, and future member-slot pricing.
- Audit records and automated tests for economic and ownership transitions.

The first release does not include ownership transfers, house sales, voluntary surrender, member-funded payments, automatic block cleanup, item recovery for former owners, rent bidding, or WorldGuard integration.

## Boundaries

Housing is a new subsystem. It must not store houses as `ZoneDef`, call `ZoneService` for ownership decisions, or alter the monster Zone Wand. It may reuse neutral project infrastructure such as `RotasData`, permission checks, audit logging, UI primitives, networking conventions, and event registration.

House definitions and runtime tenancy are separate:

- A `HouseDefinition` describes administrator-authored identity, dimension, cuboid bounds, tier, sign binding, display name, and enabled state.
- A `HouseTenancy` describes owner UUID, members, lease kind, payment deadlines, overdue state, purchased member slots, and revision.
- Tier and global economic rules are configuration, not copied into each tenancy. Transactions record the charged amount and rule revision for auditability.

Definitions, tenancy state, and billing indexes live in the existing overworld `RotasData` saved data. The server is the only authority. Clients render bounded snapshots and submit requested actions; they never calculate or commit ownership, access, prices, or deadlines.

## House Wand Workflow

`/rotas house wand` gives an authorized administrator the House Wand. The item is separate from the monster Zone Wand in registration, NBT keys, service logic, tooltip text, and preview rendering.

- Left-click a block to set corner 1.
- Right-click a block to set corner 2.
- Both corners must be in the same dimension.
- Selection uses exact X, Y, and Z bounds; it does not expand to full build height.
- Sneak-use in air clears the unfinished selection.
- Normal use in air opens the house administration screen.
- `/rotas house create <house_id> <tier>` validates and creates the selected house.

House IDs are normalized namespaced IDs. Creation fails if the selection is missing, volume exceeds configured safety limits, the tier is unknown, or the cuboid overlaps another enabled house in the same dimension. Adjacent faces are allowed. An interval/spatial index provides bounded point lookup without scanning every house for each event.

The admin screen exposes the same operations as commands. File configuration and UI changes use RotasUtils' existing draft, review, and apply behavior where applicable; imports never mutate live gameplay immediately.

## Configuration

Housing configuration contains:

- `enabled`
- `currency`, default `rotas:gold`
- `payment_interval_seconds`, default 259200 (3 days)
- `reminder_lead_seconds`, default 86400 (24 hours)
- `overdue_grace_seconds`, default 3600 (1 hour)
- `buyout_multiplier`, default 10
- `base_member_limit`, default 5
- `member_slot_price`, disabled/unset initially
- `max_purchased_member_slots`, default 0
- maximum house count, per-house volume, and selection coordinate limits
- bounded scheduler batch size and audit retention
- tier definitions containing deposit and maintenance fee

All monetary values are non-negative bounded integers. Timing values have safe minimums and maximums. Unknown currencies or tiers fail validation. Changing a tier affects future charges, while an already overdue charge retains its recorded amount until paid or superseded by repossession.

## Tenancy State Machine

The persisted states are:

- `AVAILABLE`: no current owner; anyone may rent it.
- `ACTIVE`: rented and current on maintenance.
- `OVERDUE`: payment failed; owner and members retain normal access during the grace period.
- `BOUGHT_OUT`: permanent owner; no recurring billing.

There is no separate persistent `LOCKED` state. At the end of the grace period, repossession atomically clears the owner and members and changes the house to `AVAILABLE`. This exactly represents a locked former tenancy while making the house immediately rentable.

State transitions:

1. `AVAILABLE -> ACTIVE`: an eligible player pays the tier deposit. Ownership, first deadline, and empty member list commit with the wallet debit.
2. `ACTIVE -> ACTIVE`: at the deadline, automatic maintenance payment succeeds and advances the next deadline by one interval.
3. `ACTIVE -> OVERDUE`: automatic payment fails. The charge amount and grace deadline are recorded and the owner is notified.
4. `OVERDUE -> ACTIVE`: the owner manually pays the recorded overdue charge before another player rents the house. The next payment deadline is based on the successful recovery time, avoiding immediately repeated overdue cycles.
5. `OVERDUE -> AVAILABLE`: grace expires. Owner and members are cleared; placed blocks and inventories remain untouched.
6. `ACTIVE|OVERDUE -> BOUGHT_OUT`: the owner pays the deposit multiplied by the configured buyout multiplier. When overdue, the recorded overdue charge is added to that price and both amounts are paid in one atomic transaction.

After repossession, the former owner has no special stored claim. They may rent the now-available house like any other player; whichever valid rent transaction commits first wins. If the former owner pays through the recovery action before repossession is committed, they keep the house. No stale screen or delayed packet can reclaim a house after another player has rented it.

## Time and Scheduling

Deadlines use persisted epoch milliseconds from the server clock. A bounded priority queue indexes the next reminder, payment, and grace-expiry work. The server processes due entries periodically on its main thread and caps work per tick.

On startup, the index is rebuilt from persisted tenancies. Overdue work is caught up deterministically without an unbounded loop. If the first missed deadline plus its grace period has already elapsed, the tenancy is repossessed without taking a late partial payment. Otherwise, all maintenance installments due through the current time are aggregated and attempted as one atomic debit. A successful debit advances the deadline to the first interval strictly after the current time; a failed debit records the aggregate charge and preserves the grace deadline derived from the first missed payment. Clock rollback never shortens an already persisted deadline.

Reminders are delivered immediately when the owner is online. Offline reminders are queued as a bounded mailbox notification and shown on the next login. Reminder delivery is idempotent per tenancy revision and deadline.

## Economy Transactions

The existing RotasUtils wallet is the currency source of truth. Every rent, maintenance, recovery, buyout, and future slot purchase runs on the server thread.

Each operation validates authorization, expected tenancy revision, state, price, and balance before mutation. Wallet debit and tenancy mutation are committed together through a transaction boundary. If any validation or mutation fails, neither side changes. Stable receipt IDs prevent replay after packet duplication or retries. Overflow, invalid price, full receipt storage, or unavailable data fails closed with a useful player message.

Automatic payment only debits the owner. Members cannot pay through the housing system.

## Members

Only the active owner can add or remove members. The maximum is:

`base_member_limit + purchased_member_slots`

The initial configuration disables slot purchases while retaining the field and transaction path for later activation. Adding a member rejects the owner UUID, duplicates, nonexistent/unresolvable player identities, and limits. Removing a member takes effect immediately. Membership is cleared on repossession and does not transfer to a new tenant.

Commands accept player names for usability but resolve and persist UUIDs. Screens display the best known player name while authorization always uses UUIDs.

## Protection Rules

For every relevant server event, `HouseProtectionService` looks up the enabled house containing the target block and evaluates the current tenancy:

| Action | Owner | Member | Visitor | Available house |
|---|---:|---:|---:|---:|
| Walk through open space | Allow | Allow | Allow | Allow |
| Open or close doors, gates, trapdoors | Allow | Allow | Deny | Deny |
| Break blocks | Allow | Allow | Deny | Deny |
| Place blocks | Allow | Allow | Deny | Deny |
| Use containers or storage entities | Allow | Allow | Deny | Deny |
| Manage members | Allow | Deny | Deny | Deny |
| Pay or buy out | Allow | Deny | Deny | Deny |

Movement is never cancelled. An open door therefore permits passage naturally. Protection covers common vanilla interaction routes and loader-specific events for block breaking, placement, right-click use, buckets/fluids, explosions, pistons crossing boundaries, entity-based containers, and automation where an untrusted actor could mutate protected contents. Environmental behavior is governed by explicit configuration and defaults to preserving the house against external explosions and cross-boundary piston movement.

Operators do not silently bypass protection. Administrative bypass is explicit, permission-checked, visible in feedback, and audited.

## Signs and Player UX

An administrator binds a sign while editing a house. The binding records dimension and block position in the house definition; the sign text itself is presentation, not authority. Breaking or replacing the sign invalidates the binding safely without deleting the house.

Using a valid sign opens a compact house screen showing:

- House name, tier, status, owner, and member count.
- Deposit or maintenance cost and wallet balance.
- Exact next payment, reminder, or grace deadline.
- The single primary action appropriate to the state: Rent, Pay overdue, Buy out, or Manage.
- Clear confirmation before irreversible payments.

`/rotas house` opens the same player screen. Command equivalents remain available for accessibility and server administration. English and Thai localization are included. The UI follows the existing RotasUtils visual language and remains usable at GUI scales 2 and 3.

## Commands

Player-facing commands:

- `/rotas house list`
- `/rotas house info [house_id]`
- `/rotas house rent <house_id>`
- `/rotas house pay <house_id>`
- `/rotas house buyout <house_id>`
- `/rotas house members <house_id>`
- `/rotas house member add <house_id> <player>`
- `/rotas house member remove <house_id> <player>`
- `/rotas house manage [house_id]`

Admin commands:

- `/rotas house wand`
- `/rotas house create <house_id> <tier>`
- `/rotas house edit <house_id>`
- `/rotas house enable <house_id>`
- `/rotas house disable <house_id>`
- `/rotas house remove <house_id>`
- `/rotas house bind-sign <house_id>`
- `/rotas house inspect <house_id>`
- `/rotas house validate`

Destructive admin operations require confirmation through the review/apply workflow or an explicit command confirmation token. Removing an occupied house is rejected until an administrator intentionally resolves the tenancy.

## Data Integrity and Failure Handling

- NBT readers enforce schema versions, collection limits, string lengths, valid UUIDs, coordinate bounds, member limits, and allowed states.
- Invalid individual houses are quarantined from gameplay and reported to administrators rather than granting access accidentally.
- Duplicate IDs, overlaps, invalid tiers, invalid sign bindings, and impossible deadlines appear in validation output.
- Every mutation increments a tenancy or definition revision so stale requests fail safely.
- Repossession never removes or relocates blocks, block entities, entities, or items in the region.
- World saves use the existing `SavedData` lifecycle. Critical economic operations mark data dirty immediately.
- Audit entries include actor, action, house ID, prior/new state, amount, currency, revision, and timestamp without exposing sensitive unrelated player data.

## Components

The implementation should introduce focused units with these responsibilities:

- `HouseDefinition`, `HouseBounds`, `HouseTier`, `HouseTenancy`, and `HouseStatus`: validated domain state and NBT codecs.
- `HouseRegistry`: definition lookup, overlap validation, and spatial indexing.
- `HouseService`: authoritative state transitions and membership operations.
- `HouseBillingService`: due-work index, reminders, catch-up, and repossession.
- `HouseProtectionService`: pure access decisions plus event-facing helpers.
- `HouseWandItem` and `HouseWandService`: selection only.
- `HouseCommands`: player and administrator command routing.
- `HouseNetwork`: bounded snapshots and action requests using existing networking conventions.
- House player/admin screens: presentation and request submission only.
- Loader adapters: only behavior that Architectury common events cannot express consistently.

Avoid a single large manager class. Domain transition methods should be testable without launching Minecraft wherever practical.

## Verification

Automated verification must cover:

- NBT round trips, corrupt input, limits, and migrations.
- Cuboid containment, adjacency, overlap rejection, dimension separation, and index consistency.
- Every allowed and rejected state transition.
- Exact-boundary payment, reminder, grace-expiry, restart catch-up, and clock rollback cases.
- Insufficient funds, overflow, replayed request, stale revision, and atomic rollback.
- Two players racing to rent the same house.
- Former-owner recovery before repossession versus a completed new tenancy.
- Member limits, UUID identity, immediate removal, and membership clearing.
- The full permission matrix, including doors, containers, block entities, fluids, explosions, and pistons.
- Sign binding invalidation and stale sign interaction.
- English and Thai translation-key completeness.

Build gates are `:common:test`, `:forge:build`, and `:fabric:build`. Runtime verification must use an isolated test world and demonstrate wand creation, persistence across restart, rent/payment/overdue/repossess/rerent, visitor denial with open-door movement still allowed, owner/member management, sign UI, and both GUI scales. Forge and Fabric event behavior must both be exercised before claiming cross-loader completion.

## Acceptance Criteria

The feature is complete when an administrator can create an independent house with the House Wand; a player can rent it using configured currency; ownership and protection survive restart; billing progresses from reminder through payment or repossession; the first valid renter after repossession receives control without world-content cleanup; owners can manage members and buy out the house; all values are configurable; no ownership transfer exists; monster zones behave exactly as before; automated builds/tests pass; and the critical gameplay flow is verified on both supported loaders.
