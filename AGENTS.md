# Agent Rules for ShopFlow

These rules are mandatory for any AI agent working in this repository.

## Rule priority

Project-specific rules in this file override generic workspace guidance. If a
direct user request conflicts with repository safety rules, stop and ask unless
the repository owner explicitly approves the exact action in the current
conversation.

## Core principles

### 1. Think Before Coding

Do not assume. Do not hide confusion. Surface tradeoffs early.

- State assumptions explicitly when they affect implementation.
- If the request has multiple plausible meanings, name them.
- If a simpler useful solution exists, say so.
- If something important is unclear, ask a targeted question before editing.
- Do not silently choose a risky interpretation and continue.

### 2. Simplicity First

Write the minimum code that solves the current problem.

- No features beyond the request.
- No abstractions for single-use code.
- No configurability, caching, retries, queues, notifications, or monitoring
  unless required or already established by local patterns.
- No error handling for scenarios that cannot occur in this code path.
- If the solution is much larger than the problem, simplify it.

Rule of thumb: prefer the smallest correct solution; add abstractions only
after real reuse or complexity exists.

### 3. Surgical Changes

Touch only what is needed. Clean up only your own mess.

- Do not "improve" adjacent code, comments, formatting, or APIs unless needed.
- Do not refactor unrelated code.
- Match existing style, even if you would choose another style in new code.
- Preserve code and comments you do not understand.
- If you notice unrelated dead code, mention it instead of deleting it.

Remove imports, variables, functions, or files made unused by your own change.
Do not remove pre-existing unused code unless asked.

Every changed line should trace directly to the user request or to verification
needed for that request.

### 4. Goal-Driven Execution

Define success criteria and loop until verified.

Convert vague tasks into testable goals:

| Request | Better working goal |
| --- | --- |
| "Add validation" | Add focused invalid-input checks, then make them pass. |
| "Fix the bug" | Reproduce the bug, then fix the reproducer. |
| "Refactor X" | Prove behavior before and after with relevant tests. |
| "Make it faster" | Define the target metric: latency, throughput, perceived speed, or resource use. |

For multi-step work, state a short plan:

```text
1. Inspect current behavior -> verify: code path and existing tests are known.
2. Add or update focused coverage -> verify: it fails or checks the right behavior.
3. Implement the smallest fix -> verify: focused check passes.
4. Check regressions -> verify: relevant suite or command passes.
```

Weak criteria like "make it work" are not enough.

## Repository workflow before changing code

1. Read `CONTRIBUTING.md` completely.
2. Read AND analyze recent Git history to extract the conventions actually in use:
   - run `git log -n 10 --format="%H%n%an <%ae>%n%s%n%n%b"` to see full
     subjects and bodies, not just one-line subjects;
   - inspect relevant commits with `git show` when touching related areas;
   - extract the recurring pattern from these commits: subject `type(scope)`
     style, body presence/length, Jira footer format, Smart Commit usage,
     and `(#<PR>)` suffix conventions;
   - state the observed pattern back before drafting your own commit, and
     match it. Do not assume the convention from memory — derive it from the
     last 10 commits each time.
3. Check current branch and dirty workspace:
   - `git status --short --branch`
4. Check whether `develop` moved before doing work:
   - run `git fetch origin`;
   - compare with `git log --oneline --left-right HEAD...origin/develop`;
   - if `origin/develop` has new commits not in the current branch, stop and tell
     the repository owner before editing. Offer clear options such as stash local
     work, rebase onto `origin/develop`, merge `origin/develop`, or continue
     intentionally without integrating.

## Pull Request rules

Before creating a Pull Request:

1. Read recent PR history:
   - run `gh pr list --state all --limit 10`;
   - inspect 3-5 recent or relevant PRs with
     `gh pr view <number> --comments --review`.
2. Analyze those PRs to extract the recurring pattern, then state it back
   before drafting your own: PR title format (`[SF-XX]` vs Conventional
   Commits), description section layout, test notes, review handling, squash
   subject/body format, and branch cleanup. Derive the convention from the
   inspected PRs each time — do not assume it from memory.
3. Confirm the PR targets `develop`, except release PRs from `develop` to
   `main`.
4. Include the Jira key in the PR title and description.
5. Include verification commands and results in the PR description.
6. Do not create, update, merge, or close a PR unless the repository owner
   explicitly approves that exact action.

## Commit rules

Never create, amend, squash, rebase, push, or otherwise rewrite commits unless
the repository owner explicitly approves that exact Git action in the current
conversation.

Before requesting approval for any commit, show:

- files to be staged;
- `git diff --cached --stat`;
- exact commit message;
- verification commands and results.

Commit messages must follow `CONTRIBUTING.md`:

- Conventional Commit subject: `<type>(<scope>): <subject>`;
- English subject, lowercase, no trailing period, max 72 characters;
- Jira issue footer on its own line, for example `SF-25 #in-progress`.

## Branch rules

- Do not push directly to `main` or `develop`.
- Work must stay on `feature/*`, `bugfix/*`, `docs/*`, or `chore/*` branches.
- Merge to `develop` via Pull Request after CI passes and review is approved.

## Completion checklist

Before reporting work as complete, confirm:

- the change solves the stated request, not a broader imagined request;
- important assumptions and remaining risks are clear;
- the diff is small and local;
- no unrelated formatting, comments, or APIs changed;
- unused code created by the change was removed;
- relevant verification ran, or the reason it could not run is stated;
- no commit, push, merge, rebase, or squash was performed without explicit
  approval.
