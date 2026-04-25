# Pulse-Check-API — "Watchdog" Sentinel

A Spring Boot Dead Man's Switch service for CritMon Servers Inc. Remote devices register a monitor with a countdown timer; if no heartbeat arrives before the countdown reaches zero, the system automatically fires an alert.

---

## Architecture Diagram

```mermaid
flowchart TD
    A([Device Administrator]) -->|POST /monitors| B{Monitor already\nexists?}
    B -->|Yes| C[409 Conflict]
    B -->|No| D[Create Monitor\nstatus = ACTIVE]
    D --> E[Start countdown timer\n— ScheduledExecutorService]
    E --> F([Monitor Running])

    F -->|POST /monitors/{id}/heartbeat| G[Cancel existing timer]
    G --> H[Update lastHeartbeat]
    H --> I[Set status = ACTIVE]
    I --> E

    F -->|POST /monitors/{id}/pause| J[Cancel timer]
    J --> K([status = PAUSED])
    K -->|POST /monitors/{id}/heartbeat| I

    E -->|Countdown reaches 0| L[fireAlert]
    L --> M[Log JSON alert to ERROR log]
    M --> N([status = DOWN])

    F -->|GET /monitors/{id}| O[Return status +\nseconds remaining]
    F -->|GET /monitors| P[Return all monitors]
    F -->|DELETE /monitors/{id}| Q[Cancel timer\nRemove monitor]

    style C fill:#f66,color:#fff
    style N fill:#f66,color:#fff
    style K fill:#f90,color:#fff
    style D fill:#6a6,color:#fff
```

---

## Setup Instructions

### Prerequisites

- Java 17+
- Maven 3.8+

### Run

```bash
cd backend/Pulse-Check
mvn spring-boot:run
```

Server starts on **http://localhost:8081**

### Build executable JAR

```bash
mvn clean package
java -jar target/pulse-check-0.0.1-SNAPSHOT.jar
```

---

## API Documentation

### `POST /monitors`

Registers a new device monitor and starts its countdown timer.

**Request Body:**

```json
{
  "id": "device-123",
  "timeout": 60,
  "alertEmail": "admin@critmon.com"
}
```

| Field        | Type    | Constraints                    |
|--------------|---------|--------------------------------|
| `id`         | string  | Required, unique               |
| `timeout`    | integer | Required, minimum 1 second     |
| `alertEmail` | string  | Required, valid email format   |

**Response — 201 Created:**

```json
{
  "message": "Monitor registered. Countdown started.",
  "monitorId": "device-123",
  "timeout": 60,
  "alertEmail": "admin@critmon.com",
  "status": "ACTIVE"
}
```

---

### `POST /monitors/{id}/heartbeat`

Resets the countdown for the specified monitor. Also un-pauses a paused monitor.

**Response — 200 OK:**

```json
{
  "message": "Heartbeat received. Timer reset.",
  "monitorId": "device-123",
  "status": "ACTIVE",
  "lastHeartbeat": "2024-06-01T12:05:00.000Z",
  "secondsRemaining": 60
}
```

**Response — 404 Not Found:**

```json
{
  "status": 404,
  "error": "Monitor not found: device-123",
  "timestamp": "2024-06-01T12:05:00.000Z"
}
```

---

### `POST /monitors/{id}/pause`

Stops the countdown completely. No alert will fire until a heartbeat is received.

**Response — 200 OK:**

```json
{
  "message": "Monitor paused. No alerts will fire until a heartbeat is received.",
  "monitorId": "device-123",
  "status": "PAUSED"
}
```

---

### `GET /monitors/{id}` *(Developer's Choice)*

Returns the current status and live time remaining for a single monitor.

**Response — 200 OK:**

```json
{
  "id": "device-123",
  "status": "ACTIVE",
  "timeout": 60,
  "alertEmail": "admin@critmon.com",
  "secondsRemaining": 42,
  "lastHeartbeat": "2024-06-01T12:05:00.000Z",
  "expiresAt": "2024-06-01T12:06:00.000Z",
  "createdAt": "2024-06-01T12:00:00.000Z"
}
```

---

### `GET /monitors` *(Developer's Choice)*

Returns all registered monitors.

**Response — 200 OK:**

```json
[
  {
    "id": "device-123",
    "status": "ACTIVE",
    "timeout": 60,
    ...
  }
]
```

---

### `DELETE /monitors/{id}` *(Developer's Choice)*

Deregisters a monitor and cancels its timer cleanly.

**Response — 200 OK:**

```json
{
  "message": "Monitor deregistered successfully.",
  "monitorId": "device-123"
}
```

---

### Alert Payload (logged when countdown reaches zero)

When a device misses its heartbeat, the following JSON is written to the application's ERROR log:

```json
{
  "ALERT": "Device device-123 is down!",
  "time": "2024-06-01T12:06:00.000Z",
  "deviceId": "device-123",
  "alertEmail": "admin@critmon.com"
}
```

---

## Example cURL Commands

**Register a monitor (60-second timeout):**

```bash
curl -X POST http://localhost:8081/monitors \
  -H "Content-Type: application/json" \
  -d '{"id":"device-123","timeout":60,"alertEmail":"admin@critmon.com"}'
```

**Send a heartbeat:**

```bash
curl -X POST http://localhost:8081/monitors/device-123/heartbeat
```

**Pause for maintenance:**

```bash
curl -X POST http://localhost:8081/monitors/device-123/pause
```

**Check live status:**

```bash
curl http://localhost:8081/monitors/device-123
```

**List all monitors:**

```bash
curl http://localhost:8081/monitors
```

**Deregister:**

```bash
curl -X DELETE http://localhost:8081/monitors/device-123
```

---

## Developer's Choice — Monitor Status Inspection & Lifecycle Management

**What was added:**

Three additional endpoints beyond the required specification:

| Endpoint                  | Purpose                                     |
|---------------------------|---------------------------------------------|
| `GET /monitors/{id}`      | Live status: current state + seconds remaining |
| `GET /monitors`           | Dashboard view of all registered monitors   |
| `DELETE /monitors/{id}`   | Clean deregistration — cancels timer, frees resources |

**Why these were added:**

1. **Observability gap:** Without `GET /monitors/{id}`, there is no way to verify a monitor is running or to see how much time is left before an alert fires. Support engineers would be flying blind. The `secondsRemaining` field is calculated live from the scheduled expiry timestamp, so it always reflects the real remaining window.

2. **Operational necessity:** Without `DELETE /monitors/{id}`, monitors can never be cleanly removed. A device that is retired or replaced would leave a zombie monitor that keeps firing alerts forever. The delete endpoint also cancels the underlying `ScheduledFuture` to prevent resource leaks.

3. **Dashboard use-case:** `GET /monitors` enables a simple operational dashboard to be built on top of the API — CritMon staff can see the entire fleet status at a glance.
