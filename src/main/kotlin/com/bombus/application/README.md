# Application (use cases)

Orchestrates domain objects to fulfill use cases. Defines the **ports** it needs
from the outside world. Framework-free except for the small exception hierarchy
(`NotFoundException`, `ConflictException`, ...) the web adapter translates to HTTP.

Depends on: domain + port interfaces only — never on concrete adapters.

- `port/inbound/`  — driving (inbound) ports: use-case interfaces (e.g. `CreateHiveUseCase`)
- `port/outbound/` — driven (outbound) ports: interfaces the core requires
                     (e.g. `LoadHivePort`, `SaveHivePort`, `EventPublisher`)
- `usecase/`       — use-case implementations; `@Transactional` boundary lives here
