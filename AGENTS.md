# Agent Rules for ShopFlow

These rules are mandatory for any AI agent working in this repository.

## Before changing code

1. Read `CONTRIBUTING.md` completely.
2. Read recent Git history for context:
   - `git log --oneline --decorate -n 20`
   - inspect relevant commits with `git show` when touching related areas.
3. Check current branch and dirty workspace:
   - `git status --short --branch`
4. Check whether `develop` moved before doing work:
   - run `git fetch origin`;
   - compare with `git log --oneline --left-right HEAD...origin/develop`;
   - if `origin/develop` has new commits not in the current branch, stop and tell
     the repository owner before editing. Offer clear options such as stash local
     work, rebase onto `origin/develop`, merge `origin/develop`, or continue
     intentionally without integrating.

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
- Feature work must stay on `feature/*` or `bugfix/*` branches.
- Merge to `develop` via Pull Request after CI passes and review is approved.
