# ----------------------------------------------------------------------------------------------------------------------
# Stage 1: Build the Spring Boot application
# ----------------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-focal as builder

# Set working directory inside the container
WORKDIR /app

# Copy the Maven wrapper files to enable Maven builds without a global Maven installation
COPY mvnw .
COPY .mvn .mvn

# Copy the pom.xml file first to download dependencies, leveraging Docker layer caching
# This step only runs if pom.xml changes, speeding up subsequent builds
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

# Copy the rest of the source code
COPY src src

# Build the application
# -DskipTests ensures tests are not run during the Docker build process, speeding it up
# --offline uses previously downloaded dependencies
RUN ./mvnw package -DskipTests

# ----------------------------------------------------------------------------------------------------------------------
# Stage 2: Create the final running image
# ----------------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-focal

# Set working directory inside the container
WORKDIR /app

# Install Arduino CLI and pre-download the AVR core so compilation works offline.
# curl is needed for the install script; we remove it afterwards to keep the image lean.
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh \
        | BINDIR=/usr/local/bin sh && \
    apt-get purge -y curl && apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Pre-install Arduino AVR core (P1 boards: Uno, Mega).
# ESP8266/ESP32 cores require additional board manager URLs and are installed on demand (P2).
RUN arduino-cli core update-index && \
    arduino-cli core install arduino:avr

# Copy the built JAR file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the port on which the Spring Boot application will run
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
