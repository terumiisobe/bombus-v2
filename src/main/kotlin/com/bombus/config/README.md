# Config (composition root)

All Spring wiring / dependency injection lives here. The core declares port
interfaces; this layer binds concrete adapter implementations to them.

- `@Configuration` classes, bean definitions, `SecurityFilterChain`
- `@ConfigurationProperties` data classes for type-safe config
- The Spring Boot `@SpringBootApplication` main class
