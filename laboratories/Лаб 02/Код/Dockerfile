FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --uid 10001 --create-home app && mkdir -p /app/data && chown -R app:app /app
WORKDIR /app
COPY --from=builder /build/target/job-portal-ipz-1.0.0.jar app.jar
ENV IPZ_BIND_HOST=0.0.0.0
USER 10001
EXPOSE 18727
CMD ["java", "-jar", "app.jar", "--port", "18727", "--db", "data/jobs.sqlite", "--seed"]
