# Running pollsystem locally

Two guides, pick the one that matches how you work:

- **[RUNNING-on-host.md](RUNNING-on-host.md)** — dev on your **host** (JDK + Node
  installed): `BuildAndDeploy.bash local`/`local-docker`, hot reload, tests, DB,
  logs, staging deploy.
- **[RUNNING-on-docker.md](RUNNING-on-docker.md)** — **fully containerized** via
  the VS Code Dev Container (no host JDK/Node): Reopen in Container → `claude -c`
  → run everything from inside.
