# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached across builds that only
# change application source, not the dependency set.
COPY .mvn/ .mvn/
COPY pom.xml .
RUN mvn -B -s .mvn/settings.xml dependency:go-offline

COPY src/ src/
RUN mvn -B -s .mvn/settings.xml -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /build/target/ledgerflow-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
