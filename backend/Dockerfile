# Builds and runs the SSHomcat backend. Used by the repo-root docker-compose.dev.yml for local
# end-to-end testing (docs/roadmap.md M1) -- NOT a prescribed production deployment shape; a real
# deployment follows docs/architecture.md (Tomcat 10.1.x behind Apache, connector on 127.0.0.1).

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM tomcat:10.1-jdk17-temurin
# Deploy as ROOT so paths match docs/api.md exactly (/api/health, /ws/ssh -- no context prefix).
RUN rm -rf /usr/local/tomcat/webapps/ROOT
COPY --from=build /workspace/target/sshomcat.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
