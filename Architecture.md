# ARCHITECTURE.md — System Design Specification
Master Architecture Blueprint & Maven Directory Layout (S52B1 Enterprise Banking System)

---

## Document Control & Version History

| Attribute | Details |
| :--- | :--- |
| **Document Version** | `1.1.0` |
| **Status** | `APPROVED` |
| **Target System Milestone** | `S52B1 — Phase 1: Core Infrastructure` |
| **Last Updated** | September 2026 |
| **Target Runtime** | Java 25 / Jakarta EE 11 / GlassFish 8 |

### Revision Log

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-09-15 | Lead Architect | Initial template & directory mapping specification. |
| `1.1.0` | 2026-09-22 | Development Team | Updated to reflect Java 25 / Jakarta EE 11 stack, dual Surefire/Failsafe + Cargo execution pipeline, HikariCP config, DataFaker database seeding, and 4-phase implementation roadmap. |

---

ARCHITECTURE.md — System Design Specification
Master Architecture Blueprint & Maven Directory Layout (S52B1 Enterprise Banking System)
1. System Overview & Core Philosophy
This document defines the high-level architecture, directory organization, data flow, and module boundaries for the S52B1 Enterprise Banking System. All development MUST adhere to the structural constraints, lifecycle hooks, and architectural boundaries defined here.
Target Technology Stack
• Java Version: Java 25
• Enterprise Platform: Jakarta EE 11
• Application Server: GlassFish 8 (Managed via Codehaus Cargo Plugin 1.10.15)
• Build Tooling: Apache Maven (Lifecycle managed via maven-surefire-plugin for unit tests and maven-failsafe-plugin for integration tests)
• Database & Persistence: Embedded H2 Database (In-Memory PostgreSQL mode), HikariCP 6.0.0 Connection Pooling, JDBC / Jakarta Persistence (JPA)
• Security & Auth: JJWT 0.12.6, Jakarta Security (DatabaseIdentityStore)
• Test Utility: DataFaker 2.3.0 / 2.4.2 for database seeding and mock data generation
2. File Structure to Program Structure Mapping
The system follows a standard Maven directory structure mapping filesystem paths directly to runtime responsibilities and execution boundaries:

Architectural Layer	File System Location	Program Runtime Mapping	Data Storage Boundaries
API / Transport Layer	src/main/java/com/bank/security/api/	Jakarta REST Controllers, DTO request parsing, HTTP response mapping.	Stateless (No direct persistence access).
Security & Auth Module	src/main/java/com/bank/security/auth/	Jakarta Security IdentityStore, JJWT authorization filters, password hashing utilities.	Queries credential & user role tables.
Domain Service Layer	src/main/java/com/bank/security/service/	Orchestrates transactional control flow, enforces double-entry rules, processes accruals.	In-memory state during request execution.
Data Access Layer	src/main/java/com/bank/security/repository/	JPA Repositories, JDBC DAOs, HikariCP DataSource integrations.	Directly interfaces with H2 DB tables.
Data Seeding & Mappings	src/test/java/com/bank/security/util/	DataFaker-based batch seeding (DatabaseSeeder), schema initializers.	Pre-populates H2 during unit/IT setup.
Configuration & Metadata	src/main/resources/META-INF/	persistence.xml, beans.xml, logging configurations.	Read-only container specifications.
3. Standardized Maven Directory Tree
Plaintext

S52B1/
├── pom.xml                                   # Master Maven Configuration (Cargo, Surefire, Failsafe, JJWT, HikariCP)
├── ARCHITECTURE.md                           # Master Architecture Specification (This Document)
├── README.md                                 # Environment setup & local execution guides
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── bank/
    │   │           └── security/
    │   │               ├── api/              # REST Endpoints & DTOs
    │   │               ├── auth/             # Jakarta Security, IdentityStore, Password Hashing
    │   │               ├── config/           # HikariCP & @DataSourceDefinition setups
    │   │               ├── domain/           # JPA Entities (User, Account, Ledger, AuditLog)
    │   │               ├── repository/       # Data Access / JPA Repositories
    │   │               └── service/          # @Transactional Business Services (Ledger, Audit)
    │   └── resources/
    │       └── META-INF/
    │           └── persistence.xml           # JPA Persistence Unit Configuration
    └── test/
        ├── java/
        │   └── com/
        │       └── bank/
        │           └── security/
        │               ├── *Test.java        # Unit Tests (Executed via Surefire during 'mvn test')
        │               ├── *IT.java          # Integration Tests (Executed via Failsafe during 'mvn verify')
        │               └── util/             # DatabaseSeeder & DataFaker Utilities
        └── resources/
            └── schema.sql                    # H2 Schema DDL for Seeding

4. Bottom-Up Implementation Roadmap
Development follows a strict 4-phase sequence. Each milestone must be validated using mvn verify prior to proceeding:
Plaintext

[ Phase 1: Core Foundation ]  ➜  [ Phase 2: User Security & Auth ]  ➜  [ Phase 3: Ledger System ]  ➜  [ Phase 4: Audit System ]

Phase 1: Core Infrastructure & Persistence Setup
• Deliverables:
• Configure HikariCP 6.0.0 connection pooling and H2 database integration.
• Define base JPA entities (UserEntity, AccountEntity, LedgerEntry, AuditLogEntity).
• Implement DatabaseSeeder utilizing DataFaker and JDBC batch statements (PreparedStatement) with database truncation logic to maintain test isolation.
Phase 2: User Security & Authentication Module
• Deliverables:
• Implement secure password hashing helpers (Argon2 / PBKDF2).
• Configure Jakarta Security @DatabaseIdentityStoreDefinition or custom IdentityStore.
• Wire JJWT 0.12.6 token generation/verification into REST authentication filters.
Phase 3: Double-Entry Ledger System
• Deliverables:
• Implement @Transactional service methods enforcing double-entry bookkeeping (debits = credits).
• Build domain logic for account types (Checking, Savings, CD) and transaction execution.
• Add unit and integration tests under src/test/java/*IT.java.
Phase 4: Audit & Compliance System
• Deliverables:
• Build CDI interceptors (@AroundInvoke) and JPA listeners to observe ledger events.
• Capture user principal, action timestamp, IP address, and payload deltas.
• Persist audit entries to an append-only audit_logs table.
5. Build & Testing Execution Rules
To ensure test isolation and prevent unclosed GlassFish server processes, tests are partitioned between two plugins:
1. Unit Testing (mvn test):
• Handled by maven-surefire-plugin (3.5.0).
• Targets *Test.java files.
• Executes in memory without starting GlassFish.
2. Integration Testing (mvn verify):
• Handled by cargo-maven3-plugin (1.10.15) and maven-failsafe-plugin (3.5.0).
• Cargo boots GlassFish 8 during pre-integration-test.
• Failsafe executes *IT.java test suites against localhost:8080.
• Cargo shuts down GlassFish safely during post-integration-test regardless of test outcomes.
• Failsafe verifies build success/failure during verify.
