# Self-hosted GitHub Actions runner for the Build workflow, and image for ci/local.sh.
# JDK 21 (Zulu, as actions/setup-java in the GitHub-hosted jobs) runs Gradle;
# the foojay resolver downloads the Java 25 toolchain into the Gradle volume.

FROM azul/zulu-openjdk:21.0.13 AS jdk

FROM ghcr.io/actions/actions-runner:2.338.2

# The runner user gets the uid and gid of the host user, so ci/local.sh writes host files with the right owner.
ARG RUNNER_UID=1000
ARG RUNNER_GID=1000

USER root

COPY --from=jdk /usr/lib/jvm/zulu21-ca-amd64 /usr/lib/jvm/zulu21

# gh: the release draft job calls the GitHub API. fontconfig: the IDE test framework renders text headless.
RUN set -eux; \
    curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
      -o /usr/share/keyrings/githubcli-archive-keyring.gpg; \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
      > /etc/apt/sources.list.d/github-cli.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends gh fontconfig fonts-dejavu-core; \
    rm -rf /var/lib/apt/lists/*; \
    # Ubuntu 24.04 images have the user "ubuntu" with uid and gid 1000.
    if id ubuntu >/dev/null 2>&1; then userdel -r ubuntu; fi; \
    groupmod -g "$RUNNER_GID" runner; \
    usermod -u "$RUNNER_UID" -g "$RUNNER_GID" runner; \
    # The jobs run code from the pull requests: the runner user gets no sudo and no docker group.
    gpasswd -d runner sudo; \
    gpasswd -d runner docker; \
    rm -f /etc/sudoers.d/runner; \
    mkdir -p /home/runner/.gradle /runner-state; \
    chown -R runner:runner /home/runner /runner-state

COPY --chmod=755 entrypoint.sh /usr/local/bin/t3-ci-entrypoint

ENV JAVA_HOME=/usr/lib/jvm/zulu21 \
    PATH=/usr/lib/jvm/zulu21/bin:$PATH

USER runner
WORKDIR /home/runner

ENTRYPOINT ["t3-ci-entrypoint"]
CMD ["run"]
