# ADR-0044: A legal spacing vocabulary, one inset consumer, and the primary action returns to 64dp

- **Status:** accepted
- **Date:** 2026-08-29
- **Deciders:** maintainer, agent
- **Relates to:** ADR-0011 and its Turn 3/Turn 4 amendments, ADR-0016 (`GymDimens` itself),
  ADR-0029, ADR-0030, `specs/roadmap.md` §"What is left from the `Redesign.dc.html` audit",
  `GymDimensTest`

## Context

`Redesign.dc.html` synced a fifth turn (2026-08-29), exported as a `handoff/` bundle of seven
gated files rather than one prose document. File `01-insets-and-spacing.md` is the foundation
the other six read against — legal dp values are that starting point.

This amendment reverses a value ADR-0011's Turn 4 amendment deliberately set, and the reversal
needs recording rather than silently overwriting a decision that has its own pinned test.

**`PrimaryAction`: 72dp back to 64dp.** Turn 4 raised it from 64dp to 72dp for the "sweaty hands,
phone at arm's length" reasoning, and pinned it in `GymDimensTest` specifically because
`>= MinTouchTarget` alone had let a 64dp/72dp mismatch through unnoticed once already. Turn 5's
legal row-height set is `{44, 56, 64, 80}` — 72 is not on it, for every primary action in the
app, not just the log button. Two things changed the calculus since Turn 4:

1. Turn 4 itself had already split the primary action into two floors — `PrimaryAction` (72dp,
   the single-string overload) and `LogRowHeight` (64dp, the two-line log button) — because the
   log button's fixed two-line content no longer needed the extra room. That 72-vs-64 split was
   already an exception to "one primary action size," not the rule.
2. Turn 5's legal-values table makes the exception the rule: every primary action in the app —
   the log button, the warm-up screen's `DONE`, the add-exercise sheet's primary, `Finish
   workout` — reads the same 64dp floor. Keeping `PrimaryAction` at 72dp would mean the single
   busiest button in the app (the log button, `LogRowHeight`, already 64dp) is *shorter* than
   every less-frequent one, which is backwards from Turn 4's own "sweaty hands" reasoning applied
   to the row that actually gets tapped most.

**`LogRowHeight` is retired, not kept alongside `PrimaryAction` at the same value.** Its entire
reason to exist — per its own doc comment — was staying below `PrimaryAction` while the
single-string overload held the taller floor. Once both are 64dp, keeping two names for one
number is exactly what this file's own class doc (`GymDimens.kt`) warns against: a second name
invites a future drift back to two different values instead of naming the one that's now shared.

