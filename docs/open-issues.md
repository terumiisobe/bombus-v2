# Open Issues

## epic-4 — Colmeia Count (review 2026-07-03)

- [ ] 🟢 `StatusColmeiaLookupAdapter.findIdByName` has no direct integration test — only exercised indirectly via `perdidaId` in `ColmeiaCountAdapterIntegrationTest`; the unknown-name → `null` path is only faked in the unit test (`src/main/kotlin/com/bombus/colmeia/adapter/outbound/persistence/StatusColmeiaLookupAdapter.kt`).
- [ ] 🟢 No test for a breakdown query that also carries an explicit `statusId` (the `includeStatusId` branch in `CountColmeiasService.breakdown`) (`src/main/kotlin/com/bombus/colmeia/application/CountColmeiasService.kt`).
