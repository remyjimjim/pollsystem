# Dev Container image for pollsystem — a reproducible Ubuntu dev environment.
# Java 17 + Node 22 come from devcontainer Features (see devcontainer.json). This
# image adds the Docker CLI + buildx + compose plugins (it talks to the HOST's
# Docker Desktop via the socket bind-mounted in devcontainer.json — no daemon
# runs in here; "Docker-outside-of-Docker"), plus a psql client for convenience.
# buildx is required: the backend image uses `RUN --mount=type=cache` (BuildKit),
# which the classic builder can't do — without buildx, `local-docker` fails to
# build backend/frontend.
FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

RUN install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
 && chmod a+r /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin docker-compose-plugin postgresql-client \
 && rm -rf /var/lib/apt/lists/*

# flyctl (Fly.io CLI) for `BuildAndDeploy.bash test` staging deploys. Installed
# system-wide (/usr/local/bin); auth comes from the host's ~/.fly, mounted in
# devcontainer.json. (`test-secrets` still needs the host OS keychain, so it
# stays host-only.)
RUN curl -fsSL https://fly.io/install.sh | FLYCTL_INSTALL=/usr/local sh \
 && /usr/local/bin/flyctl version
