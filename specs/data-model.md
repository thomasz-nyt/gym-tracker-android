# Data Model

## Core principle

**Log sets, not workouts.** A `session` is a thin container; everything —
charts, PRs, AI context — derives from the `sets` table. There is deliberately no
"activity type" concept anywhere in the schema (constitution §1).

One structure sits between a session and its sets: `session_exercises`, so that an
exercise added to a session can exist before its first set is logged, and so the
same exercise can appear twice in one session (both US-02 criteria). See
`adr/0004-session-exercises-table.md`. This is not a workout template — there are
still no activity types, and every chart and PR derives from `sets`.

All wearable-derived fields are **nullable**, always. Nothing in the model assumes
a watch exists.

## Domain entities (`:core:domain`, pure Kotlin)

```kotlin
@JvmInline value class UserId(val value: String)
@JvmInline value class ExerciseId(val value: String)
@JvmInline value class SessionId(val value: String)
@JvmInline value class SessionExerciseId(val value: String)
@JvmInline value class RoutineId(val value: String)
@JvmInline value class RoutineItemId(val value: String)

enum class BodyPart { CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS,
                      QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, FULL_BODY }

// UNSPECIFIED means the catalog recorded no equipment; OTHER means equipment that
// exists and is miscellaneous. Keeping them apart is what stops the M3 filter from
// claiming knowledge it does not have — constitution §2, ADR-0015.
enum class Equipment { MACHINE, CABLE, BARBELL, DUMBBELL, SMITH,
                       BODYWEIGHT, KETTLEBELL, BAND, OTHER, UNSPECIFIED }

data class Exercise(
    val id: ExerciseId,
    val name: String,
    val aliases: List<String>,
    val primaryMuscles: List<BodyPart>,
    val secondaryMuscles: List<BodyPart>,
    val equipment: Equipment,
    val instructions: List<String>,
    val mediaUrl: String?,        // gif or video, cached locally
    val mediaType: MediaType?,    // GIF | VIDEO | NONE
    val youtubeUrl: String?,
    val source: String,           // provenance: "free-exercise-db" | "household"
    val isStarter: Boolean,       // curated above the alphabetical tail (ADR-0007)
    val imageAsset: String?,      // bundled photo filename; null means no image slot
)

data class WorkoutSession(
    val id: SessionId,
    val userId: UserId,
    val gymName: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val metrics: SessionMetrics?, // null unless a health source provided them
    val routine: RoutineOrigin?,  // schema v9, US-32, ADR-0028 — see the note below the Room schema
)

/** All fields nullable. Absence is a first-class state, never zero. */
data class SessionMetrics(
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val activeKilocalories: Int?,
    val source: String?,          // "health_connect" | "healthkit"
)

/**
 * A session's routine, written once at start and never read back through a repository
 * (US-32, ADR-0028). [id] is a bare `String`, deliberately not [RoutineId]: resolving it back
 * to a routine — `routines.find(RoutineId(id))` — takes a deliberate, greppable wrap that
 * isn't there today, which is most of what keeps this provenance rather than a live pointer.
 */
data class RoutineOrigin(
    val id: String,
    val name: String,
)

/**
 * Sets, reps and load for one movement (US-30, ADR-0027) — a [RoutineItem]'s plan for it, or
 * the snapshot a [SessionExercise] copied when the session started. Each field independently
 * nullable. Never written to `sets`, and never read by anything that computes a derived
 * number — volume, the trend, Epley and personal records all read `sets` alone.
 */
data class MovementTarget(
    val sets: Int?,
    val reps: Int?,
    val weightKg: Double?,
)

/** An exercise as it appears in one session. The same exercise may appear twice. */
data class SessionExercise(
    val id: SessionExerciseId,
    val sessionId: SessionId,
    val exerciseId: ExerciseId,
    val position: Int,            // 1-based order within the session
    val target: MovementTarget?,  // schema v8, US-30, ADR-0027 — copied from RoutineItem at start
)

data class ExerciseSet(
    val id: String,
    val sessionExerciseId: SessionExerciseId,
    val setIndex: Int,            // 1-based within the session_exercise
    val weightKg: Double?,        // canonical unit is ALWAYS kg
    val reps: Int,
    val rpe: Double?,             // 5.0..10.0 in 0.5 steps
    val performedAt: Instant,
)

/**
 * A saved *shape* — a name and an order (US-29, ADR-0020).
 *
 * Note what is absent, and that it is absent on purpose: no sets, no reps, no load.
 * A routine says which movements and in what order. What you lifted last time is read
 * from `sets` through PrefillFromLastSet at render time, never stored here.
 */
data class Routine(
    val id: RoutineId,
    val userId: UserId,
    val name: String,
    val position: Int,            // 1-based order in the member's list
)

/**
 * One movement's place in a routine. Carried no target as of schema v7 (ADR-0020 option 3);
 * ADR-0027 (US-30, schema v8) adds one — see the "Targets" note below the Room schema.
 */
data class RoutineItem(
    val id: RoutineItemId,
    val routineId: RoutineId,
    val exerciseId: ExerciseId,
    val position: Int,            // 1-based order within the routine
    val target: MovementTarget?,  // schema v8, US-30, ADR-0027
)
```

