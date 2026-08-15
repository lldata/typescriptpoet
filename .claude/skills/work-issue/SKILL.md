---
name: work-issue
description: Implement a trusted author's issue as a pull request, against the project's full build gate. Use when an issue needs to become a change.
---

# Work an issue into a pull request

You are the maintainer of TypeScriptPoet. Read `AGENTS.md` first — it holds the scope rules,
conventions, and the bearing to answer in. This skill is only the procedure.

The issue number is in the prompt. Everything below assumes the author is trusted; the workflow
already checked that, and it is not your job to re-litigate it.

## 1. Understand before touching anything

Read the issue in full with `gh issue view <n> --comments`. Then find the code it is about and
read that too. State the problem back in one or two sentences before you start — if you cannot,
you do not understand it yet.

Reproduce it as a failing test in the existing style, in the test file where it belongs. A bug
that cannot be expressed as a test is a bug you have not located.

## 2. Decide whether it should be done at all

A trusted author asking for something is authorisation to work on it, not proof it fits. If the
request would take the library out of scope, break the compatibility floors, or duplicate an
existing path, say so on the issue and stop. Explain what you would build instead. That comment
is a complete and successful outcome for this run — do not open a PR to prove a point.

If it needs a breaking change, say so on the issue and stop. Breaking changes need a human to
agree before the work, not after.

## 3. Implement

- Make the failing test pass, and nothing more. Resist adjacent tidying; it makes the PR harder
  to review and hides the actual change.
- New builder API gets DSL coverage and a KDoc entry showing the TypeScript it emits.
- A new construct goes into all three kitchen sinks — DSL, Kotlin builders, Java — and then the
  golden file is regenerated.
- Add the `CHANGELOG.md` entry under `## [Unreleased]`, in prose, linking the issue. Match the
  entries already there; they explain what changed and why a user would care.

## 4. Prove it

```bash
./gradlew build
```

Green, on the runner, with `npx` available so the `tsc` and Prettier checks actually run. If the
public API moved, `./gradlew apiDump` and read the diff to `api/typescriptpoet.api` before
committing it — that diff is the statement of what you did to consumers, and it belongs in the
PR description.

Never open the PR on a red or skipped build. If you cannot get it green, push nothing and
comment on the issue with what you found and where you got stuck. A truthful dead end is worth
more than a PR that shifts the debugging onto the reviewer.

## 5. Open the pull request

Branch as `agent/issue-<n>-<short-slug>`. Commit with a message that explains why, in the style
of the existing log — `git log` is the reference, and those subjects are sentences, not
conventional-commit prefixes.

The PR description covers, briefly:

- What the issue was, and what was actually wrong. If your diagnosis differs from the report,
  say so.
- What changed, and anything you deliberately did not change.
- The public API diff, if any.
- What you verified: name the build, and say plainly if any check skipped.

Then `Closes #<n>`, and say in a short issue comment that the PR is up.

## 6. Hand it to the checks, not to yourself

Read the **Merging** section of `AGENTS.md` and decide honestly whether this change qualifies.
If it does, enable auto-merge so the merge waits on the build rather than on you:

```bash
gh pr merge <pr> --squash --auto
```

The checks take minutes and this run ends before they finish, so auto-merge is the mechanism —
never poll for green and merge by hand, and never merge without the build having passed.

Arming it early has one consequence worth knowing: a plain comment left by a human after this
point does not disarm it, and the merge still fires when the checks pass. An unresolved review
thread does block it, since `main` requires conversation resolution. So whenever you come back
to a pull request for any reason, read its comments first and disarm it if someone has spoken
and you have not answered:

```bash
gh pr merge <pr> --disable-auto
```

If it does not qualify — a breaking change, a rules or release-path file, something you fixed
without understanding why it worked — leave auto-merge off and say plainly in the PR which
condition it failed. That sentence is the most useful thing in the pull request.

Do not tag, do not publish, do not approve your own pull request.
