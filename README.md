# University Reservation Service (`reservation-service`)

Microservice for managing university facility reservations, approval workflows, and availability scheduling in the University Services Platform.

> **Service Ownership Notice:**
> This service owns **only reservation schema and data** (`reservations` and `reservation_approvals`).
> It does **not** own facility or user identity data and **must not directly access another microservice's database**.

---

## 1. Service Purpose & Architectural Responsibility

`reservation-service` manages the lifecycle of facility reservations for students, faculty, and administrators. Key responsibilities include:
- Validating reservation requests against operating hours, resource capacity, and current availability via the Group 6 `facility-resource-service`.
- Supporting auto-approval for low-risk resources (`approvalRequired = false`) and manager review workflows for approval-required resources (`approvalRequired = true`).
- Enforcing status transition constraints (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`).
- Recording chronological audit logs for all approval and rejection actions.
- Providing summary and trend endpoints for resource utilization reporting.

---

## 2. Tech Stack & Prerequisites

- **Java**: 17
- **Framework**: Spring Boot 4.1.1
- **Database**: MySQL 8.0 (Production / Docker), H2 (Local Dev / Tests)
- **Security**: Spring Security OAuth2 Resource Server (JWT)
- **API Documentation**: SpringDoc OpenAPI / Swagger UI 3.0.2
- **Build Tool**: Gradle 8.10+ / 9.x Wrapper

---

## 3. Configuration & Environment Variables

| Variable | Default Value | Description |
|---|---|---|
| `SERVER_PORT` / `server.port` | `8082` | Port running reservation-service |
| `DB_USERNAME` | `root` | MySQL Database Username |
| `DB_PASSWORD` | `root` | MySQL Database Password |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3307/reservation_db` | Database JDBC URL |
| `FACILITY_SERVICE_URL` | `http://localhost:8081` | Base URL for Group 6 `facility-resource-service` |
| `GROUP5_INTEGRATION_ENABLED` | `false` | Enable/disable Group 5 User Validation integration |
| `JWT_JWK_SET_URI` | `""` | JWK Set URI for JWT verification from identity-access-service |

---

## 4. External Dependencies & Integration Contracts

### Group 6: Facility Resource Service Integration
- Base URL: `${FACILITY_SERVICE_URL:http://localhost:8081}`
- Validation Endpoint: `GET /api/resources/{id}/validate`
- Availability Check Endpoint: `POST /api/resources/check-availability`
- Group 7/8 Compatibility Endpoints: `GET /api/resources/{id}/validate/group7`, `GET /api/resources/{id}/validate/group8`

### Group 5: User Validation Integration
- Abstraction: `UserValidationClient`
- Current Status: **BLOCKED** (Development / Mock mode active until Group 5 API endpoints are published).

---

## 5. Reservation Workflow & Status Flow

### Approval Required Logic
- If `approvalRequired = true` from facility validation response:
  - Reservation created with status `PENDING`. Requires Resource Manager approval via `POST /api/v1/reservations/{id}/approve`.
- If `approvalRequired = false`:
  - Reservation auto-approved with status `APPROVED` if valid and no conflicts exist.

### Allowed Status Transitions
- `PENDING -> APPROVED`
- `PENDING -> REJECTED` (requires written rejection reason)
- `PENDING -> CANCELLED`
- `APPROVED -> CANCELLED`

*Disallowed transitions*: `REJECTED -> APPROVED`, `CANCELLED -> APPROVED`, `CANCELLED -> REJECTED`, `APPROVED -> REJECTED`.

---

## 6. API Endpoint Summary

| HTTP Method | Path | Required Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/reservations` | Authenticated | Create a new reservation request |
| `GET` | `/api/v1/reservations` | Authenticated | List all reservations (filterable by status, resourceId, requesterId) |
| `GET` | `/api/v1/reservations/pending` | `RESOURCE_MANAGER` | View all pending reservations awaiting review |
| `GET` | `/api/v1/reservations/my` | Authenticated | View reservations created by the authenticated user |
| `GET` | `/api/v1/reservations/{id}` | Authenticated | View reservation details |
| `GET` | `/api/v1/reservations/{id}/history` | Authenticated | View approval and rejection history |
| `POST` | `/api/v1/reservations/{id}/approve` | `RESOURCE_MANAGER` | Approve a pending reservation |
| `POST` | `/api/v1/reservations/{id}/reject` | `RESOURCE_MANAGER` | Reject a pending reservation (reason required) |
| `POST` | `/api/v1/reservations/{id}/cancel` | Authenticated | Cancel a pending or approved reservation |
| `GET` | `/api/v1/reservations/summary/status` | Authenticated | View status summary count |
| `GET` | `/api/v1/reservations/summary/resources` | Authenticated | View resource utilization summary |
| `GET` | `/api/v1/reservations/summary/resources/{resourceId}/trend` | Authenticated | View daily usage trend for a resource |

---

## 7. Swagger UI & Documentation

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI JSON Spec: `http://localhost:8082/v3/api-docs`

---

## 8. Local Execution & Docker Instructions

### Running Locally with Gradle
```bash
# Run unit & integration tests
./gradlew clean test

# Build application JAR
./gradlew clean build

# Boot application
./gradlew bootRun
```

### Running with Docker Compose
```bash
docker-compose up --build -d
```

---

## 9. Sample Request & Response

### Create Reservation Request
`POST /api/v1/reservations`
```json
{
  "resourceId": "1",
  "requesterId": "student-101",
  "startTime": "2026-10-20T09:00:00",
  "endTime": "2026-10-20T11:00:00",
  "purpose": "Group Project Study Session",
  "expectedAttendees": 15
}
```

### Response
```json
{
  "id": 1,
  "resourceId": "1",
  "requesterId": "student-101",
  "startTime": "2026-10-20T09:00:00",
  "endTime": "2026-10-20T11:00:00",
  "status": "PENDING",
  "purpose": "Group Project Study Session",
  "expectedAttendees": 15,
  "createdAt": "2026-09-26T11:30:00",
  "updatedAt": "2026-09-26T11:30:00"
}
```

---

## 10. Known External Blockers

1. **Group 5 Integration**: Live integration remains BLOCKED until Group 5 identity endpoints are available.
2. **Facility Resource Service**: Service expects Group 6 `facility-resource-service` running at `http://localhost:8081`.
