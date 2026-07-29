# ADR-0007: Starter exercises, and bundled images for them only

- **Status:** accepted
- **Date:** 2026-07-26
- **Deciders:** maintainer (requested), agent (scoped)

## Context

Using the search screen against the real catalog exposed two problems the specs did
not anticipate.

**The empty-history case is unusable.** US-02 orders results "recently used first,
before alphabetical results". A member who has never logged anything has no history,
so they get 873 exercises alphabetically, beginning with "3/4 Sit-Up", "90/90
Hamstring", "Ab Crunch Machine". `roadmap.md` calls M1 "the milestone that decides
whether the app is good", and the first thing a new household member sees is a wall
of names. The recency rule is right; it just has nothing to work with on day one.

**Names alone are hard to choose from.** The maintainer's words: seeing the machine
would make it much easier to pick. That is especially true for the Partner and Teen
personas in `user-stories.md`, who will not know "Wide-Grip Lat Pulldown" by name.

Media is scheduled for M3, and `kickoff.md` is explicit about how: mirror into our own
storage, "Do not hotlink a free API endpoint — those carry rate limits and no uptime
guarantee". There is no Supabase project until M2, so the M3 mechanism does not exist
yet.

Measured, rather than assumed:

- free-exercise-db is **The Unlicense** (public domain), so bundling is permitted.
- It ships 1746 images across 873 exercises, ~73 KB each: **~121 MB**. Bundling the
  whole set into the APK is not an option.
- One image each for ~35 exercises is **~2.5 MB**, which is.

## Options considered

1. **Hotlink `raw.githubusercontent.com`.** Free and complete. Rejected outright:
   `kickoff.md` forbids it by name, and it would break the offline guarantee in
   constitution §2 — the gym has no signal.
2. **Wait for M3.** Correct by the roadmap, and leaves the screen hard to use for the
   whole of M1, including the exit gate where the maintainer logs three real workouts.
3. **Bundle images for a curated starter set; leave the rest for M3.** Costs ~2.5 MB
   of APK, works offline on first launch, needs no backend, and covers exactly the
   exercises a beginner is choosing between. Chosen.
4. **Bundle downscaled images for all 873.** Even at 15 KB each that is ~13 MB of APK
   for images of exercises nobody in this household does. Rejected.

## Decision

Add an `isStarter` flag to the bundled catalog, set for a curated list of common gym
movements, and bundle one image for each of those.

Search ordering becomes: **recently used → starter → alphabetical**. Recency still
wins, so the list personalises itself as soon as there is history and the starter set
fades out of the way on its own. No setting, no onboarding step.

Exercises outside the starter set show no image rather than a placeholder that
pretends to be one (constitution §2: if a metric is unavailable, show it as
unavailable). Their media arrives at M3 through the mirroring `kickoff.md` describes.

## Consequences

- This pulls a slice of M3 into M1, against `roadmap.md`'s sequencing. It is deliberate
  and requested, and it is bounded: no Coil GIF pipeline, no Storage bucket, no
  household uploads. Those remain M3.
- APK grows ~2.5 MB. Acceptable for a household app installed by hand.
- The starter list is a judgement call about what is "common", and it is data in
  `:tools:catalog`, not logic — a test asserts every starter slug resolves to a real
  exercise, so the list cannot rot silently when the catalog is refreshed.
- A member whose gym lacks a starter machine is not blocked: the full catalog is still
  one search away, and their own history takes over the top of the list immediately.
- **Revisit at M3**, when Storage-mirrored media covers the whole catalog. The
  `isStarter` flag stays useful for empty-history ordering even then.
