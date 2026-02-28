# AGENTS.md

## Build Commands

This is a Maven-based Spring Boot 3.2.5 + Kotlin 1.9.23 project. Java 21 is required.

- **Full build**: `mvn clean install`
- **Run application**: `./start.sh` (downloads Maven wrapper if needed)
- **Run all tests**: `mvn test`
- **Run single test class**: `mvn test -Dtest=ClassName` (e.g., `mvn test -Dtest=RecommendationEngineTest`)
- **Run single test method**: `mvn test -Dtest=ClassName#methodName`
- **Run with profile**: `mvn test -Dspring.profiles.active=test`

No lint tools are currently configured. Add ktlint or detekt if needed.

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

- Database: PostgreSQL in production, H2 for tests
- RAG ingestion: PDFs stored in `data/plans/`, chunked via RagService
- Vector DB: Verify ingestion via RagService methods
- No mvnw wrapper - start.sh downloads it
