# ADR-0015: Three things the catalog does not know

- **Status:** accepted
- **Date:** 2026-08-01
- **Deciders:** maintainer (chose), agent (measured)

## Context

Two M3 acceptance criteria assume catalog fields that the bundled data does not contain, and
a third field claims knowledge it does not have. Measured against `exercises.json`, 873 rows:

| Field | Coverage | Assumed by |
|---|---|---|
| `aliases` | **0 / 873** | US-12, "search matches on name and common aliases" |
| `youtube_url` | **0 / 873** | US-14, "where a YouTube link exists, tapping it opens…" |
| `equipment = OTHER` | **239 / 873 (27%)** | US-12's equipment filter |

The first two would pass their tests by never doing anything: a search that matches no alias
because there are none, a link-out that never appears because no link exists. Criteria that
cannot fail are not criteria.

The third is different — the data is there, but it is dishonest. `OTHER` is the largest
bucket in the catalog and it holds two unrelated things: exercises whose equipment really is
miscellaneous (exercise ball, medicine ball, foam roll) and exercises where **the source
simply did not record any equipment**. `ExerciseTaxonomy` maps an absent value to `OTHER`
today. Constitution §2 is explicit: "If a metric is unavailable, show it as unavailable."

## Decision

Each gets a different answer, depending on whether the app can honestly supply what is
missing.

**1. Aliases — author them.** A hand-written alias table in `:tools:catalog`, alongside
`STARTER_EXERCISE_SLUGS` and following the same pattern ADR-0007 established: data, not
logic, with a test asserting every alias resolves to a real slug so the list cannot rot when
the catalog is refreshed. Scoped to how people actually search on a gym floor — "pulldown",
"pec deck", "leg press", "hack squat" — not to a synonym dictionary.

This is the one case where the household can supply better data than the source, because the
knowledge is about how *we* talk, not about the exercise.

**2. YouTube — a search, labelled as a search.** No curated links exist and inventing them is
not possible, so US-14 becomes a **YouTube search** built from the exercise name, opened in
the external browser. The UI says it is a search. The app does not imply it has vetted a
video, because it has not — presenting a generated link as a curated one is the same class of
mistake as showing an estimated weight as a logged one (constitution §2).

Still no embedded player, no third-party SDK, and no account required (constitution §3), so
this stays available to the Teen persona. It is the only thing in M3 that needs the network,
and its absence changes nothing else on the screen.

**3. Equipment — add `UNSPECIFIED`.** A new `Equipment` value, distinct from `OTHER`, for
exercises where the source recorded nothing. `OTHER` goes back to meaning what it says:
equipment that exists and is miscellaneous. The filter shows it as "Not specified".

## Consequences

- The equipment change needs a **catalog re-seed**, not a data migration: bump the schema
  version, `DELETE FROM exercises`, and let `CatalogSeeder` re-insert. This is exactly what
  `MIGRATION_4_5` already does, and it is safe for the same reason — catalog ids are UUIDv5
  over the source slug, so they are unchanged and every `session_exercises.exercise_id` still
  resolves. No logged set is touched.
- The alias list is a judgement call about language and will need adding to as the household
  uses the app. That is fine; it is a data file, and a wrong alias is a bad search result
  rather than a bug.
- US-14 promises less than it originally did. That is the point: it now promises something
  true.
- **Revisit if** a catalog source with real aliases or curated video links is adopted. The
  alias table would become a supplement rather than the whole supply.
