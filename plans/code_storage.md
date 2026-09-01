# Storing User Code: Dedicated Git Server Implementation

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2025-11-29 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document details the implementation plan for the Git-based solution chosen for storing user code.

## 1. Chosen Technology: Gitea

The system will use a dedicated, self-hosted Git server. The proposed technology is **Gitea**.

**Rationale:**
- **Lightweight:** written in Go, needs substantially fewer resources than, say, GitLab.
- **Simple to operate:** runs easily as a Docker container and slots straight into our existing `docker-compose.yml` setup.
- **Full-featured API:** every function we need (user and repo management, file operations) is available through its REST API, which is essential for our backend.

## 2. Architecture and Flows

### 2.1. Infrastructure (`docker-compose.yml`)

`docker-compose.yml` will be extended with a new `gitea` service:

```yaml
services:
  # ... existing backend, frontend, db services

  gitea:
    image: gitea/gitea:latest
    container_name: legymernok-gitea
    environment:
      - USER_UID=1000
      - USER_GID=1000
      - GITEA__database__DB_TYPE=postgres
      - GITEA__database__HOST=legymernok-db:5432
      - GITEA__database__NAME=gitea
      - GITEA__database__USER=gitea
      - GITEA__database__PASSWD=gitea_secret
      # ... other Gitea settings
    restart: always
    networks:
      - legymernok-network
    volumes:
      - ./gitea-data:/data
    ports:
      - "3000:3000" # Gitea web UI
      - "2222:22"   # Gitea SSH
```
**Task:** a separate `gitea` database and user need to be created on the `legymernok-db` Postgres server.

### 2.2. User Management and Synchronization

`legymernok` users and Gitea users need to stay in sync.

1.  **Admin token:** after installing Gitea, an administrator user must be created manually, and an API token generated. `legymernok-backend` will use this token to perform administrative operations on Gitea (e.g. creating users).
2.  **Registration:** when a new cadet registers on `legymernok.hu`:
    a. `legymernok-backend` successfully saves the new `cadets` record to the database.
    b. Right after that, the backend calls the Gitea API's `/api/v1/admin/users` endpoint to create the user on Gitea too (with a random password, since the cadet never needs to log into the Gitea UI directly).
    c. The backend stores the **numeric user ID** returned by Gitea in the `cadets` table's `gitea_user_id` column.

### 2.3. Starting a Mission: Creating the Repository

1.  The cadet clicks the "Start" button on a mission in the frontend.
2.  The frontend asks the backend whether the cadet has already started this mission.
3.  If there is no entry yet in the `cadet_missions` table:
    a. The backend calls the Gitea API's `/api/v1/user/repos` endpoint on the cadet's behalf (or as an administrator, specifying the author's `gitea_user_id`).
    b. It creates a **new, private repository**, e.g. named `python-basics-variables`.
    c. The backend **uploads the mission's starter files via the API** (e.g. `main.py`, `README.md`) into the repo. Gitea's file API makes this possible without needing a local `git clone`.
    d. The backend creates a new record in the `cadet_missions` table, storing the `cadet_id`, `mission_id`, the `status` (`IN_PROGRESS`), and the freshly created `repository_url`.
4.  The backend returns the repo URL (and the file contents) to the frontend, which loads the code into the "Simulator".

### 2.4. Coding and Saving: Creating a Commit

The frontend's code editor is **NOT** a full-fledged Git client. The flow is much simpler:

1.  The cadet writes code in the browser.
2.  When the "Diagnostics" button is pressed (or on a periodic auto-save), the frontend sends the **full, current contents of the file(s)** to `legymernok-backend`.
3.  With the received content, the backend **calls Gitea's file-modification API endpoint**, which atomically creates a new commit in the cadet's repo. The commit message might be something like `"Simulator save - 2025-11-28 15:30"`.

### 2.5. Testing: Running the Code in the Sandbox

1.  Once the commit is successfully created, the backend kicks off the testing process.
2.  It spins up an **isolated, temporary Docker container** (the "sandbox").
3.  Inside the container, it runs `git clone <repository_url>` to fetch the latest state of the cadet's code.
4.  The backend looks up the test code(s) belonging to that mission in the `mission_tests` table, and copies them into the appropriate place in the cloned repo inside the sandbox.
5.  Inside the sandbox it runs the test runner command (e.g. `pytest`).
6.  The backend captures the command's output (stdout, stderr) and exit code, and sends it back to the frontend, which displays it in the "Diagnostic Report" tab.
7.  Once testing is finished, the sandbox Docker container is stopped and removed.
