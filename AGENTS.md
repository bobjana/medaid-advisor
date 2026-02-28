# AGENTS.md

## Build Commands

This is a Maven-based Spring Boot 3.2.5 + Kotlin 1.9.23 project. Java 21 is required.

We use [just](https://github.com/casey/just) as the task runner for common development tasks.

### Just Commands (Recommended)

| Command | Description |
|---------|-------------|
| `just` | Show all available commands |
| `just dev` | Start full development environment (DB + Ollama + App) |
| `just build` | Build the project |
| `just test` | Run all tests |
| `just run` | Run the application |
| `just ingest-plans` | Ingest plan data from PDFs |
| `just ingest-all` | Ingest both plans and RAG data |
| `just db-up` | Start PostgreSQL and Ollama |
| `just db-down` | Stop database services |
| `just db-reset` | Reset database (drop and recreate) |
| `just health` | Check API health |
| `just docs` | Show API documentation |

### Maven Commands (Alternative)

- **Full build**: `mvn clean install`
- **Run all tests**: `mvn test`
- **Run single test class**: `mvn test -Dtest=ClassName` (e.g., `mvn test -Dtest=RecommendationEngineTest`)
- **Run single test method**: `mvn test -Dtest=ClassName#methodName`
- **Run with profile**: `mvn test -Dspring.profiles.active=test`

### Legacy Scripts

Scripts are located in `infra/scripts/`:
- `start.sh` - Start PostgreSQL, Ollama, and the application
- `test-ingestion.sh` - Test ingestion functionality

## Development Workflow

### First Time Setup

```bash
# 1. Start infrastructure (PostgreSQL + Ollama)
just db-up

# 2. Build the project
just build

# 3. Ingest plan data from PDFs
just ingest-plans

# 4. Run the application
just run
```

### Daily Development

```bash
# Quick start everything
just dev

# Or step by step:
just db-up      # Start databases
just build      # Build project
just run        # Run app (in another terminal)
just ingest-plans  # Ingest/update plans
```

### Database Migrations

We use **Flyway** for database schema management:

- Migrations are in `src/main/resources/db/migration/`
- Schema is version controlled via SQL files
- Hibernate validates schema on startup (`ddl-auto: validate`)
- Run `just db-reset` to completely reset the database

## Code Style Guidelines

### File Structure

Package: `cc.zynafin.medaid.{layer}`
- `controller/` - REST controllers (@RestController)
- `service/` - Business logic (@Service)
- `domain/` - Entities, DTOs, value objects
- `repository/` - Data access (Spring Data JPA)

### Class Types

- **DTOs**: Use `data class` with properties in constructor
- **Entities**: Regular `class` with @Entity and @Id
- **Services**: Regular `class` with @Service, constructor injection
- **Repositories**: Interface extending `JpaRepository`

### Dependencies

Constructor injection only. No field injection.

```kotlin
@Service
class RecommendationService(
    private val repository: RecommendationRepository,
    private val ragService: RagService
) {
    // ...
}
```

### Naming Conventions

- Classes: PascalCase (e.g., `RecommendationService`)
- Methods: camelCase (e.g., `getRecommendations`)
- Properties: camelCase (e.g., `planName`)
- Constants: UPPER_SNAKE_CASE (e.g., `DEFAULT_LIMIT`)

### Kotlin Idioms

- Use `?:` Elvis operator for null coalescing
- Use `?.` safe calls for nullable properties
- Use `when` expressions instead of if-else chains
- Use `val` for immutable, `var` only when needed
- Use `listOf()`, `mapOf()` for immutable collections

### Error Handling

No @ControllerAdvice currently defined. Handle errors in service layer:

```kotlin
try {
    // operation
} catch (e: Exception) {
    log.error("Error description", e)
    throw CustomException("message")
}
```

Use SLF4J logger (`log.error()`, `log.info()`, `log.warn()`).

## Testing Conventions

Test framework: JUnit 5 + MockK + Spring Boot Test

- **Test location**: Co-located with source (e.g., `RecommendationServiceTest.kt` next to `RecommendationService.kt`)
- **Structure**: Arrange-Act-Assert pattern
- **Mocking**: Use `mockk<T>()` and `every { ... } returns ...`
- **Assertions**: Use standard JUnit `assertThat()` or Truth if configured

Test profile uses H2 in-memory database via `application-test.yml`.

## Key Project Notes

- **Database**: PostgreSQL in production, H2 for tests
- **Migrations**: Flyway manages schema, migrations in `db/migration/`
- **RAG ingestion**: PDFs stored in `data/plans/`, chunked via RagService
- **Vector DB**: pgvector extension for semantic search
- **Ingestion**: Pipeline is idempotent - run multiple times safely
- **No mvnw wrapper** - use system Maven or install wrapper
