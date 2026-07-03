# Stage 1: Build the JPro distribution using Gradle
FROM eclipse-temurin:17-jdk-jammy AS build

# Set the working directory inside the container
WORKDIR /app

# Copy gradle wrapper and related files
COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew

# Copy build configuration files
COPY build.gradle settings.gradle ./
COPY app/build.gradle app/
COPY example-plugins/build.gradle example-plugins/

# Run a dry build to download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy the source code
COPY app/src/ app/src/
COPY example-plugins/src/ example-plugins/src/

# Build the JPro distribution zip
RUN ./gradlew :app:jproRelease --no-daemon

# Stage 2: Create the runtime environment
FROM eclipse-temurin:17-jre-jammy

# Install necessary libraries for JavaFX
RUN apt-get update && apt-get install -y \
    libgtk-3-0 \
    libgl1 \
    libx11-6 \
    libasound2 \
    unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Extract the built distribution from the build stage
COPY --from=build /app/app/build/distributions/app-jpro.zip /app/
RUN unzip app-jpro.zip && rm app-jpro.zip && mv app-jpro/* . && rm -r app-jpro

# Expose the default port
EXPOSE 8080

# Run the JPro launch script
CMD ["sh", "-c", "bin/start.sh -Djpro.port=${PORT:-8080}"]
