FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B && \
    cp target/auth-api-*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre-noble AS runtime

WORKDIR /app

RUN useradd --system --uid 10001 spring
COPY --from=build --chown=spring:spring /workspace/app.jar app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
