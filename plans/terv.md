# LégyMérnök.hu - Development Plan (Master Plan)

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2025-12-13 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document contains the project's finalized development timeline and technical milestones, merging the initial vision with the Gitea-based architecture that was actually implemented.

## 1. Technology stack
*   **Backend:** Java 17, Spring Boot 3.x (Web, Data JPA, Security)
*   **Database:** PostgreSQL 16
*   **Code storage:** Gitea (self-hosted Git server) - *central piece for managing code.*
*   **Frontend:** React (Vite, TypeScript), Tailwind CSS / Bootstrap
*   **Containerization:** Docker, Docker Compose
*   **CI/CD:** GitHub Actions (planned)

---

## 2. Architecture and security model

The system's central component is **Gitea**, which manages both the course material (template repos) and the students' solutions.

*   **Admin template repo:** private repository containing the exercise skeleton (public) and the solutions/tests (hidden).
*   **Student repo:** private repository created at the moment "Start Mission" is triggered. Contains the skeleton, but **not** the solutions.
*   **Runner service:** isolated Docker environment that, during evaluation, merges the student repo's code with the admin repo's hidden tests.

---

## 3. Development timeline (milestones)

### Milestone 0: Foundations and infrastructure (DONE)
*Goal: set up a stable development environment.*
*   [x] **Project initialization:** monorepo (`backend`, `frontend`), `docker-compose.yml`.
*   [x] **Database:** provisioning and initializing the PostgreSQL and Gitea containers.
*   [x] **Backend foundations:**
    *   Layered architecture (Controller, Service, Repository, Model).
    *   `GiteaService`: creating users via the Gitea API.
    *   Security: Spring Security, BCrypt, JWT token-based authentication.
    *   User management (registration, login, Gitea sync).
*   [x] **Data model v1:** `StarSystem` (course) and `Mission` (lesson) entities and CRUD API.
*   [x] **Frontend foundations:** React + Vite + TypeScript scaffold.

### Milestone 1: Content management and administration (IN PROGRESS)
*Goal: let instructors (admins) create courses and exercises through a convenient interface.*

#### 1.1. Admin UI (frontend)
*   [ ] **Login:** login page, JWT token handling.
*   [ ] **Course manager:** listing, creating, and editing star systems.
*   [ ] **Mission editor:**
    *   Form for the exercise's data.
    *   **Monaco Editor integration:** built-in code editor where the admin writes the template code.

#### 1.2. Backend automation (Gitea API)
*   [ ] **Repo automation:** extend `GiteaService` to create **private** repositories under the admin user via the API.
*   [ ] **File upload:** extend `GiteaService` to upload files (code, description).
*   [ ] **Mission service:** rework the `createMission` endpoint to accept code instead of a URL, and to handle repo creation and upload behind the scenes.

### Milestone 2: The learner experience (Start Mission)
*Goal: let students start exercises, get their own repo, and code.*

#### 2.1. Start Mission flow (backend)
*   [ ] **Template cloning (Smart Copy):** when a student starts an exercise:
    1.  The system reads the exercise's **admin template repo** with admin privileges.
    2.  Filters out the secret files (solutions, hidden tests).
    3.  Creates a **new, private repo for the student** on Gitea.
    4.  Uploads the filtered starter package (skeleton code) to the student's repo.
*   [ ] **Database:** record the student's repo URL in the `cadet_missions` table.

#### 2.2. Coding (frontend)
*   [ ] **Course browser:** student-facing UI for listing courses and lessons.
*   [ ] **Web IDE:** lets the student edit files in their own Gitea repo from the browser (Monaco Editor + Gitea API commit).

### Milestone 3: Testing and evaluation (Runner)
*Goal: automatically run and verify submitted code.*

*   [ ] **Docker runner service:** a separate Spring Boot (or Go/Python) service that uses the Docker API.
*   [ ] **Testing logic:**
    1.  Runner downloads the student's code.
    2.  Runner downloads the hidden tests from the admin template repo.
    3.  Merges and runs them in a temporary container.
    4.  Returns the result (pass/fail + logs).
*   [ ] **Security:** resource limits (CPU, RAM), timeouts, network isolation.

### Milestone 4: Circuit simulator
*Goal: support electronics exercises.*

*   [ ] **Library:** integrate `circuit-simulator-js` (or similar) as a React component.
*   [ ] **Integration:** store and version the circuit description file (JSON) in the Gitea repo.

### Milestone 5: Community features (Social AI)
*   [ ] AI Tutor integration.
*   [ ] Leaderboards, XP system.

---

## 4. DevOps and CI/CD

*   **GitHub Actions:**
    *   Build & test (backend + frontend).
    *   Docker image build & push.
    *   Deploy (SSH + Docker Compose).
