# Build Java with scripts/release.py; only the verified jar and Cangjie sources enter this context.
ARG JAVA_IMAGE=public.ecr.aws/docker/library/eclipse-temurin@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0
FROM ${JAVA_IMAGE} AS component
ARG TARGETARCH
RUN sed -i -e '/^deb /{/ main/!d;}' -e 's|http:|https:|g' -e 's/ restricted//g' -e '/jammy-backports/d' /etc/apt/sources.list && \
    apt-get -o Acquire::Retries=2 -o Acquire::https::Timeout=20 update && apt-get install -y --no-install-recommends gcc g++ binutils libc6-dev libssl-dev && rm -rf /var/lib/apt/lists/*
COPY deploy/build/cangjie-sdk.tar.gz /tmp/sdk.tar.gz
COPY cangjie/task-preview/src/ /src/
COPY deploy/build/cangjie-sdk.sha256 /tmp/sdk.sha256
RUN cd /tmp && sha256sum -c sdk.sha256 && tar -xzf sdk.tar.gz && \
    export CANGJIE_HOME=/tmp/cangjie && export PATH=/tmp/cangjie/bin:$PATH && \
    /tmp/cangjie/bin/cjc --diagnostic-format noColor /src/*.cj -o /tmp/task-preview && \
    mkdir -p /output/runtime && cp /tmp/task-preview /output/task-preview && \
    cp -a /tmp/cangjie/runtime/lib/linux_*_cjnative/. /output/runtime/ && chmod -R a+rX /output

FROM ${JAVA_IMAGE}
ARG REVISION=unversioned
LABEL org.opencontainers.image.title="Campus course backend" org.opencontainers.image.revision=${REVISION}
RUN sed -i -e '/^deb /{/ main/!d;}' -e 's|http:|https:|g' -e 's/ restricted//g' -e '/jammy-backports/d' /etc/apt/sources.list && \
    apt-get -o Acquire::Retries=2 -o Acquire::https::Timeout=20 update && apt-get install -y --no-install-recommends curl libstdc++6 libssl3 && rm -rf /var/lib/apt/lists/* && \
    groupadd -g 10001 campus && useradd -u 10001 -g campus -M -s /usr/sbin/nologin campus
WORKDIR /app
COPY backend/target/campus-help-1.0.0.jar /app/app.jar
COPY --from=component /output/ /opt/task-preview/
ENV SPRING_PROFILES_ACTIVE=deploy TASK_PREVIEW_BINARY=/opt/task-preview/task-preview TASK_PREVIEW_RUNTIME=/opt/task-preview/runtime
USER 10001:10001
RUN printf 'CAMPUS_TRANSITION_V1\tPARALLEL\nTASK\t1\tBORROW\tPENDING\t0\nEVENT\t1\tcheck\t0\tCANCEL\tPUBLISHER\nEND\n' | \
    env -i PATH=/usr/bin:/bin LD_LIBRARY_PATH=/opt/task-preview/runtime /opt/task-preview/task-preview
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/app.jar"]
