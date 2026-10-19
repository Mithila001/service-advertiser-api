## Task list

### Completed
- User registration
- Login/logout with JWT access tokens and opaque refresh tokens
- Refresh token rotation, revocation, reuse detection, and expired-token cleanup
- Business lifecycle (create, view, update, delete)
- Business item management (create, view, update, delete)
- Business and item category catalogs and category assignment
- Province, district, and city location catalog
- Public business and item discovery with filters, sorting, and pagination
- Admin business moderation (suspend/reactivate) with paginated listings and audit history

### Local development commands

| Command                      | Purpose                                             |
| ---------------------------- | --------------------------------------------------- |
| `docker compose up -d`       | Start PostgreSQL                                    |
| `docker compose down`        | Stop and remove the container while preserving data |
| `.\mvnw.cmd spring-boot:run` | Run the backend                                     |
| `.\mvnw.cmd clean verify`    | Build and run tests                                 |

Prefer Spring Data JPA for database access. Use `JdbcTemplate` when JPA is not straightforward.

## Application Dev Feature

### Swagger UI and OpenAPI

- Swagger UI and generated OpenAPI documentation for exploring and trying API endpoints.

### API Documentation in Code

- API descriptions, request/response details, validation rules, and examples are documented in controller and DTO annotations.
- Shared authentication guidance, pagination rules, and standard error responses are configured in `OpenApiConfig`.
