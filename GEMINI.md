# Project: LégyMérnök.hu (Be an Engineer.hu)

## Directory Overview

This directory contains the planning and design documents for **LégyMérnök.hu**, a comprehensive, project-based online learning platform. The goal is to teach engineering disciplines, starting with software engineering, through an engaging, space-themed narrative.

The project is currently in the **planning phase**. There is no source code yet. The documents outline the architecture, user experience, database schema, and core functionalities.

## Key Files

This directory serves as the blueprint for the project. The main artifacts are:

1.  **`terv.md` (Development Plan):**
    *   **Purpose:** The master plan for the project.
    *   **Content:** Defines the project's goals, the full technology stack (Java/Spring Boot backend, React frontend, PostgreSQL database, Docker for containerization), and a detailed, milestone-based development roadmap. It also outlines the CI/CD strategy using GitHub Actions.

2.  **`ux_ui_terv.md` (UX/UI Plan):**
    *   **Purpose:** Describes the user experience and interface design.
    *   **Content:** Establishes the core "space travel" narrative. Users are "cadets," courses are "star systems," and lessons are "missions." It details the wireframes for key screens like the "Cockpit" (Dashboard), "Star Map" (Course list), and the three-panel "Simulator" (Workspace for coding).

3.  **`database_schema.md` (Database Schema):**
    *   **Purpose:** Contains the SQL schema for the PostgreSQL database.
    *   **Content:** Provides `CREATE TABLE` statements for the main entities, including `cadets`, `star_systems`, `missions`, and `mission_tests`. The structure is designed to support the Git-based architecture for storing user code.

4.  **`code_storage.md` (Code Storage Architecture):**
    *   **Purpose:** Details the implementation plan for handling user-generated code.
    *   **Content:** Specifies a sophisticated architecture using a self-hosted **Gitea** (a lightweight Git server) instance. It explains the workflow from user registration (creating a parallel Gitea user) to starting a mission (creating a dedicated, private Git repository) and testing the code (cloning the repo into a secure Docker sandbox).

## Usage and Next Steps

These documents are the foundational guide for building the application. The next logical step, as outlined in `terv.md` under **"Mérföldkő 0: Alapok és Infrastruktúra,"** is to begin the actual development by:

1.  Initializing a Git monorepo with `backend` and `frontend` directories.
2.  Creating the initial `docker-compose.yml` file that includes services for the backend, frontend, database, and the **Gitea** server as specified in `code_storage.md`.
3.  Scaffolding the "Hello World" applications for the Spring Boot backend and React frontend.
