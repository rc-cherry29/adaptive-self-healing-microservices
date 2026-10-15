# NRDA Control Center — React + Tailwind

A live dashboard for the existing Adaptive Self-Healing Framework for Microservices.
No mock experiment data: all numbers come from the existing Spring Boot Monitoring Service + MySQL.

## Project structure

```text
nrda-frontend/
├── backend/
│   ├── NRDADashboardController.java   # copy into Monitoring Service package
│   └── README.md                       # exact Spring Boot integration
├── src/
│   ├── App.jsx                         # dashboard, charts, fault lab, history
│   ├── api.js                          # REST calls
│   ├── index.css                       # Tailwind 4 and visual styles
│   └── main.jsx                        # React entry
├── index.html
├── package.json
├── vite.config.js                     # /api proxy to localhost:8083
└── README.md
```

## Quick start on Windows PowerShell

Prerequisite: Node.js 20.19+ or 22.12+; Java Spring Boot Monitoring Service on 8083.

1. Unzip this folder anywhere convenient (for example next to your Spring Boot project).
2. Copy `backend/NRDADashboardController.java` into:
   `monitoring-service/src/main/java/com/selfhealing/monitoring_service/controller/NRDADashboardController.java`
3. Follow `backend/README.md` for JDBC dependency and properties.
4. Restart Monitoring Service in IntelliJ.
5. From this frontend folder run:

```powershell
npm install
npm run dev
```

6. Open http://localhost:5173

### Verify the backend first

```powershell
Invoke-RestMethod http://localhost:8083/api/dashboard/summary
Invoke-RestMethod http://localhost:8083/api/dashboard/experiments?limit=3
Invoke-RestMethod http://localhost:8083/api/dashboard/analytics
Invoke-RestMethod http://localhost:8083/api/dashboard/services
```

### Fault lab (local demos only)

Fault controls default to OFF. To enable for a controlled demonstration, add to Monitoring Service's `application.properties`:

```properties
dashboard.fault-controls-enabled=true
```

Restart Monitoring Service. The controller only accepts fault-control calls from localhost.
Disable again after the demo.

### Important data interpretation

- SERVICE_DOWN rows are generally per incident; SLOW_RESPONSE rows may be per attempt.
- 0 ms gateway timing means the gateway was available at the first probe, not zero downtime.
- Missing gateway measurements are NULL and must not be treated as zero.
- `REACHABLE` service status means an HTTP response was received without verified actuator health.
- The frontend does not modify `recovery.decision-mode`; change that property and restart the Monitoring Service to switch modes.
