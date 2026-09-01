# Mission Forge Feature Design Document

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-08-15 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

## I. Core Concept

Mission Forge is a user interface that lets cadets create their own custom missions and (in the future) star systems. This turns them into active participants in shaping the game world, letting them put their programming knowledge to use on real projects. The code they create is stored in Gitea repositories, validated through automated testing (Gitea Actions), and builds up a real Gitea portfolio for the user.

## II. User Flow

1.  **Navigation:** The user opens the Mission Forge page.
2.  **Initialization (left-hand configuration panel):**
    - **Selector:** Choose whether a "New Star System" or a "New Mission" is being created. (Initially only "New Mission" will be implemented.)
    - **When creating a new mission:**
      - The user picks their own star system from a dropdown (data from the `GET /api/star-systems/my-systems` call).
      - They enter the mission's name, description, difficulty, type, and order (form fields).
      - They pick a programming language (`JavaScript` or `Python`).
    - **"Initialize Mission" (or "Create Mission") button:** Pressing this triggers the first API call to the backend.
3.  **Editor phase (right-hand Monaco Editor):**
    - Once the backend has successfully initialized the mission and the Gitea repo, the frontend loads the template files (`solution.js`/`.py`, `solution.test.js`/`.py`, `README.md`) from the Gitea repo into the Monaco Editor (`GET /api/missions/{missionId}/forge/files`).
    - The user edits the files.
    - **"Save" button:** saves the modified files back to Gitea (`POST /api/missions/{missionId}/forge/save`).
4.  **Testing and feedback:**
    - After every "Save", a Gitea Action automatically runs the tests in the user's repo.
    - The Gitea Action reports the test results back to the backend (`POST /api/mission-verification/{missionId}/callback`).
    - The frontend shows the user feedback based on the mission's `verificationStatus` field (e.g. `PENDING`, `SUCCESS`, `FAILED`).

## III. Frontend Design and Implementation

1.  **Dependencies:**
    - `@monaco-editor/react`: for the code editor component.
    - `framer-motion`: (already installed) for animations.
2.  **Main component:** `MissionForgePage.tsx`
    - **Layout:** Full-screen, using `RetroUI.css` styles with a metallic, screwed-together frame and a terminal-style background.
    - **Two-column split (`Material UI Grid`):**
      - **Left side (narrower, roughly 1/3 width): `ForgeConfigPanel.tsx`**
        - Top: selector (Star System / Mission).
        - Below: a form for creating the selected entity.
          - `New Mission` form fields: `starSystemId` (dropdown), `name`, `descriptionMarkdown`, `missionType`, `difficulty`, `orderInSystem` inputs.
          - Language selector (`JavaScript` / `Python`).
          - "Initialize Mission" button.
      - **Right side (wider, roughly 2/3 width): `ForgeEditor.tsx`**
        - **Monaco Editor:** code editor.
        - **File handling:** tabs or a dropdown to switch between the `solution.*`, `solution.test.*`, `README.md` files.
        - **Buttons:** a "Save" button to push changes to Gitea.
        - **Status display:** shows the `Mission`'s `verificationStatus` (DRAFT, PENDING, SUCCESS, FAILED) and the result of the last test run, if any.
3.  **API integration:**
    - `POST /api/missions/forge/initialize`: called by `ForgeConfigPanel` to initialize the mission.
    - `GET /api/missions/{missionId}/forge/files`: called by `ForgeEditor` to load the files.
    - `POST /api/missions/{missionId}/forge/save`: called by `ForgeEditor` to save the files.
    - `GET /api/star-systems/my-systems`: called by `ForgeConfigPanel` to list the user's own star systems.
4.  **Localization (i18n):** every UI element is translatable.

## IV. Backend Design and Implementation

1.  **Gitea integration strategy ("Admin-Owned, User-Collaborator"):**
    - Every user-generated Gitea repository stays owned by the **admin user**.
    - The user is added as a `collaborator` with `write` access to the repository.
    - This simplifies CI/CD secrets management and keeps the admin in control.

