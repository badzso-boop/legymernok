# LégyMérnök.hu - UX and UI Plan (Spaceship Edition)

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2025-11-29 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document outlines the core user experience (UX) and user interface (UI) concept for the `legymernok.hu` platform, built around a space-travel narrative.

## 1. UX principles: the Cadet's journey

- **Exploration and adventure:** learning isn't a chore, it's an adventure across the cosmos. The user (the "cadet") discovers new star systems while acquiring engineering knowledge.
- **Building and progress:** the cadet builds their own spaceship (their knowledge) from scratch. Every successful mission is a new component, a more advanced system, bringing them closer to the ultimate goal. Progress is accompanied by eye-catching animations and badges.
- **Community experience:** cadets are members of a fleet who help each other. Forums and shared projects can reinforce this further down the line.

## 2. Main screens (ship's logs)

### A) Arriving at the hangar (unauthenticated user)

#### 1. Docking gate (homepage)
*Goal: recruit the cadet, give a glimpse of the promised adventure.*
- **Console (header):** logo, "Star Map", "Dock" (login), "Enlist" (registration) button.
- **Central viewport (hero section):** an eye-catching, friendly animation: Earth, a spaceship under construction next to it, stars in the background. A friendly astronaut character waves.
    - Headline: **"Program your own space voyage!"**
    - Subheadline: "Learn engineering fundamentals through missions, and build the spaceship that will take you to the stars."
    - CTA button: **"Ignite the Engines!"**
- **Training plan ("How does it work?"):**
    1.  **Drafting table:** build the frame of your spaceship (learn the fundamentals).
    2.  **Simulator:** complete missions on Earth and the Moon (solve problems).
    3.  **Interstellar jump:** explore the galaxy (reach more advanced topics).
- **Discoverable star systems (featured courses):** 3-4 cards, with eye-catching star system images.
    - "The Python Nebula" (Python Fundamentals)
    - "The Java Galaxy" (Java in Practice)

#### 2. Star map (courses page)
*Goal: explore the galaxy and the knowledge hidden within it.*
- **Navigation console:** search and filters (e.g. technology: `Python`, `Java`; type: `Spaceship Systems`, `Space Station Management`).
- **Star system list:** courses appear as star systems or galaxies on eye-catching cards.
    - **Card contents:** star system name, short description ("In this system you can produce Python fuel..."), difficulty level (e.g. "Safe zone"), number of missions it contains.

#### 3. Enlist / Dock (registration / login)
*Goal: join the fleet.*
- Themed forms.
    - **Enlist:** Cadet ID, communication channel (email), security code (password)...
    - **Dock:** ID and code to log in.

### B) In the cockpit (authenticated user)

#### 1. Cockpit (dashboard)
*Goal: the cadet's personal control panel, all important information in one place.*
- **Console:** logo, "Navigation", "Star Map", profile icon (dropdown menu: ship's log, log out).
- **Welcome message:** "Welcome aboard, Captain [Cadet Name]!"
- **Current mission:** a large panel showing the most recent mission, with a "Back to the simulator!" button.
- **Navigation targets:** list of star systems already started. The progress bar is styled as a hyperdrive charge indicator.
- **Exploration suggestions:** new star systems recommended by the system.

#### 2. Navigation map (course detail page)
*Goal: an overview of a star system's planets (missions).*
- **Background:** the map of the given star system.
- **Mission chain:** missions appear as planets or asteroids along a route.
    - **A planet (mission) on the map:** number, name ("Planet 1: Analyzing the atmosphere of 'Variables'"). Already-visited planets are colored and carry a small flag (✅). The next planet blinks. Further-away planets are still grey (🔒).

#### 3. Simulator (workspace)
*Goal: the actual learning and coding interface, styled to look like a spaceship console.*
- **Three-pane, futuristic design:**
    1.  **Mission log (description):**
        - The mission's goal, the planet's description.
        - Step-by-step instructions, presented like an official mission order.
    2.  **Command-line interface (IDE):**
        - Embedded code editor with a futuristic frame.
        - Buttons: "Run Simulation", "Diagnostics" (testing).
    3.  **Sensors (output):**
        - Tabs: "Telemetry" (console) and "Diagnostic Report" (tests).
        - **Diagnostic report:** test results, with `SYSTEM OK` (green) or `CRITICAL FAILURE` (red) messages.
        - **On a successful solution:** animation: **"Mission Complete! Hyperdrive recharged. Jumping to next coordinates!"**. The "Next" button reads: **"JUMP!"**

## 3. UI style guide

- **Color palette:** deep space-blue and black background. Vivid neon colors (cyan, magenta, green) for text, buttons, and active elements, as if projected as a hologram.
- **Typography:** a modern, sci-fi-flavored but well-readable typeface (e.g. Orbitron, Space Grotesk). Monospace for code (Fira Code).
- **Icons and animations:** clean, futuristic icons. Subtle animations: pulsing lights, scanning lines, the ship moving across the map.
