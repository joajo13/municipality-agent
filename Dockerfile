# syntax=docker/dockerfile:1

# --------------------------------------------------------------------------------------
# Build. A full JDK, the wrapper, and nothing from the host: whatever builds here builds
# the same way on a laptop, on a runner, and in six months.
# --------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

# The dependencies change far less often than the code does. Resolving them in their own
# layer means editing a Java file does not re-download the internet.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

# Boot's own layering: dependencies, then the framework, then this application. Rebuilding
# after a one-line change replaces the last layer and leaves the other three alone.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# --------------------------------------------------------------------------------------
# Run. A JRE, four layers, and a user that is not root.
# --------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# A system account with no shell and no home to write to. If something does get in, it
# gets in as somebody who cannot install anything or leave anything behind.
RUN groupadd --system --gid 1001 agent \
 && useradd --system --uid 1001 --gid agent --no-create-home --shell /usr/sbin/nologin agent

WORKDIR /app

COPY --from=build --chown=agent:agent /build/extracted/dependencies/ ./
COPY --from=build --chown=agent:agent /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=agent:agent /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=agent:agent /build/extracted/application/ ./

USER agent:agent

EXPOSE 8080

# MaxRAMPercentage rather than a fixed heap: the container is told how much memory it has
# and the JVM should believe it, whatever that number turns out to be in production.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

# Readiness, not liveness: this asks whether the database is reachable, which is the
# difference between a process that exists and a service that can answer. /dev/tcp is a
# bash builtin, and bash is named explicitly because the default shell here is not it.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
    CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/localhost/8080 && printf 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

# Not "java -jar": the jar has been taken apart into four layers merged into this
# directory, and the loader is started from the classpath rather than from an archive.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
