docker compose up -d	        Start PostgreSQL
docker compose down	            Stop and remove the container, preserve data
.\mvnw.cmd spring-boot:run	    Run the backend
.\mvnw.cmd clean verify	        Build and execute tests




Prioritize Spring Data JAP as the default database access technology. And use JdbcTemplate only when JAP is not straight forward to use.


A Spring Boot application implementing domain-specific business functionality, supported by cross-cutting security mechanisms, automated testing, observability, and infrastructure capabilities.