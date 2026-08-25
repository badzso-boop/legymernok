# Social AI - The Automatic Ship's Log

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2025-11-29 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document contains the plan for an automated content-generation system supporting **LégyMérnök.hu**'s "Building in Public" strategy.

## 1. Concept: "The Onboard Computer Reports"

The goal is to share every major step of development (every commit) with the audience in a transparent but entertaining way. Instead of posting dry changelogs, we tell the project's "story" through the chosen spaceship narrative.

**The AI Persona:**
The posts are written by the system's "Onboard Artificial Intelligence." Its style:
-   **Technical, but enthusiastic:** understands the engineering details, but is excited about the progress.
-   **Narrative:** interprets the code as building a spaceship (e.g. setting up Docker = "Isolating the life-support capsules").

## 2. Workflow

The process is built on a **GitHub Actions** pipeline that only runs on pushes to the `main` branch, and only when the commit message doesn't contain the `[no-social]` flag.

### Steps:

1.  **Trigger:** code push to `main`.
2.  **Extraction:**
    -   The script extracts the commit message and the list of modified files (`git diff --stat`).
    -   Determines the commit type (e.g. `feat`, `fix`, `chore`, `refactor`).
3.  **Text generation (LLM — e.g. Gemini Pro / GPT-4):**
    -   Generates a short (Twitter/LinkedIn) and a long (blog) post text based on the commit data.
    -   Style: "Space Engineer" / "Sci-Fi".
4.  **Image generation (e.g. Gemini / DALL-E 3 / Midjourney):**
    -   Generates a prompt based on the text, then an image from that prompt.
    -   Style: Cyberpunk, Space Opera, Blueprint, Neon.
5.  **Publishing / approval:**
    -   **V1 (Safe):** sends the finished text and image to a private **Discord** channel (via webhook) or a Pull Request comment. The developer posts it manually from there.
    -   **V2 (Automatic):** posts directly to Twitter/LinkedIn via API (recommended only for trusted prompts).

## 3. Prompt Engineering Plans

### A) Text generation (text prompt template)

```text
Role: You are the onboard computer of the LégyMérnök.hu education platform.
Task: Write a social media post about the following code change.
Input (Commit): "{commit_message}"
Changes: "{git_diff_summary}"

Instructions:
1. Use the project's spaceship/sci-fi metaphors (e.g. Backend = Engine, Frontend = Dashboard, Bug = Space debris/Glitch).
2. Be enthusiastic, as if we just installed a new component on the ship.
3. End the post with relevant hashtags (#buildinpublic #java #react #coding).
4. Output format: JSON (twitter_text, linkedin_text, blog_summary).
```

### B) Image generation (image prompt ideas)

The AI needs to visualize the technical change.

| Commit type | Visual metaphor | Image style prompt fragment |
|---|---|---|
| **Database (SQL/Postgres)** | Data crystals, holographic library, server room with cables | `futuristic server room, glowing blue data crystals, isometric view, cyan and magenta lighting` |
| **Frontend (React/UI)** | Spaceship cockpit, hologram projector, HUD (Head-up Display) | `spaceship cockpit view, complex holographic interface, digital dashboard, floating screens, ux design concept` |
| **Backend (Java/Spring)** | Engine, reactor core, engine room, pipes and circuits | `engine room of a starship, glowing energy core, intricate mechanical details, steam and sparks, engineering aesthetic` |
| **Bugfix** | Welding robot, sparks, system restored (green lights) | `robot repairing a hull breach, welding sparks, system diagnostic screen showing 'OK' in green, gritty sci-fi` |
| **CI/CD / Docker** | Robot arms assembling something, containers in space | `automated factory arm assembling a futuristic device, shipping containers floating in zero gravity, organized chaos` |

## 4. Technical Implementation (Milestone 0 addendum)

For this system we'll create a `.github/workflows/social-ai.yml` file and a `scripts/social_generator.py` Python script.

**Required API keys (GitHub Secrets):**
-   `LLM_API_KEY` (for writing the text and prompting the image)
-   `IMAGE_GEN_API_KEY` (if a separate service is needed for the image)
-   `DISCORD_WEBHOOK_URL` (for sending the generated content)

## 5. Example Output

**Commit:** `feat: Add dedicated Docker container for code execution`

**Generated Tweet:**
> 🚀 New entry in the ship's log: the safety simulation chambers (Docker containers) are active! From now on, every cadet's code runs in an isolated space, so the main engine won't blow up if someone writes an infinite loop. 🌌👨‍🚀
>
> #LégyMérnök #DevLog #Docker #SafetyFirst #CodingEducation

**Generated image:**
A futuristic, floating glass capsule in dark space, containing a glowing line of code being scanned by blue lasers.