**Everything else in `GymDimens` stays.** File `01`'s literal instruction — "delete anything not
on [the eleven-token] list" — is not followed as written. Tokens like `PhotoHeight`,
`ChartHeight`, `Thumbnail`, `MascotHome`/`MascotInline`, `CompactScreenPadding`, and others are
load-bearing in `feature/catalog`, `feature/progress`, and `feature/routines` — none of which
this turn's frames (`5a`–`5h`) touch — and Turn 4's own amendment already established the
precedent this repo uses instead: a new constraint gets a new, additive token or rule, not a
silent repoint or deletion of one an untouched screen still reads. Read literally, file `01`
would delete working, already-audited sizing with nothing named to replace it — exactly the
condition `00-gate.md` itself says to stop and ask about ("if the spec doesn't contain a value
you need, stop and ask. Do not choose."). Confirmed with the maintainer: the eleven-token table
names the **legal vocabulary for new spacing and row-height literals**, enforced by the lint
rule below; it is not an inventory of every token `GymDimens` is allowed to hold.

**Insets:** `GymTrackerNavHost`'s root `Scaffold` is the only consumer of `WindowInsets
.statusBars` found in the codebase (grepped across `feature/**` and `app/src/main`); no screen
applies its own `statusBarsPadding()` or a fixed top `Spacer` in that range. `LiveHeartRateChip`
is the one deliberate exception — it lives in the `topBar` slot specifically so a live reading
floats in the status-bar area on every screen, per its own doc comment (US-47) — and is absent
entirely (zero height) when no reading exists. Whether Scaffold's own default
`contentWindowInsets` still double-counts the status bar against that empty-but-inset-padded
`topBar` slot is not something a source grep can settle; it needs the instrumented measurement
this ADR's user story requires before any layout code changes, per file `01`'s own step 0
("reproduce first ... if it is not, the diagnosis is wrong and you should say so").

## Options considered

1. **Keep `PrimaryAction` at 72dp, treat file `01`'s row-height table as covering only the log
   button.** Rejected: the maintainer's explicit direction was to adopt 64dp, and a table that
   claims to be the *legal* set for row heights app-wide while one button is quietly exempted
   is worse documentation than either following it or amending it — 64 chosen, not exempted.
2. **Adopt 64dp and delete every `GymDimens` token file `01` doesn't name — chosen for
   `PrimaryAction`/`LogRowHeight`, rejected for the rest.** The maintainer's direction here was
   explicit too: scope the lint rule, keep tokens outside this turn's frames.
3. **Leave `LogRowHeight` as a token equal to `PrimaryAction`.** Rejected: a second name for one
   number has no reader it serves once the two floors are identical, and this file's own class
   doc treats a private literal duplicating a token as the exact failure mode `GymDimens` exists
   to prevent — a second *token* duplicating another is the same failure one level up.

## Decision

`GymDimens.PrimaryAction` becomes 64dp; `GymDimens.LogRowHeight` is removed and its one call
site (`PrimaryActionButton`'s two-line overload) reads `PrimaryAction` directly. Every other
`GymDimens` token is unchanged. A new lint check enforces `{2, 4, 12, 20, 32, 44, 56, 64, 80}`
as the legal set for `.dp` literals written directly in `feature/**` — scoped to the two
categories file `01` itself names, **vertical spacing and row/element height** (a `Spacer`
height, a vertical `Arrangement.spacedBy`, a `.height(...)` modifier, vertical padding), not
every `.dp` literal in the module. Confirmed with the maintainer: the gate table's own assertion
1.5 ("count of `.dp` literals in `feature/**` outside the legal set = 0") reads more broadly than
the file's prose, and applying it to icon sizes, stroke widths, and horizontal-only padding would
require inventing tokens or allowlist entries for dozens of unrelated call sites — well past this
file's own stated scope ("touches no feature logic"). Named `GymDimens` tokens are exempt by
construction regardless of category. `WindowInsets.statusBars` consumption stays exactly where
it already is — the root `Scaffold` — pending the instrumented measurement; if that measurement
shows a real gap, the fix is scoped separately rather than assumed here.

## Consequences

**Easier:** one row-height floor for every primary action removes a distinction (`PrimaryAction`
vs `LogRowHeight`) that existed for one turn and was already a wrinkle in "one primary action per
screen." New feature code gets a build-time check against inventing spacing outside the legal
set, the same enforcement `NoTextWithoutMaxLinesTest` already gives `maxLines`.

**Harder:** the three secondary square buttons that read `GymDimens.PrimaryAction` as a
`minHeight`/`minWidth` floor (`GuidedExerciseScreen`'s `Stop`, `SessionMovements`' `Add set`,
`RestPanel`'s `Adjust`) shrink from 72dp to 64dp alongside the primary — all three stay above
`MinTouchTarget` (48dp), so nothing crosses the accessibility floor, but this is a visible size
change beyond the log button alone and should be checked on-device, not just in a pinned test.

**Committed to:** `GymDimensTest`'s pinned-value test now asserts 64dp, and a second pinned test
(`LogRowHeight` no longer exists) makes a future re-introduction of a shorter-than-`PrimaryAction`
log floor a visible diff in this file, not a silent one.

**Revisit if:** a later turn wants the log button taller than other primary actions again — Turn
4 already tried exactly that split once, so the reasoning above should be re-read before repeating
it, not re-derived from scratch.
