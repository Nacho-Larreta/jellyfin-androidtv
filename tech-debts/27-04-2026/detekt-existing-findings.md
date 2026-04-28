# Detekt Findings Ignored By Configuration

## Context

Running `./gradlew-local detekt` succeeds because the root Gradle config has `ignoreFailures = true`, but the report still emits many findings across the Android TV codebase.

The Jellyflix-specific files touched for profile selector/custom libraries were cleaned so they do not appear in the targeted Detekt grep for:

- `UserViewsRepository`
- `JellyflixCollectionType`
- `UserCardView`
- `MainToolbar`

## Debt

The repo has existing broad Detekt debt, mainly:

- Long composable/settings methods.
- High cyclomatic complexity in legacy UI/playback helpers.
- Magic numbers in media/profile capability code.
- Forbidden TODO/FIXME comments in existing modules.
- Large utility files and objects with too many functions.

## Risk

This does not block runtime behavior today, but it reduces the signal/noise ratio of Detekt and makes it harder to enforce quality gates for new Jellyflix changes.

## Suggested Cleanup Path

1. Create a Detekt baseline for the current upstream debt.
2. Turn new-code violations into blocking failures.
3. Refactor only high-churn areas first: toolbar, profile selector, browsing rows, playback helpers.
4. Replace broad suppressions with extracted components/use cases when touching the same areas again.

## Current Decision

Accepted as non-blocking for this feature because fixing the whole repo-wide Detekt surface would be a separate refactor, not part of Android TV profile/library parity.
