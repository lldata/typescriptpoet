---
name: release-readiness
description: Assess whether a release is due, reporting to the run summary and opening an issue only when one actually is. Use on the weekly schedule or when asked whether to cut a release.
---

# Release readiness

Read `AGENTS.md` for the version policy before you form an opinion. The short version: a human
tags, you advise. 2.0.0 has not shipped, so until it does nothing here proposes a tag.

## Gather

- `git describe --tags --abbrev=0` for the latest tag, then `git log <tag>..main --oneline` for
  what has landed since.
- The `## [Unreleased]` section of `CHANGELOG.md`.
- `gh issue list --state open` and `gh pr list --state open`.

## Judge

Answer three questions, in this order:

**Is the changelog honest?** Every user-visible commit since the tag should have an entry. List
any that do not, by commit. This is the most common reason a release is not ready, and it is the
one thing here that is a fact rather than a judgment.

**Is there anything half-finished?** A construct added to the builders but not the DSL, an API
addition without a `MIGRATING.md` note, a `TODO` left in a shipped path. Name it.

**What would the release be?** Per the version policy: after 2.0.0 ships, routine merges earn a
patch. A minor or major happens only because a trusted author asked for one in an issue — never
because the work looks big to you. If what has landed feels larger than a patch, say that in as
many words and leave the decision alone.

## Report

**When the answer is hold, do not open an issue.** Write the report to the workflow run summary
and stop:

```bash
cat >> "$GITHUB_STEP_SUMMARY" <<'EOF'
… the report …
EOF
```

A standing issue that says "hold" every week is noise, and noise is what makes a real signal
easy to miss. Hold is the answer most weeks, so most weeks leave nothing behind but a run
summary. That is the intended outcome, not a run that failed to find something to say.

**When a release is genuinely due**, open an issue titled **Release readiness** — that is the
signal, and it should be rare enough to be worth a notification.

Before opening one, check whether it already exists, matching the title exactly. A text search
also matches issues that merely contain the words, so do not trust search relevance to rank the
real one first:

```bash
gh issue list --search "Release readiness in:title" --state open --json number,title \
  --jq '.[] | select(.title == "Release readiness") | .number'
```

If one is open, edit its body rather than opening a second, and comment only when the
recommendation itself changes.

The body holds: the latest tag and commit count since, what has landed, what is missing, and the
exact tag a human would push. Use the actual next tag in sequence rather than copying an example
— check `git tag --list 'v2.0.0-alpha*' | sort -V | tail -1` and the version policy for what
comes after it:

```bash
git tag v2.0.0-alphaN && git push origin v2.0.0-alphaN
```

If nothing has landed since the last tag, say so in one line in the run summary and stop. Do not
manufacture work.
