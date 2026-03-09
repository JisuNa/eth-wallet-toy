# CLAUDE.md

Kotlin + Spring Boot + JPA based MSA backend service

## Tech Stack
- Kotlin 2.2+, Java 21
- Spring Boot 4.0.x (Spring Framework 7), Spring Data JPA
- Gradle (Kotlin DSL)
- MySQL 8, Redis, Kafka, Debezium
- web3j, Resilience4j, WireMock, LocalStack (KMS)
- Docker Compose

## Architecture
- DDD + EDA + MSA
- wallet-service(8081), transaction-service(8082), blockchain-service(8083)

## Project Structure
- Monorepo: Gradle multi-module (core, wallet, transaction, blockchain)
- `core` module: shared library (domain primitives, event schemas, exception handling, JPA/Redis/Kafka/KMS config)
- Domain modules (`wallet`, `transaction`, `blockchain`): Spring Boot apps depending on `:core`
- No compile-time dependency between domain modules. Inter-service communication via REST (Circuit Breaker) and Kafka.

## Rules
1. Do not perform tasks that were not explicitly requested. Do not create, modify, or delete files beyond what is asked.
2. Entity and DTO must be separated
3. Use null safety, no !! operator
4. No unnecessary comments. Only comment on **why**, never on **what**. Only TODO/FIXME allowed.
5. Do not create separate Mapper classes. Use extension functions (toDomain(), toEntity(), toResponse()) instead.
6. Do not create interfaces for Services unless there are multiple implementations.
7. Do not write boilerplate code that Kotlin already solves (e.g., manual getters/setters, explicit singletons — use data class, object, etc.)
8. Do not catch exceptions silently. Always log or rethrow.
9. Do not use field injection (@Autowired). Use constructor injection only.
10. Separate logical steps within a method with blank lines.

## Git Strategy
- main ← dev ← feature/*
- squash merge
- commit: feat: / fix: / refactor: / chore:

## Environment
- JDK 21
- Profiles: local, dev, prod

## References
TBD
