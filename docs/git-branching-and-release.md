# Git Branching And Release

## Branches

- `main`: releasable branch. CI must pass before merge.
- `develop`: integration branch for milestone work.
- `codex/<scope>`: AI implementation branches, one milestone slice per branch when practical.
- `hotfix/<scope>`: urgent production fixes from `main`.

## Commit Convention

Use Conventional Commits:

- `feat(scope): summary`
- `fix(scope): summary`
- `test(scope): summary`
- `docs(scope): summary`
- `chore(scope): summary`

Every AI-generated commit should include a validation section in the body. A local template is available in `.gitmessage`.

## Pull Request Gate

- Backend: `mvn test` and package.
- Frontend: lint and production build.
- Database: Flyway versions must be sequential.
- Security: no generated artifacts or secret-like literals in commits.
- Review focus: permission boundaries, audit events, migration reversibility, and cross-client behavior.