2.  **Data models and DTOs (`backend/src/main/java/...`):**
    - **`Cadet`:** `getAuthorities()` for fine-grained permission handling.
    - **`Permission` & `Role`:** for the RBAC system.
    - **`Mission`:**
      - `owner: Cadet` field.
      - `verificationStatus: VerificationStatus` field (`DRAFT`, `PENDING`, `SUCCESS`, `FAILED`, `REVIEW_NEEDED`).
      - `templateRepositoryUrl`: the URL of the user-specific Gitea repo owned by the admin.
    - **`StarSystem`:** `owner: Cadet` field.
    - **`CreateMissionInitialRequest`:** DTO for initializing a mission.
    - **`MissionForgeContentRequest`:** DTO for saving file contents.
    - **`MissionResponse`:** extended with `ownerId`, `ownerUsername`, `verificationStatus` fields.

3.  **Service layer (`backend/src/main/java/com/legymernok/backend/service/mission/MissionService.java`):**
    - **`initializeForgeMission(CreateMissionInitialRequest request)`:** initializes the mission (DB record, creating the Gitea repo from a template, adding the user as a collaborator).
    - **`saveForgeMissionContent(MissionForgeContentRequest request)`:** saves the user-edited files to the Gitea repo, sets the mission's `verificationStatus` to `PENDING`.
    - **`getMissionFiles(UUID missionId)`:** fetches the contents of the files in a mission's Gitea repo.
    - **`startMission(UUID missionId, String username)`:** updated to copy content from the **original mission repo** owned by the admin into the cadet's own (admin-owned) repo.
    - **`deleteMission(UUID id)`:** deletes the mission and its Gitea repo (from under the admin account).
    - **`updateMissionVerificationStatus(UUID missionId, VerificationStatus newStatus)`:** updates the mission's status (Gitea Action callback).

4.  **Controller layer (`backend/src/main/java/com/legymernok/backend/web/mission/MissionController.java`):**
    - **`POST /api/missions/forge/initialize`:** endpoint to initialize a mission.
    - **`POST /api/missions/{missionId}/forge/save`:** endpoint to save files.
    - **`GET /api/missions/{missionId}/forge/files`:** endpoint to fetch files.
    - The old `POST /api/missions` endpoint has been removed.

5.  **Gitea integration (`backend/src/main/java/com/legymernok/backend/integration/GiteaService.java`):**
    - **Configuration:** injects the owner and name of the JS and Python template repos from `application.properties`.
    - **`createMissionRepository(String missionIdString, String templateLanguage, Cadet user)`:**
      - Creates an empty repo under the admin account.
      - Picks the matching template repo.
      - Calls `copyRepositoryContents()` (recursive file copy from the template into the new repo).
      - Calls `addCollaborator()` (adds the user with write access).
    - **`uploadFile(String repoOwner, String repoName, String filePath, String content)`:** unified method for uploading/updating files (create or modify).
    - **`copyRepositoryContents(String sourceOwner, String sourceRepoName, String targetOwner, String targetRepoName)`:** recursive method for copying files and folders.
    - **Other methods:** `createGiteaUser`, `deleteGiteaUser`, `createEmptyRepository`, `deleteRepository`, `getRepository`, `getRepoContents`, `getFileContent`, `addCollaborator` (all made more flexible, taking an `owner` parameter where it makes sense).

6.  **CI/CD Gitea Actions:**
    - **In the template repos:** every template repo (JS and Python) contains a `.gitea/workflows/ci.yml` file.
    - **Contents:** checkout, Node/Python setup, install dependencies, run tests, `determine status` (`SUCCESS`/`FAILED`), `send status to backend webhook` (`POST /api/mission-verification/{missionId}/callback`).
    - **`MISSION_ID`:** the Gitea repository name is itself the `Mission UUID`.
    - **Secret:** `MISSION_VERIFICATION_SECRET`, set in Gitea's `Secrets`.

7.  **`MissionVerificationController`:** receives the Gitea Actions callbacks and updates the `Mission`'s `verificationStatus`.
