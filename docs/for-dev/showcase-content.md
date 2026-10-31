# Service Advertiser API

A backend project exploring what it takes to run, observe, test, and release an API—not just implement its business features.

Built with Spring Boot, Service Advertiser provides an API for businesses to publish products and services and for customers to discover them. Around that core, the project brings together security, monitoring, isolated environments, simulated traffic, and a deliberate release process.

## Tech stack

- **Application:** Java 21, Spring Boot, Spring Security, Spring Data JPA, and Maven
- **Data:** PostgreSQL and Flyway database migrations
- **API:** REST, JWT authentication, and OpenAPI / Swagger UI
- **Observability:** Spring Boot Actuator, Micrometer, Prometheus, Grafana, Grafana Loki, and Grafana Alloy
- **Infrastructure:** Docker, Docker Compose, and Caddy
- **Testing and delivery:** JUnit, Testcontainers, Locust, Python, GitHub Actions, and GitHub Releases

## The application

The API models a simple service advertising platform. Users can create accounts, register a business, and manage product and service listings. Visitors can explore active businesses and listings, searching and filtering by categories, location, and other relevant details. Administrators can moderate businesses.

The application includes input validation, role and ownership checks, password protection, short-lived access tokens, and rotating refresh tokens. Configurable rate limits protect authentication endpoints, while health checks and database safeguards support reliable operation. Interactive API documentation makes the endpoints easier to explore.

## Observability

The project brings application logs and metrics together so activity can be explored from a single dashboard.

- **Spring Boot Actuator and Micrometer** expose application health and metrics.
- **Prometheus** collects metrics such as request rates, response times, JVM memory, and database connection pool activity.
- **Grafana Alloy** collects structured application logs and sends them to **Loki** for storage and querying.
- **Grafana** connects to Prometheus and Loki, presenting metrics and logs in dashboards for investigation.

Request IDs help connect a request to its log entries. Development uses application log files; the containerized environments collect their own application logs. Each environment has separate monitoring data, and the API can continue running when monitoring is unavailable.

## Three environments, one application

Docker Compose organizes the project into independent environments, each with its own database and monitoring state.

- **Development:** Spring Boot runs locally against PostgreSQL in Docker, with monitoring available when needed.
- **Simulation:** A containerized API, isolated PostgreSQL database, monitoring stack, and Locust workload create a safe space for synthetic activity.
- **Deployment:** A single Linux VM setup runs the application behind Caddy, which provides HTTPS. The setup includes private monitoring, file-based secrets, persistent storage, and a documented backup and restore process.

Simulation and deployment use the same application image, with configuration adapted to each environment. The deployment setup is designed for a small VM and keeps deployment and operational decisions explicit.

## Simulating activity

The simulation uses Locust to exercise real API flows with synthetic users and data. A bootstrap process creates 100 accounts, including 20 providers, along with 20 businesses and 60–80 listings. Continuous activity is weighted toward reading and can also create and update data through the API.

The workload supports up to **100 concurrent simulated users**. Over time, it can grow to **500 stored or reserved accounts** and **100 live businesses**, with up to 10 listings per business. Locust provides a live interface and can produce HTML and CSV reports, while Grafana shows the application's behavior during a run.

The simulation has its own API container, database, and monitoring storage. It uses test credentials and disables rate limiting to focus on workload behavior; other request and database protections remain active. This makes it useful for repeatable exploration, though it is not an exact replica of deployment.

In a documented local run, 100 simulated users generated 10,590 requests over a five-minute measurement window with no recorded Locust failures. This is a result for that workload and workstation, not a production capacity guarantee.

## Testing and releases

The project combines unit and integration tests with container-based checks and disposable environment acceptance tests. Integration tests use an isolated PostgreSQL container to exercise database-backed behavior.

GitHub Actions runs verification for pull requests targeting the main branch. For a release, a version is selected and committed manually, then an annotated version tag starts a more extensive workflow. That workflow verifies the tagged source, runs tests and infrastructure checks, builds a temporary image, and exercises disposable environments before creating a draft GitHub Release.

A person reviews and publishes the draft. Deployment remains a separate manual operation: publishing a release does not deploy the application or publish a container image. This keeps the release process practical while preserving a clear review point.

## Project goal

Service Advertiser is a learning and showcase project for the work surrounding a backend API: making behavior visible, creating realistic test traffic, protecting and operating the service, and delivering changes with confidence. Its business domain gives those infrastructure choices a concrete application to support.
