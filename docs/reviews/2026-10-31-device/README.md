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
The full regression suite remains required. These images are a checkpoint, not a claim that every agreed review item has
been completed.

- [Large text Focus](focus-large-text.png): 200% text with the selected exercise and Log reachable.
- [Routine name with keyboard](routine-new-name.png): Save and Back stay above the keyboard; the preview scrolls.

The short-screen history test reproduced retained scroll clipping the exercise title before
resetting the page scroll. The keyboard regression was verified against the old layout using
native window bounds: Save at y=1760–1830 was covered by the keyboard beginning at y=1442.
The adjusted dialog passed the same bounds check. These checks inspect native platform windows
because Compose visibility alone did not detect keyboard coverage.
