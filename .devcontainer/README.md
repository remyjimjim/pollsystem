# Dev Container

A reproducible, containerized dev environment for pollsystem — Ubuntu 24.04 +
JDK 17 + Node 22 + the Docker CLI + Claude Code, with your Claude history and the
project mounted in. VS Code's UI stays on the host; everything *inside* the
window (terminals, extensions, `gradle`/`npm`, `git`, Claude Code) runs in the
container.

## Open it

1. Install the **Dev Containers** extension (`ms-vscode-remote.remote-containers`).
2. Open the repo in VS Code → **Reopen in Container** (Command Palette:
   *Dev Containers: Reopen in Container*). First build pulls the base image +
   Features (a few minutes); later opens are fast.

## What's wired

- **Toolchain:** JDK 17 (Temurin), Node 22, Gradle via the repo wrapper,
  `docker` + `docker compose`, `psql`. Stack extensions auto-install (Java,
  Kotlin, Gradle, Spring Boot, Vue/Volar, ESLint, Prettier, Docker).
- **Claude Code:** installed in the container; run `claude` in a terminal. Your
  global history/auth (`~/.claude` + `~/.claude.json`) and the per-project
  `.claude/` are mounted, so history is continuous with the host.
- **Docker-outside-of-Docker:** the container uses the **host's Docker Desktop**
  (its socket is mounted), and the repo is mounted at the **same absolute path**
  as on the host. So `docker compose` bind-mounts and Testcontainers resolve
  identically whether you run them from the host or from inside — which means
  **`./gradlew test`, `local-docker`, and `BuildAndDeploy.bash` all work from
  inside the container** as well as from the host.

## Running the app

Both work, your choice:

- **From the host** (as before): `./scripts/BuildAndDeploy.bash local` /
  `local-docker` — you're fond of this, and it's unchanged.
- **From inside the container:** the same commands. `local-docker` orchestrates
  the app containers on the host's Docker (open <http://localhost:3000> etc. in
  your host browser); `local` runs bootRun+watcher+Vite inside the container.
  > Note: inside the container, `local` reaches the db/mailpit **containers**, not
  > a host `localhost` — run `local` from the host, or use `local-docker` from
  > inside, unless you point `SPRING_DATASOURCE_URL`/`MAIL_SMTP_HOST` at the
  > compose service names.

## Tests

`cd backend && ./gradlew test` — Testcontainers starts a throwaway Postgres via
the mounted Docker socket. `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`
(set in `devcontainer.json`) lets the container reach it on Docker Desktop.

## Notes / gotchas

- **Docker Desktop socket** lives at `~/.docker/desktop/docker.sock` (not
  `/var/run/docker.sock`); the mount in `devcontainer.json` handles that. If you
  switch to plain Docker Engine, change that mount source to `/var/run/docker.sock`.
- **Shared Claude auth:** mounting `~/.claude` shares your login/session into the
  container. Use Claude Code in one place at a time to avoid history races.
- **UID:** the container's `vscode` user is uid 1000, matching the host, so the
  mounted socket and `~/.claude` have the right permissions.
