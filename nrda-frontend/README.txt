NRDA LIVE ARCHITECTURE UPGRADE — FRONTEND ONLY

This patch is for your already working nrda-frontend project.
It preserves your existing dashboard controller and Spring Boot services.

FAST INSTALL (Windows PowerShell):
1. Stop nothing: Vite hot-reloads when files change.
2. Extract this ZIP directly into:
   C:\Users\ramch\OneDrive\Desktop\eureka-server\nrda-frontend
   Choose Replace files if asked.
3. In the existing dashboard browser tab, click Architecture in the left sidebar.
4. If Vite isn't running, in the frontend folder run:
   npm run dev -- --port 5174
   If port 5174 is already in use, open http://localhost:5174 instead of starting a second Vite process.

WHAT IT SHOWS
- Real Product, Order, Eureka and Gateway health from GET /api/dashboard/services
- Configured mode and stored experiment count from GET /api/dashboard/summary
- Last recorded experiment from GET /api/dashboard/experiments
- Visual animated logical connections (NOT measured network traffic)
- 6-second highlights for NEW experiment rows seen after the page has loaded
- REACHABLE means HTTP responds, NOT a verified Actuator UP health state

NO BACKEND CHANGES REQUIRED. NO NEW NPM DEPENDENCIES.

Test: open http://localhost:5174, choose Architecture, optionally run
  npm run build
in the frontend folder.
