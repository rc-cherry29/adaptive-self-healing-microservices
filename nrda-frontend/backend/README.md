# Integrate with your existing Monitoring Service

## Step A — Copy the Java controller

Copy `NRDADashboardController.java` into:

```text
monitoring-service/
  src/main/java/com/selfhealing/monitoring_service/controller/
    NRDADashboardController.java
```

The package is `com.selfhealing.monitoring_service.controller` and is automatically scanned by the existing Spring Boot application if its main class is under `com.selfhealing.monitoring_service`.

## Step B — Ensure JDBC support

The controller uses `JdbcTemplate` and your **existing** MySQL datasource.
If `JdbcTemplate` is unresolved in IntelliJ, add the Spring JDBC starter to `monitoring-service/pom.xml` inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

Then use Maven Reload. If already present (or a JDBC bean exists), do not duplicate it.

## Step C — Properties

In `monitoring-service/src/main/resources/application.properties`, retain your current MySQL settings and add:

```properties
recovery.decision-mode=NRDA
dashboard.fault-controls-enabled=false
```

For a local live demo, set `dashboard.fault-controls-enabled=true`, restart the Monitoring Service, and turn it off afterward. This is intentionally not a production-ready fault control endpoint.

## Step D — Restart the Monitoring Service

Check these URLs in a browser or PowerShell:

```powershell
Invoke-RestMethod http://localhost:8083/api/dashboard/summary
Invoke-RestMethod http://localhost:8083/api/dashboard/experiments?limit=3
Invoke-RestMethod http://localhost:8083/api/dashboard/analytics
Invoke-RestMethod http://localhost:8083/api/dashboard/services
```

## API contract

| Method | Endpoint | Description |
| --- | --- | --- |
| GET | /api/dashboard/summary | Total, verified successes, NRDA count, configured mode |
| GET | /api/dashboard/experiments?limit=150 | Recent rows from experiment_results |
| GET | /api/dashboard/analytics | Grouped averages and measurement coverage |
| GET | /api/dashboard/services | Live HTTP/actuator probes of four services |
| POST | /api/dashboard/faults/slow/enable | Enable Product slow fault; local + property gate |
| POST | /api/dashboard/faults/slow/disable | Disable Product slow fault; local + property gate |

## Troubleshooting

- `404 /api/dashboard/...`: controller is in the wrong package or Monitoring Service was not restarted.
- `500 /api/dashboard/...`: verify the MySQL datasource, experiment_results table and schema columns.
- `No qualifying bean of type JdbcTemplate`: add JDBC starter and reload Maven.
- `Vite 5173 loads but says Cannot load monitoring API`: confirm Monitoring Service is listening on port 8083.
- `403` when clicking fault buttons: intentionally disabled; enable only for local testing.
- Product Service `TIMEOUT` while slow fault is active is expected for a short HTTP probe.