### Units

Store kilograms everywhere. Convert only in the presentation layer, driven by the
member's `unitPreference`. There must be a single `UnitConverter` in
`:core:domain` with a rounding-behaviour test table. Unit bugs in a lifting app are
uniquely infuriating; do not scatter conversions.

### Identity before M2

M1 has no auth. On first launch the app generates one **local member UUID**
(stored in DataStore) and stamps it on every session and set as `user_id`. One
device = one member until M2. At M2 sign-in, the local UUID's rows are
re-assigned to the authenticated Supabase user id in a single UPDATE before the
first sync.

## Room (local, source of truth for the UI)

Tables mirror the domain entities plus sync bookkeeping:

```
exercises(id PK, name, aliases_json, primary_json, secondary_json, equipment,
          instructions_json, media_url, media_type, youtube_url, source,
          is_starter, image_asset, updated_at)

sessions(id PK, user_id, gym_name, started_at, ended_at,
         avg_hr, max_hr, active_kcal, metrics_source,
         updated_at, sync_state,
         routine_name NULL, routine_id NULL)

session_exercises(id PK, session_id FK→sessions ON DELETE CASCADE,
                  exercise_id, position,
                  target_sets NULL, target_reps NULL, target_weight_kg NULL,
                  updated_at, sync_state)

sets(id PK, session_exercise_id FK→session_exercises ON DELETE CASCADE,
     set_index, weight_kg, reps, rpe, performed_at,
     updated_at, sync_state)

routines(id PK, user_id, name, position, created_at,
         updated_at, sync_state)

routine_items(id PK, routine_id FK→routines ON DELETE CASCADE,
              exercise_id FK→exercises, position,
              target_sets NULL, target_reps NULL, target_weight_kg NULL,
              updated_at, sync_state)

sync_queue(id PK, entity, entity_id, op, payload_json, created_at, attempts)
```

`routines` and `routine_items` are **additive** (schema v7, US-29): `sessions`, `sets` and
`session_exercises` are untouched by them. Starting a routine copies its items into
`session_exercises`, after which the session is an ordinary session and every M1 story keeps
working on it unchanged. As of schema v9 (see the routine-provenance note below), a session
carries a copy of the routine's name and id — but still no foreign key, and no query joins
`sessions` back to `routines`. Editing today never edits Tuesday.

`session_exercises.exercise_id` deliberately has an index but **no Room foreign key** to the
derived catalog. Migrations v4→v5 and v5→v6 wipe and re-seed `exercises` while session history
survives; an FK would make that impossible. Read paths therefore keep an honest missing-catalog
fallback. `routine_items.exercise_id` does have the FK because backup validation rejects a file
whose routine references an id the current bundled catalog no longer contains.

**Targets (schema v8, US-30, ADR-0027).** The three nullable `target_*` columns above are new,
and `session_exercises` gains the same three, which `StartSessionFromRoutine` fills in as it
copies. That duplication is the point: the session carries its own snapshot of what was planned,
so there is still **no foreign key back to the routine and nothing to join on**, and editing the
routine next week cannot rewrite what last Tuesday was planned to be. ADR-0027 rejected the
join-back implementation for exactly that reason.

