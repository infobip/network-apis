FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY infobip-napi-enterprise-backend/pom.xml infobip-napi-enterprise-backend/pom.xml
RUN ./mvnw -B -q dependency:go-offline
COPY infobip-napi-enterprise-backend/src infobip-napi-enterprise-backend/src
RUN ./mvnw -B -q package -DskipTests \
    && cp infobip-napi-enterprise-backend/target/enterprise-backend-service-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /opt/app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
