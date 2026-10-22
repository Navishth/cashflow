FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml maven-settings.xml ./
COPY src ./src
RUN mvn -s maven-settings.xml -B -ntp package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app && mkdir /app/mail && chown app:app /app/mail
COPY --from=build /build/target/cashflow-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
