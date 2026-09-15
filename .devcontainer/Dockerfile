# Extends the stock Java 25 dev image with Maven baked in.
# Why not the java feature? There is no standalone maven feature, and using
# the java feature only for Maven also exports a dangling SDKMAN JAVA_HOME
# that shadows the image's real JDK and breaks `mvn`.
FROM mcr.microsoft.com/devcontainers/java:25-bookworm

ENV SDKMAN_DIR=/usr/local/sdkman
RUN bash -c "source ${SDKMAN_DIR}/bin/sdkman-init.sh && sdk install maven 3.9.10"

USER vscode
