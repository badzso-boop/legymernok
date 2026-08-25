# LégyMérnök.hu 2.0 - New Direction (The 2026 Vision)

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-08-15 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document captures the project's new, gamified and mobile-first direction. The goal is an education platform that doesn't look like a boring admin panel, but like the control panel of an interactive spaceship.

---

## 1. Vision and look & feel
*   **Style:** Pixel Art / Retro-Futuristic Sci-Fi.
*   **Platform:** Mobile-First PWA (Progressive Web App). Full-screen experience, as if it were a native app.
*   **Character:** a friendly, 2D pixel-art robot who acts as our helper and narrator.

---

## 2. User flow (the user's journey)

### A) Landing (landing page)
1.  **The encounter:** the user arrives. No boring menu. A pixel-art robot waves.
2.  **The interaction:** a speech bubble above the robot: *"Ready for an adventure?"*.
3.  **The launch:** "Yes" button -> animation: the robot rolls/flies off screen, the camera "enters" the spaceship.

### B) The Command Deck (main menu)
This is the central hub. Not a list, but a graphical dashboard with large, touch-friendly blocks.
*   **Map (navigation):** the main storyline.
*   **Brain teasers (puzzles):** daily logic mini-games (solo missions).
*   **Knowledge base (fun facts):** the "Hard Science" section (math, physics, electronics). This is where deeper knowledge can be gained, rewarded with more XP/badges.
*   **Character (profile):** progress, statistics.

### C) The Galaxy Map (navigation map)
*   **View:** 2D graph (star map).
*   **Nodes:** the star systems.
    *   **Colors:** different types/states (blinking = current, grey = locked, green = complete).
    *   **Connections:** graph edges indicate which one follows which.
*   **Interaction:** tapping -> detail view (mission selector).

### D) The Workshop (electronics)
*   **Concept:** from the virtual to the real.
*   **Simulation:** assembling circuits (battery + resistor + LED = light).
*   **Reality:** blueprints that can be built at home too.
*   **Webshop (long-term):** ordering component kits.

---

## 3. Technical implementation plan

### Frontend (React + Vite)
*   **Animations:** `framer-motion` (transitions, speech bubbles, UI motion).
*   **Graphics:** pixel-art assets (SVG or PNG sprites).
*   **Map:** `react-flow` or D3.js for rendering the graph.
*   **CSS:** Tailwind CSS (grid layout for the Command Deck).
*   **PWA:** `vite-plugin-pwa` for installability and offline mode.

### Backend (Java Spring Boot)
*   **Data model expansion:**
    *   `StarSystem`: `parentId` (prerequisite), `coordinates` (x, y for the map).
    *   `Mission`: additional types (`PUZZLE`, `ELECTRONICS`).
*   **Logic:**
    *   For "coding" exercises, the backend translates the commands (e.g. from blocks) into real code and commits it to Gitea.

---

## 4. Development schedule (roadmap)

1.  **Phase 1: Landing & Command Deck UI** (creating the "wow" factor).
2.  **Phase 2: Galaxy Map** (database expansion + graph visualization).
3.  **Phase 3: Knowledge base & brain teasers** (static content + mini-logic).
4.  **Phase 4: Robot control** (reworking the main mission loop).