The sentence this paragraph replaces said there was no field a "planned vs actual" comparison
could be built from. That is no longer true, and ADR-0027 is explicit that it is the real cost
of the decision: a comparison is now *expressible*, and what stops it being dishonest is a
labelling rule and its tests rather than a missing column.

**`sets` still has no target column, and must not gain one.** A logged set records what
happened; the target lives on the *appearance* of the exercise. Every derived number — volume
(US-17), the trend (US-16), Epley, personal records (US-18) — reads `sets` alone, so a planned
load that was never lifted can never become a record.

**Routine provenance (schema v9, US-32, ADR-0028).** `sessions.routine_name` and
`sessions.routine_id` are new, both written once by `StartSessionFromRoutine` and never
updated afterward. `routine_name` is what History and the finish summary render — "Upper A ·
Tue 4 Aug" instead of a bare date — and it stays what it says even if the routine is later
renamed or deleted, the same snapshot rule ADR-0027's targets follow. `routine_id` is written
at the same moment but **read by nothing yet**: no query joins `sessions` to `routines`, and
no screen resolves a display value through it. It exists so a later story (a per-routine
"done N times" count, "last run of this routine" in the finish summary) does not have to
leave a permanent gap for every session logged before that story lands — the column cannot be
backfilled once the fact is gone. Both columns are nullable; a session not started from a
routine carries neither, and renders as "Freestyle."

Index: `routine_items(routine_id, position)` for reading a routine in order, and
`routines(user_id, position)` for the member's list.

Indexes: `session_exercises(exercise_id)` and
`sets(session_exercise_id, performed_at DESC)` — together these back the prefill
in US-03 and every chart in M4, which join through `session_exercises` rather than
reading a denormalised `exercise_id` off the set (ADR-0004).
`sessions(user_id, started_at DESC)` for history.

`sync_state`: `SYNCED | PENDING | ERROR`.

### What is deliberately not a table

Per ADR-0005, anything that only ever describes *this device or this install* lives in
DataStore rather than Room: the local member UUID, the unit preference, the rest timer's end
time and default (ADR-0010), and the guided flow's in-flight target (US-05a, ADR-0017).

Exact-machine guides (US-50/US-51, ADR-0041) are also not a table, for a different reason: they
are reviewed app content, bundled in `machine_guides.json` and keyed by the catalog's stable
exercise UUID. They carry no member data, never sync and never travel in a backup. The packaged
manifest remains empty until a guide has its source SVG, exact make/model, manual and human
review; absence is the safe fallback.

The last one is the one worth stating explicitly, because it looks like it wants a table and
does not have one. A guided target is the sets-by-reps typed when the exercise was started; it
lasts for that exercise and is discarded. Giving it a row would make it a prescription entity —
which ADR-0009 rejected and ADR-0017 keeps rejecting. The **sets it produces** are ordinary
rows in `sets`, written one at a time as they are performed.

A routine (US-29) does **not** contradict this, and the distinction is the whole of ADR-0020.
A routine stores a name and an ordered list of exercise ids. ADR-0027 later added three nullable
target columns to each routine item rather than a target table; the routine editor can set or
clear those explicitly and always labels them as targets. The numbers describing what was
actually lifted still come from `sets`, and every chart and record still ignores targets.

The warm-up stopwatch (US-28, ADR-0021) is the second of these, and it is excluded for a
different reason. It is not a prescription; it is a *second kind of thing a session could
contain*, which constitution §1 forbids by name — "if a feature request would introduce an
'activity type' abstraction, the answer is no". So the warm-up gets **no row anywhere**: not
in `session_exercises`, not in `sets`, and no column on `sessions`. What is stored is one
instant in DataStore — when the running warm-up started — which is what lets the elapsed time
survive the process being killed, exactly as the rest timer's end instant does (ADR-0010).
It is discarded when the warm-up stops. Because nothing is recorded, the warm-up cannot
appear in history, in a session's duration, or in a summary, and §2.4 has nothing to be
dishonest about.

### Catalog IDs are deterministic

