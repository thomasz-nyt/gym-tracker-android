# ADR-0014: The catalog has no GIFs, and M3 ships no media pipeline

- **Status:** accepted
- **Date:** 2026-08-01
- **Deciders:** maintainer (chose), agent (measured)

## Context

M3 was specified as "Exercise catalog **and media**", with two roadmap lines describing how
the media half would work:

> GIF playback via Coil; mirrored into Supabase Storage, never hotlinked

and `tech-stack.md`:

> Images / GIF | Coil 3 with `coil-gif` | **GIF is the primary exercise-demo format**

Both are wrong about the seed data, and had been since M0. Measured against the bundled
catalog and its source:

- **free-exercise-db ships no GIFs at all.** It publishes **two static JPGs** per exercise —
  a start position and an end position. 1746 images across 873 exercises, ~69 KB each.
- The bundled `exercises.json` has **`media_url` set on 0 of 873** exercises and
  `media_type` null on all of them, exactly as ADR-0007 left it.

So "GIF playback" was never a rendering problem. There was nothing to render.

The second assumption also no longer holds. Media was to be mirrored into **Supabase
Storage**, which M2 builds — and the maintainer has chosen to take M3 first, to keep the
offline core moving without an account (2026-08-01). There is no bucket to mirror into.

Options for supplying media without a backend were measured before choosing:

- One downscaled image per exercise, bundled: **~13 MB** of APK on top of the current ~16 MB.
- Both frames for all 873: **~26 MB**.
- Starters only, as ADR-0007 already ships: **36 images, ~2.5 MB, no change**.

## Decision

**M3 ships no media pipeline.** It is renamed from "Exercise catalog and media" to
"Exercise catalog".

- The 36 starter photos ADR-0007 bundled stay exactly as they are.
- The other 837 exercises show **no image** on the detail screen — not a placeholder
  (constitution §2: absent is shown as absent). Their detail screen carries the muscle tags
  and the numbered instruction steps, which **868 of 873 exercises already have**.
- `coil-gif` and Media3/ExoPlayer are **not** added. M3 needs no new dependency; Coil is
  already loading the starter photos.
- Mirrored media and US-15's family-recorded clips both move to **M2**, where the Storage
  bucket and the household they belong to exist.

The maintainer chose the cheapest option deliberately: text instructions carry the catalog,
and the exercises a beginner is actually choosing between are the ones that already have
photos.

## Consequences

- **M3's exit criterion had to change, because the old one was already met.** It read "every
  exercise in the catalog has either a GIF, a clip, or text" — and 868 of 873 ship text
  today, so the gate was satisfied by M1's seed data and tested nothing M3 builds. The new
  criterion is a task, performed in airplane mode, at a machine.
- Five exercises have no instructions *and* no photo: Iron Cross, One-Arm Kettlebell Swings,
  Push Press, Side Bridge, Side Jackknife. Their detail screen must say the catalog records
  no instructions, rather than rendering an empty panel. That is US-13's wording now.
- Nothing in M3 needs the network, which is why it can be taken before M2 at all.
- **Revisit at M2.** The options above are still the options, and the measurements still
  stand. If bundling wins then too, the Storage mirror may never be needed for stock media —
  it would only be needed for household clips, which are the one kind of media that cannot be
  bundled.
