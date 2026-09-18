# Dev Container image for pollsystem — a reproducible Ubuntu dev environment.
# Java 17 + Node 22 come from devcontainer Features (see devcontainer.json). This
# image adds the Docker CLI + compose plugin (it talks to the HOST's Docker
# Desktop via the socket bind-mounted in devcontainer.json — no daemon runs in
# here; "Docker-outside-of-Docker"), plus a psql client for convenience.
FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

RUN install -m 0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
 && chmod a+r /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable" \
      > /etc/apt/sources.list.d/docker.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends docker-ce-cli docker-compose-plugin postgresql-client \
 && rm -rf /var/lib/apt/lists/*
