FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/agrotec-api-*.jar app.jar
# Sem root: se a API for comprometida, o atacante nao ganha o container inteiro.
RUN useradd --system --uid 10001 --no-create-home agroclima
USER agroclima
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
