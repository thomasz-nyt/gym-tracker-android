# Native device review checkpoint

Captured on the connected Pixel 8a, Android 17, using `com.gymtracker.review` and synthetic
training fixtures. These are native Android screenshots. The normal REP installation and its
personal records were not used by the tests.

- [Current-set Focus](focus-current-set.png): selected exercise, current set, load/reps, compact rest and pinned Log.
- [Records on demand](focus-records.png): actual current sets, target and previous-performance state.
- [Explicit routine reuse](routine-reuse.png): after workout persistence, review the changed list before choosing a template operation.
- [Saved workout](workout-saved.png): actual sets and unfinished/unperformed movements remain honest.

Ten focused native checks passed together on the Pixel 8a: four Focus checks, three routine-reuse
checks, numeric-field accessibility and both two-tap logging checks. This includes 200% text,
returning from long history on a short screen and Save as new with the software keyboard open.
The full suite now passes in both configurations, as recorded below. These images remain a
checkpoint: the complete agreed review still has unchecked implementation work.

- [Large text Focus](focus-large-text.png): 200% text with the selected exercise and Log reachable.
- [Routine name with keyboard](routine-new-name.png): Save and Back stay above the keyboard; the preview scrolls.

The short-screen history test reproduced retained scroll clipping the exercise title before
resetting the page scroll. The keyboard regression was verified against the old layout using
native window bounds: Save at y=1760–1830 was covered by the keyboard beginning at y=1442.
The adjusted dialog passed the same bounds check. These checks inspect native platform windows
because Compose visibility alone did not detect keyboard coverage.

- [Warm-up](warm-up.png): explicitly opened from More; Done and Skip return to the same visit.
- [Warm-up with large text](warm-up-large-text.png): 200% text in a constrained 320×600 dp app viewport. The unused white area is outside this test viewport. The full action label remains available to accessibility while the visible label ellipsizes.

Full native regression at this continuation:

| Configuration | Passed | Conditional/viewport skips | Failed |
| --- | ---: | ---: | ---: |
| Default optional-service bindings | 66 | 5 | 0 |
| Optional features off | 68 | 3 | 0 |

Both runs declare 71 cases. Counts above use per-test instrumentation status, separating
assumption skips from passes; the runner's headline test total includes assumption-skipped cases.
The four no-op contract checks execute in the off variant. Conditional skips cover unavailable
service controls and the existing viewport-specific inset check. Native tests now follow Focus,
Records and More, retaining real Room assertions for logging/correction/Undo/rest and adding
shared-plan completion/extra-set and large-text warm-up coverage. Local full gates also pass,
with 1,076 unit tests, zero failures/errors, app lint and domain dependency checks.