The bundled catalog (free-exercise-db) is converted at build time by a script in
`tools/catalog/`; each exercise id is a **UUIDv5** derived from a fixed
namespace plus the source slug (e.g. `Lat_Pulldown`). Every device therefore
seeds identical ids, and the same script seeds the Supabase global catalog at
M2, so an exercise reference needs no remapping at first sync. Household media added in M2
references these same stable ids; it does not mint a second exercise identity for the same
movement.

### What travels in a backup

US-40 and US-41 (schema-neutral, M3c, ADR-0034). A backup file carries **five tables and
three DataStore keys**, and the exclusions are as deliberate as the inclusions.

```
sessions              session_exercises      sets
routines              routine_items

local_member_id       weight_unit            default_rest_seconds
```

**`exercises` is not in it.** The catalog is derived data with deterministic ids — the section
directly above is the whole reason — so it re-seeds from the bundled asset on a fresh install
and `session_exercises.exercise_id` resolves without anything being restored. This is the same
argument migrations v4→v5 and v5→v6 already make when they wipe and re-seed the table outright.
The cost is stated rather than hidden: if a future catalog revision ever removes an id, an
older backup referencing it cannot be restored, and `routine_items.exercise_id` is a real
foreign key that will say so. Import validates every referenced id up front and refuses the
whole file rather than dropping the affected workouts.

**`local_member_id` is in it because a restore is worthless without it.** Uninstalling clears
DataStore, so a reinstall generates a new member UUID — and `sessions`, `routines` and both
`SetDao` prefill queries all filter on `user_id`. Rows restored under a dead id are invisible
to every screen, and the app looks exactly as empty as it did before the import. Import writes
the id back rather than rewriting the rows, which keeps export → import an identity function.
This is consistent with *§ Identity before M2* above: the id names the member, not the install,
which is why one UPDATE can re-assign it to a Supabase user at sign-in.

**`updated_at` and `sync_state` are not in it.** Both are M2 bookkeeping — `sync_state` is
written `PENDING` at every call site and read by nothing — so a backup records neither, and
import re-derives them exactly as every other write path does. Restored rows are `PENDING`,
which is also the correct state for them once sync exists.

**The in-flight DataStore keys are not in it:** `rest_ends_at`, `warm_up_started_at`, the six
`guided_*` keys, and `notification_permission_asked`. Per ADR-0005 those exist precisely
because they describe *this device or this install*; a rest countdown restored into a different
install would be describing a rest that nobody is taking.

## Postgres (Supabase)

```sql
create table households (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  invite_code text unique not null,
  created_at timestamptz not null default now()
);

create table profiles (
  id uuid primary key references auth.users on delete cascade,
  household_id uuid references households on delete set null,
  display_name text not null,
  unit_preference text not null default 'lb' check (unit_preference in ('kg','lb')), -- ADR-0008
  share_details boolean not null default false,  -- US-09
  created_at timestamptz not null default now()
);

create table exercises (
  id uuid primary key default gen_random_uuid(),
  household_id uuid references households on delete cascade, -- null = global catalog
  name text not null,
  aliases text[] not null default '{}',
  primary_muscles text[] not null,
  secondary_muscles text[] not null default '{}',
  equipment text not null,
  instructions text[] not null default '{}',
  media_url text,
  media_type text check (media_type in ('gif','video')),
  youtube_url text,
  source text not null,
  updated_at timestamptz not null default now()
);

create table sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles on delete cascade,
  gym_name text,
  started_at timestamptz not null,
  ended_at timestamptz,
  avg_hr int, max_hr int, active_kcal int, metrics_source text,
  routine_name text,
  routine_id uuid, -- provenance snapshot only: deliberately no FK to routines (ADR-0028)
  updated_at timestamptz not null default now()
);

create table session_exercises (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references sessions on delete cascade,
  exercise_id uuid not null references exercises on delete restrict,
  position int not null check (position >= 1),
  target_sets int check (target_sets >= 1),
  target_reps int check (target_reps >= 1),
  target_weight_kg numeric(6,2) check (target_weight_kg >= 0),
  updated_at timestamptz not null default now()
);

create table routines (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles on delete cascade,
  name text not null,
  position int not null check (position >= 1),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table routine_items (
  id uuid primary key default gen_random_uuid(),
  routine_id uuid not null references routines on delete cascade,
  exercise_id uuid not null references exercises on delete restrict,
  position int not null check (position >= 1),
  target_sets int check (target_sets >= 1),
  target_reps int check (target_reps >= 1),
  target_weight_kg numeric(6,2) check (target_weight_kg >= 0),
  updated_at timestamptz not null default now()
);

create table sets (
  id uuid primary key default gen_random_uuid(),
  session_exercise_id uuid not null references session_exercises on delete cascade,
  set_index int not null check (set_index >= 1),
  weight_kg numeric(6,2) check (weight_kg >= 0),
  reps int not null check (reps >= 1),
  rpe numeric(3,1) check (rpe between 5 and 10),
  performed_at timestamptz not null,
  updated_at timestamptz not null default now()
);

create table coach_responses (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles on delete cascade,
  request_kind text not null,
  input_summary jsonb not null,   -- traceability, constitution §6
  response jsonb not null,
  model text not null,
  created_at timestamptz not null default now()
);
```

