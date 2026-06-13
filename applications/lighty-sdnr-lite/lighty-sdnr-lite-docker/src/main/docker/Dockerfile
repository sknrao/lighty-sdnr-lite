FROM eclipse-temurin:21-jre-alpine

ENV IOSMCN_HOME=/opt/iosmcn
WORKDIR ${IOSMCN_HOME}

# Create new user 'iosmcn' with UID 1000
# https://wiki.alpinelinux.org/wiki/Setting_up_a_new_user
# https://github.com/nodejs/docker-node/blob/main/docs/BestPractices.md
RUN addgroup --system iosmcn && adduser --system --ingroup iosmcn --uid 1000 iosmcn

# Move ownership of /opt/iosmcn/app folder to iosmcn user
RUN mkdir ${IOSMCN_HOME}/app ${IOSMCN_HOME}/config
RUN chown -R iosmcn:iosmcn ${IOSMCN_HOME}
# Switch to iosmcn user
USER iosmcn

COPY LICENSE ${iosmcn.app.name} entrypoint.sh app/
COPY restConfConfig.json config/

EXPOSE 8888
WORKDIR /opt/iosmcn
ENTRYPOINT ["/opt/iosmcn/app/entrypoint.sh", "/opt/iosmcn/app/${iosmcn.app.jar}", "/opt/iosmcn/config/restConfConfig.json"]