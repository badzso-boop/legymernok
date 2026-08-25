# LégyMérnök.hu - API Specification and Backend Architecture

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2025-11-29 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document describes the backend's layered structure and the specification of its API endpoints.

## 1. Backend Architecture (Layers)

The backend is built on Java / Spring Boot, strictly following **Service-Oriented Architecture** principles for the sake of scalability and testability.

### Layers

1.  **Controller Layer (`web` package)**
    *   **Responsibility:** receiving HTTP requests, validation (at the DTO level), sending responses.
    *   **Rule:** contains no business logic. Only talks to the Service layer.
    *   *Example:* `AuthController`, `UserController`.

2.  **Service Layer (`service` package)**
    *   **Responsibility:** the home of the business logic. Transaction management. This is where decisions get made.
    *   **Orchestration:** this layer calls both the database repositories AND the external integrations (e.g. Gitea).
    *   *Example:* `UserService` (calls both `UserRepository` and `GiteaService`).

3.  **Integration Layer (`integration` package)**
    *   **Responsibility:** communication with external systems (Gitea, Docker Sandbox).
    *   **Rule:** hides the technical details of calling external APIs (REST calls, auth tokens) from the Service layer.
    *   *Example:* `GiteaClient` (Feign client or WebClient).

4.  **Repository Layer (`repository` package)**
    *   **Responsibility:** direct database operations (Spring Data JPA).
    *   *Example:* `UserRepository`.

5.  **Domain/Model Layer (`model` package)**
    *   **Responsibility:** the database entities and business objects.
    *   *Example:* `Cadet` (Entity).

6.  **DTO Layer (`dto` package)**
    *   **Responsibility:** data transfer between client and server (request/response objects).
    *   *Example:* `RegisterRequest`, `UserResponse`.

---

## 2. User Management API Endpoints

The endpoints below manage users (Cadets and Admins).

### Authentication

#### `POST /api/auth/register`
Registers a new user.
*   **Flow:**
    1.  Validation.
    2.  Create the user in the local database (`cadets` table).
    3.  **Gitea integration:** synchronously or asynchronously creates a Gitea user with the same name/password (or a generated token), and stores the resulting `gitea_user_id`.
*   **Request:**
    ```json
    {
      "username": "skywalker",
      "email": "luke@rebellion.com",
      "password": "StrongPassword123!"
    }
    ```
*   **Response (201 Created):**
    ```json
    {
      "id": "uuid-...",
      "username": "skywalker",
      "role": "CADET",
      "createdAt": "2023-..."
    }
    ```

#### `POST /api/auth/login`
Logs in and issues a JWT token.
*   **Request:**
    ```json
    {
      "username": "skywalker",
      "password": "StrongPassword123!"
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "token": "eyJhbGciOiJIUzI1..."
    }
    ```

### Users

#### `GET /api/users`
Lists all users.
*   **Permission:** `ADMIN` only.
*   **Query params:** `?page=0&size=20` (pagination).
*   **Response (200 OK):**
    ```json
    [
      {
        "id": "uuid-...",
        "username": "skywalker",
        "email": "luke@rebellion.com",
        "role": "CADET",
        "giteaUserId": 42
      },
      ...
    ]
    ```

#### `GET /api/users/{id}`
Fetches the data of a specific user.
*   **Permission:** `ADMIN`, or the requested ID matches the logged-in user.
*   **Response (200 OK):** `UserResponse` object.

#### `GET /api/users/me`
Fetches the currently logged-in user's own data.
*   **Permission:** any logged-in user.
*   **Response (200 OK):** `UserResponse` object.

#### `PUT /api/users/{id}`
Updates a user's data.
*   **Permission:** `ADMIN` (can edit anyone) or the user themself (can only edit their own account).
*   **Note:** if the password or email changes, the **Gitea** account must also be updated in the background!
*   **Request:**
    ```json
    {
      "email": "new-email@rebellion.com",
      "avatarUrl": "..."
    }
    ```
*   **Response (200 OK):** the updated `UserResponse`.

#### `DELETE /api/users/{id}`
Deletes or archives a user.
*   **Permission:** `ADMIN` only.
*   **Flow:**
    1.  Soft delete in the local database (or set status to 'INACTIVE').
    2.  **Gitea integration:** lock or delete the Gitea user (depending on whether we want to keep their code).
*   **Response (204 No Content)**

---

## 3. Further Planned API Groups

The following endpoint groups will be fleshed out in later milestones:

*   **Course Management (`/api/courses` / `/api/star-systems`):** creating and editing courses (Admin).
*   **Mission Control (`/api/missions`):** mission details, starting a mission.
    *   *Start Mission:* creates a repo on Gitea.
*   **Submission & Testing (`/api/submissions`):**
    *   *Submit:* receives a webhook from Gitea, or a manual trigger.
    *   This triggers the Docker runner.
