FROM public.ecr.aws/docker/library/maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace/packages/backend
COPY packages/backend/pom.xml ./pom.xml
COPY packages/backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system omnistudy \
    && useradd --system --gid omnistudy --home-dir /app --shell /usr/sbin/nologin omnistudy

WORKDIR /app
COPY --from=build /workspace/packages/backend/target/*.jar /app/omnistudy.jar
COPY skills /app/skills
COPY AGENTS.md /app/AGENTS.md
RUN mkdir -p /tmp/omnistudy-agentscope \
    && cp /app/AGENTS.md /tmp/omnistudy-agentscope/AGENTS.md \
    && chown -R omnistudy:omnistudy /app /tmp/omnistudy-agentscope

USER omnistudy
EXPOSE 8080 9091

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/omnistudy.jar"]
