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
)

data class WorkoutSession(
    val id: SessionId,
    val userId: UserId,
    val gymName: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val metrics: SessionMetrics?, // null unless a health source provided them
)

/** All fields nullable. Absence is a first-class state, never zero. */
data class SessionMetrics(
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val activeKilocalories: Int?,
    val source: String?,          // "health_connect" | "healthkit"
)

/** An exercise as it appears in one session. The same exercise may appear twice. */
data class SessionExercise(
    val id: SessionExerciseId,
    val sessionId: SessionId,
    val exerciseId: ExerciseId,
    val position: Int,            // 1-based order within the session
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
          updated_at)

sessions(id PK, user_id, gym_name, started_at, ended_at,
         avg_hr, max_hr, active_kcal, metrics_source,
         updated_at, sync_state)

session_exercises(id PK, session_id FK→sessions ON DELETE CASCADE,
                  exercise_id FK→exercises, position,
                  updated_at, sync_state)

sets(id PK, session_exercise_id FK→session_exercises ON DELETE CASCADE,
     set_index, weight_kg, reps, rpe, performed_at,
     updated_at, sync_state)

sync_queue(id PK, entity, entity_id, op, payload_json, created_at, attempts)
```

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

The last one is the one worth stating explicitly, because it looks like it wants a table and
does not have one. A guided target is the sets-by-reps typed when the exercise was started; it
lasts for that exercise and is discarded. Giving it a row would make it a prescription entity —
which ADR-0009 rejected and ADR-0017 keeps rejecting. The **sets it produces** are ordinary
rows in `sets`, written one at a time as they are performed.

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
M2, so `sets.exercise_id` needs no remapping at first sync. Household-created
exercises (M3) still use `gen_random_uuid()`.

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
  updated_at timestamptz not null default now()
);

create table session_exercises (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references sessions on delete cascade,
  exercise_id uuid not null references exercises on delete restrict,
  position int not null check (position >= 1),
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
