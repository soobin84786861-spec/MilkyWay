# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.skku.milkyway.MilkyWayApplicationTests"
```

## Tech Stack

- **Java 17**, **Spring Boot 4.0.5**
- **Spring Web MVC** (REST controllers)
- **Thymeleaf** (templating)
- **Spring Validation**
- **Lombok**

## Architecture

The project follows a package-by-feature structure under `com.skku.milkyway`:

```
api/
  <feature>/
    controller/   # @RestController classes
```

New features should follow this pattern: create a new package under `api/` with a `controller/` subpackage (and add `service/`, `dto/`, `repository/` as needed).

The entry point is `MilkyWayApplication.java` with `@SpringBootApplication`.
