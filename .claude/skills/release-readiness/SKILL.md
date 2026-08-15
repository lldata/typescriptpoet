---
name: release-readiness
description: Assess whether a release is due and keep the standing release-readiness issue current. Use on the weekly schedule or when asked whether to cut a release.
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

Keep exactly one open issue titled **Release readiness**. A text search for those words also
matches unrelated issues whose title happens to contain "release" and "readiness" — filter to an
exact title match rather than trusting search relevance to rank the real one first:

```bash
gh issue list --search "Release readiness in:title" --state open --json number,title \
  --jq '.[] | select(.title == "Release readiness") | .number'
```

Edit that issue's body rather than opening a second one, and add a short dated comment only when
the recommendation itself changes — nobody wants a weekly notification that says the same thing
as last week.

The body holds: the latest tag and commit count since, what has landed, what is missing, and a
one-line recommendation — hold, or ready with the exact tag a human would push. If it is ready,
give the command with the actual next tag in sequence (not a copied example — check
`git tag --list 'v2.0.0-alpha*'` or the version policy for what comes after the latest one):

```bash
git tag v2.0.0-alphaN && git push origin v2.0.0-alphaN
```

If nothing has landed since the last tag, say so in one line and stop. Do not manufacture work.
