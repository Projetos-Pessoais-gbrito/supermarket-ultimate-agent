# Contributing

## Branching model (git-flow)

| Branch          | Purpose                                               | Branches from | Merges into        |
|-----------------|-------------------------------------------------------|---------------|--------------------|
| `main`          | Production-ready code. Every merge is a tagged release.| —             | —                  |
| `develop`       | Integration branch for the next release.              | `main`        | `release/*`        |
| `feature/*`     | One feature / task from the roadmap.                  | `develop`     | `develop` (PR)     |
| `release/x.y.z` | Stabilization before a release.                       | `develop`     | `main` + `develop` |
| `hotfix/x.y.z`  | Urgent fix on production.                             | `main`        | `main` + `develop` |

Rules:

- Never commit directly to `main` or `develop`; open a Pull Request.
- Merge feature PRs with a merge commit (`--no-ff`) so the feature history stays visible.
- Tag releases on `main` using SemVer: `v0.1.0`, `v0.2.0`, ...
- Branch names: `feature/<short-kebab-name>`, e.g. `feature/sp-nfce-parser`.

## Commits (Conventional Commits)

Keep commits **small and focused** — one logical change per commit.

```
<type>(<optional scope>): <imperative summary>
```

Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `build`, `ci`, `perf`, `style`.
Scopes: `backend`, `mobile`, `infra`, `docs`.

Examples:

```
feat(backend): parse NFC-e access key from QR code URL
test(backend): cover access key check digit validation
docs: add ADR for database choice
```

## Pull Requests

- Target `develop`.
- Describe *what* and *why*, and link the roadmap task.
- CI must be green (build + tests + lint).