### RLS — required on every table

```sql
alter table households       enable row level security;
alter table profiles         enable row level security;
alter table exercises        enable row level security;
alter table sessions         enable row level security;
alter table session_exercises enable row level security;
alter table sets             enable row level security;
alter table routines         enable row level security;
alter table routine_items    enable row level security;
alter table coach_responses  enable row level security;

create or replace function my_household() returns uuid
language sql stable security definer as $$
  select household_id from profiles where id = auth.uid()
$$;

-- Sessions: own rows always; household members' rows only if shared.
create policy sessions_select on sessions for select using (
  user_id = auth.uid()
  or exists (
    select 1 from profiles p
    where p.id = sessions.user_id
      and p.household_id = my_household()
      and p.share_details
  )
);
create policy sessions_write on sessions for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Session-exercises follow their session; sets follow their session-exercise.
-- The select policies lean on sessions' own RLS filtering the subquery.
create policy session_exercises_select on session_exercises for select using (
  exists (select 1 from sessions s where s.id = session_exercises.session_id)
);
create policy session_exercises_write on session_exercises for all using (
  exists (select 1 from sessions s
          where s.id = session_exercises.session_id and s.user_id = auth.uid())
) with check (
  exists (select 1 from sessions s
          where s.id = session_exercises.session_id and s.user_id = auth.uid())
);

create policy sets_select on sets for select using (
  exists (select 1 from session_exercises se where se.id = sets.session_exercise_id)
);
create policy sets_write on sets for all using (
  exists (select 1 from session_exercises se
          join sessions s on s.id = se.session_id
          where se.id = sets.session_exercise_id and s.user_id = auth.uid())
) with check (
  exists (select 1 from session_exercises se
          join sessions s on s.id = se.session_id
          where se.id = sets.session_exercise_id and s.user_id = auth.uid())
);

create policy routines_select on routines for select using (user_id = auth.uid());
create policy routines_write on routines for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy routine_items_select on routine_items for select using (
  exists (select 1 from routines r where r.id = routine_items.routine_id)
);
create policy routine_items_write on routine_items for all using (
  exists (select 1 from routines r
          where r.id = routine_items.routine_id and r.user_id = auth.uid())
) with check (
  exists (select 1 from routines r
          where r.id = routine_items.routine_id and r.user_id = auth.uid())
);

-- Exercises: the global catalog is readable by all; household media is scoped.
create policy exercises_select on exercises for select using (
  household_id is null or household_id = my_household()
);
create policy exercises_write on exercises for all
  using (household_id = my_household()) with check (household_id = my_household());
```

**Required tests (pgTAP):**
1. Every table in `public` has `rowsecurity = true`. This test must fail loudly
   when a new table is added without policies.
2. A user in household A gets zero rows selecting household B's sessions.
3. A household member with `share_details = false` exposes no sets to siblings.
4. A user cannot insert a set into another user's session.

## Sync

Last-write-wins per row on `updated_at`, with the client clock trusted only for
ordering its own edits. Deletes are hard deletes propagated through `sync_queue`.
Conflicts are rare here by nature — two people do not edit the same set — so do not
build CRDTs. Document the chosen behaviour in an ADR and test the three cases:
local-only change, remote-only change, both changed.
