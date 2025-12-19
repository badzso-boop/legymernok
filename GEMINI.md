# LegyMernok.hu Project Context

## Project Overview
**LegyMernok.hu** is a gamified educational platform designed to teach engineering skills (software development, electronics) through a space-themed narrative. The system manages student code submissions and task templates using a self-hosted Gitea instance.

### key Technologies
- **Backend:** Java 17, Spring Boot 3.x (Web, Data JPA, Security), Maven.
- **Frontend:** React 19, Vite, TypeScript, Tailwind CSS, Material UI.
- **Database:** PostgreSQL 16.
- **Version Control System (Internal):** Gitea (Self-hosted).
- **Infrastructure:** Docker & Docker Compose.

## Architecture
The project follows a monorepo structure with services containerized via Docker.
- **Frontend:** Runs on port `3000` (host).
- **Backend:** Runs on port `8080` (host), communicates with Gitea and Postgres on the internal Docker network (`legymernok-net`).
- **Gitea:** Runs on port `3001` (host web UI), internal port `3000`.
- **Postgres:** Runs on port `5432`.

## Building and Running

### Full Stack (Docker)
To start the entire environment (Database, Gitea, Backend, Frontend):
```bash
docker compose up --build
```
*Note: Ensure ports 3000, 3001, 8080, and 5432 are free.*

### Backend (Local Development)
Located in `backend/`.
```bash
# Build
./mvnw clean install

# Run
./mvnw spring-boot:run

# Test
./mvnw test
```

### Frontend (Local Development)
Located in `frontend/`.
```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

## Development Conventions

### Backend
- **Structure:** Standard Spring Boot layered architecture (`Controller` -> `Service` -> `Repository` -> `Model`).
- **DTOs:** Use Data Transfer Objects (DTOs) for all API requests and responses (e.g., `CreateGiteaUserRequest`).
- **Gitea Integration:** The `GiteaService` handles all interactions with the Gitea API (user creation, repo management).
- **Security:** Stateless JWT authentication.

### Frontend
- **Framework:** React with Functional Components and Hooks.
- **Styling:** Tailwind CSS and Material UI.
- **State Management:** React Query (TanStack Query) for API data.

### Infrastructure & Networking
- **Internal Network:** Services communicate via the `legymernok-net` Docker network.
- **Hostnames:**
    - Use `gitea:3000` for backend-to-gitea communication within Docker.
    - Use `localhost:3001` for host-to-gitea communication (e.g., browser, manual curl).

## Current Context & Troubleshooting
- **User Preference:** The user prefers to edit files and run commands manually. Provide code snippets and instructions rather than applying changes directly unless asked.
- **Active Issue:** Troubleshooting Gitea user creation (`PasswordIsRequired` 400 Bad Request) in `GiteaService`.
    - **Status:** `curl` from within the backend container works, but the Java `RestClient` implementation fails.
    - **Workaround:** Using `ProcessBuilder` to call `curl` from Java is a temporary solution to bypass potential serialization/client issues.
