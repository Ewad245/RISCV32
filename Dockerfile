# Stage 1: Build the JPro distribution using Gradle
FROM eclipse-temurin:21-jdk-jammy AS build

# Set the working directory inside the container
WORKDIR /app

# Install unzip
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

# Copy gradle wrapper and related files
COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew

# Copy build configuration files
COPY settings.gradle ./
COPY app/build.gradle app/
COPY example-plugins/build.gradle example-plugins/

# Run a dry build to download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy the source code
COPY app/src/ app/src/
COPY example-plugins/src/ example-plugins/src/

# Build the JPro distribution zip
RUN ./gradlew :app:jproRelease --no-daemon

# Dynamically find the zip, unzip it, and flatten the directory structure
RUN mkdir /app/unpacked && \
    unzip /app/app/build/distributions/*-jpro.zip -d /app/unpacked && \
    mv /app/unpacked/*/* /app/unpacked/

# Stage 2: Create the runtime environment
FROM eclipse-temurin:21-jre-alpine

# Install necessary libraries for JavaFX
RUN apk add --no-cache \
    gtk+3.0 \
    mesa-gl \
    libx11 \
    libxext \
    libxrender \
    libxtst \
    alsa-lib \
    fontconfig \
    ttf-dejavu \
    bash \
    unzip

WORKDIR /app

# Copy the clean, pre-extracted production files directly
COPY --from=build /app/unpacked /app

# Expose the default port
EXPOSE 8080

# Hard memory limits to prevent Render OOM crashes
# -Xmx256m: Restricts the JVM heap to 256MB, leaving the remaining 256MB for off-heap/OS.
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseSerialGC -XX:MaxRAMPercentage=50.0"

# Run the JPro launch script
CMD ["bash", "-c", "bin/start.sh -Djpro.port=${PORT:-8080}"]
