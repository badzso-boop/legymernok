import axios from "axios";
import type {
  CreateMissionInitialRequest,
  MissionForgeContentRequest,
  MissionForgeResponse,
} from "../types/mission-forge";
import type {
  StarSystemResponse,
  StarSystemWithItemsResponse,
  StarSystemSearchResult,
} from "../types/starSystem";
import type { MissionResponse, ContentPageResponse } from "../types/mission";
import type { QuizDefinition, MissionResult } from "../types/quiz";
import type {
  MissionGroupResponse,
  MissionGroupWithMissionsResponse,
  GroupProgressResponse,
  CreateMissionGroupRequest,
  ReorderResponse,
} from "../types/group";
import type {
  FillInBlankUserResponse,
  FillInBlankAdminResponse,
  SaveFillInBlankRequest,
  SubmitFillInBlankRequest,
  FillInBlankResultResponse,
  LastAttemptResponse,
} from "../types/fillinblank";
import type {
  FeatureFlagResponse,
  UpdateFeatureFlagRequest,
} from "../types/featureFlag";
import type {
  FeedbackIssueResponse,
  CreateFeedbackRequest,
} from "../types/feedback";
import type {
  CadetSummaryResponse,
  CadetProfileResponse,
  MeResponse,
} from "../types/social";
import type {
  ContinueResponse,
  ActivityFeedItemResponse,
} from "../types/dashboard";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api",
  headers: {
    "Content-Type": "application/json",
  },
});

// Request interceptor: Minden kérés előtt lefut
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// Response interceptor: Minden válasz után lefut
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Ha 401-et kapunk (lejárt token vagy érvénytelen), kiléptetjük a usert
    if (error.response && error.response.status === 401) {
      localStorage.removeItem("token");
      // Itt lehetne egy window.location.href = '/login' is,
      // de elegánsabb, ha az AuthContext kezeli.
    }
    return Promise.reject(error);
  },
);

export default apiClient;

export const forgeApi = {
  /**
   * Inicializál egy új missziót a Mission Forge-on keresztül.
   * @param data A misszió létrehozására vonatkozó kérés adatai.
   * @returns Az újonnan létrehozott MissionForgeResponse.
   */
  initializeMission: async (data: CreateMissionInitialRequest) => {
    const response = await apiClient.post<MissionForgeResponse>(
      "/missions/forge/initialize",
      data,
    );
    return response.data;
  },

  /**
   * Lekéri egy adott misszió fájltartalmát a Giteából.
   * @param missionId A misszió azonosítója.
   * @returns Egy térkép, ahol a kulcsok fájlnevek, az értékek pedig fájltartalmak.
   */
  getMissionFiles: async (missionId: string) => {
    const response = await apiClient.get<Record<string, string>>(
      `/missions/${missionId}/forge/files`,
    );
    return response.data;
  },

  /**
   * Ment (feltölt/frissít) fájltartalmakat egy adott misszióhoz a Giteába.
   * @param missionId A misszió azonosítója.
   * @param data A kérés, amely tartalmazza a misszió azonosítóját és a fájltartalmakat.
   * @returns A frissített MissionForgeResponse.
   */
  saveMissionFiles: async (
    missionId: string,
    data: MissionForgeContentRequest,
  ) => {
    const response = await apiClient.post<MissionForgeResponse>(
      `/missions/${missionId}/forge/save`,
      data,
    );
    return response.data;
  },

  /** Új (üres) fájlt hoz létre egy misszió saját (template) repójában. */
  createFile: async (missionId: string, path: string) => {
    const response = await apiClient.post<MissionForgeResponse>(
      `/missions/${missionId}/forge/files`,
      { path },
    );
    return response.data;
  },

  /** Töröl egy fájlt egy misszió saját (template) repójából. */
  deleteFile: async (missionId: string, path: string) => {
    await apiClient.delete(`/missions/${missionId}/forge/files`, {
      params: { path },
    });
  },

  /** Átnevez egy fájlt egy misszió saját (template) repójában. */
  renameFile: async (missionId: string, oldPath: string, newPath: string) => {
    await apiClient.patch(`/missions/${missionId}/forge/files/rename`, {
      oldPath,
      newPath,
    });
  },

  /**
   * Lekéri a jelenlegi felhasználó tulajdonában lévő csillagrendszereket.
   * @returns StarSystemResponse objektumok listája.
   */
  getMyStarSystems: async () => {
    const response = await apiClient.get<StarSystemResponse[]>(
      "/star-systems/my-systems",
    );
    return response.data;
  },

  getMissionById: async (id: string) => {
    const response = await apiClient.get<MissionForgeResponse>(
      `/missions/${id}`,
    );
    return response.data;
  },

  /**
   * Lekéri a jelenlegi felhasználó saját misszióit.
   */
  getMyMissions: async () => {
    const response = await apiClient.get<MissionResponse[]>(
      "/missions/my-missions",
    );
    return response.data;
  },

  getContentPage: async (missionId: string, page: number, pageSize = 100) => {
    const response = await apiClient.get<ContentPageResponse>(
      `/missions/${missionId}/content?page=${page}&pageSize=${pageSize}`,
    );
    return response.data;
  },
};

export const starSystemApi = {
  create: async (data: {
    name: string;
    description: string;
    iconUrl?: string;
  }) => {
    const response = await apiClient.post<StarSystemResponse>(
      "/star-systems",
      data,
    );
    return response.data;
  },

  getWithItems: async (id: string) => {
    const response = await apiClient.get<StarSystemWithItemsResponse>(
      `/star-systems/${id}/with-missions`,
    );
    return response.data;
  },

  reorderItems: async (
    systemId: string,
    item1Id: string,
    item1Type: "MISSION" | "GROUP",
    item2Id: string,
    item2Type: "MISSION" | "GROUP",
  ): Promise<ReorderResponse> => {
    const response = await apiClient.post<ReorderResponse>(
      `/star-systems/${systemId}/reorder-items`,
      { item1Id, item1Type, item2Id, item2Type },
    );
    return response.data;
  },
};

export const missionApi = {
  /**
   * Elindítja (vagy folytatja) a missziót a bejelentkezett kadét számára —
   * létrehozza (vagy visszaadja a már meglévő) kadét-specifikus Gitea repót.
   * @returns A kadét saját repójának URL-je.
   */
  start: async (missionId: string): Promise<string> => {
    const response = await apiClient.post<string>(
      `/missions/${missionId}/start`,
    );
    return response.data;
  },

  /** Lekéri a bejelentkezett kadét saját munkarepójának fájljait egy elindított CODING misszióhoz. */
  getPlayFiles: async (missionId: string) => {
    const response = await apiClient.get<Record<string, string>>(
      `/missions/${missionId}/play/files`,
    );
    return response.data;
  },

  /** Elmenti (batch) a kadét saját munkarepójának fájltartalmait. */
  savePlayFiles: async (missionId: string, files: Record<string, string>) => {
    await apiClient.put(`/missions/${missionId}/play/files`, files);
  },

  /** Új (üres) fájlt hoz létre a kadét saját munkarepójában. */
  createPlayFile: async (missionId: string, path: string) => {
    await apiClient.post(`/missions/${missionId}/play/files`, { path });
  },

  /** Töröl egy fájlt a kadét saját munkarepójából. */
  deletePlayFile: async (missionId: string, path: string) => {
    await apiClient.delete(`/missions/${missionId}/play/files`, {
      params: { path },
    });
  },

  /** Átnevez egy fájlt a kadét saját munkarepójában. */
  renamePlayFile: async (
    missionId: string,
    oldPath: string,
    newPath: string,
  ) => {
    await apiClient.patch(`/missions/${missionId}/play/files/rename`, {
      oldPath,
      newPath,
    });
  },
};

export const quizApi = {
  // Kvíz indítása vagy folytatása
  startQuiz: async (missionId: string): Promise<QuizDefinition> => {
    const response = await apiClient.post<QuizDefinition>(
      `/quiz/${missionId}/start`,
    );
    return response.data;
  },

  // Részeredmények szinkronizálása a háttérben
  syncProgress: async (
    missionId: string,
    answers: Record<string, string[]>,
  ): Promise<void> => {
    await apiClient.put(`/quiz/${missionId}/sync`, answers);
  },

  // Végleges beküldés és javítás
  submitQuiz: async (
    missionId: string,
    answers: Record<string, string[]>,
  ): Promise<MissionResult> => {
    const response = await apiClient.post<MissionResult>(
      `/quiz/${missionId}/submit`,
      answers,
    );
    return response.data;
  },

  // (Opcionális) Korábbi eredmények lekérése
  getResults: async (missionId: string): Promise<MissionResult[]> => {
    const response = await apiClient.get<MissionResult[]>(
      `/quiz/${missionId}/results`,
    );
    return response.data;
  },

  clearSessions: async (missionId: string): Promise<void> => {
    await apiClient.delete(`/quiz/${missionId}/sessions`);
  },
};

export const missionGroupApi = {
  create: async (
    data: CreateMissionGroupRequest,
  ): Promise<MissionGroupResponse> => {
    const response = await apiClient.post<MissionGroupResponse>(
      "/mission-groups",
      data,
    );
    return response.data;
  },

  getById: async (id: string): Promise<MissionGroupWithMissionsResponse> => {
    const response = await apiClient.get<MissionGroupWithMissionsResponse>(
      `/mission-groups/${id}`,
    );
    return response.data;
  },

  update: async (
    id: string,
    data: Partial<CreateMissionGroupRequest>,
  ): Promise<MissionGroupResponse> => {
    const response = await apiClient.put<MissionGroupResponse>(
      `/mission-groups/${id}`,
      data,
    );
    return response.data;
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/mission-groups/${id}`);
  },

  addMission: async (
    groupId: string,
    missionId: string,
  ): Promise<MissionGroupResponse> => {
    const response = await apiClient.post<MissionGroupResponse>(
      `/mission-groups/${groupId}/missions/${missionId}`,
    );
    return response.data;
  },

  removeMission: async (groupId: string, missionId: string): Promise<void> => {
    await apiClient.delete(`/mission-groups/${groupId}/missions/${missionId}`);
  },

  reorderGroup: async (
    groupId: string,
    targetGroupId: string,
  ): Promise<ReorderResponse> => {
    const response = await apiClient.post<ReorderResponse>(
      `/mission-groups/${groupId}/reorder/${targetGroupId}`,
    );
    return response.data;
  },

  reorderMissionInGroup: async (
    groupId: string,
    missionId: string,
    targetMissionId: string,
  ): Promise<ReorderResponse> => {
    const response = await apiClient.post<ReorderResponse>(
      `/mission-groups/${groupId}/missions/${missionId}/reorder/${targetMissionId}`,
    );
    return response.data;
  },
};

export const groupProgressApi = {
  get: async (groupId: string): Promise<GroupProgressResponse> => {
    const response = await apiClient.get<GroupProgressResponse>(
      `/group-progress/${groupId}`,
    );
    return response.data;
  },

  start: async (groupId: string): Promise<GroupProgressResponse> => {
    const response = await apiClient.post<GroupProgressResponse>(
      `/group-progress/${groupId}/start`,
    );
    return response.data;
  },

  completeStep: async (groupId: string): Promise<GroupProgressResponse> => {
    const response = await apiClient.post<GroupProgressResponse>(
      `/group-progress/${groupId}/complete-step`,
    );
    return response.data;
  },
};

export const searchApi = {
  searchStarSystems: async (
    q: string,
    limit = 5,
  ): Promise<StarSystemSearchResult[]> => {
    const response = await apiClient.get<StarSystemSearchResult[]>(
      `/search?q=${encodeURIComponent(q)}&limit=${limit}`,
    );
    return response.data;
  },

  getEmbeddingStatus: async (
    starSystemId: string,
  ): Promise<{ hasEmbedding: boolean }> => {
    const response = await apiClient.get<{ hasEmbedding: boolean }>(
      `/star-systems/${starSystemId}/embedding-status`,
    );
    return response.data;
  },

  embedStarSystem: async (
    starSystemId: string,
  ): Promise<{ hasEmbedding: boolean }> => {
    const response = await apiClient.post<{ hasEmbedding: boolean }>(
      `/star-systems/${starSystemId}/embed`,
    );
    return response.data;
  },

  deleteEmbedding: async (
    starSystemId: string,
  ): Promise<{ hasEmbedding: boolean }> => {
    const response = await apiClient.delete<{ hasEmbedding: boolean }>(
      `/star-systems/${starSystemId}/embed`,
    );
    return response.data;
  },
};

interface ChatContextPayload {
  currentPage: string;
  pageType: string;
  formFields: Record<string, string>;
  language: string;
}

interface ChatResponsePayload {
  response: string;
  action?: { type: string; fields: Record<string, string> };
}

export const chatApi = {
  send: async (
    message: string,
    context: ChatContextPayload,
  ): Promise<ChatResponsePayload> => {
    const response = await apiClient.post<ChatResponsePayload>("/chat", {
      message,
      context,
    });
    return response.data;
  },
};

export const fillInBlankApi = {
  save: async (
    missionId: string,
    data: SaveFillInBlankRequest,
  ): Promise<FillInBlankAdminResponse> => {
    const response = await apiClient.post<FillInBlankAdminResponse>(
      `/missions/${missionId}/fill-in-blank`,
      data,
    );
    return response.data;
  },

  update: async (
    missionId: string,
    data: SaveFillInBlankRequest,
  ): Promise<FillInBlankAdminResponse> => {
    const response = await apiClient.put<FillInBlankAdminResponse>(
      `/missions/${missionId}/fill-in-blank`,
      data,
    );
    return response.data;
  },

  getForAdmin: async (missionId: string): Promise<FillInBlankAdminResponse> => {
    const response = await apiClient.get<FillInBlankAdminResponse>(
      `/missions/${missionId}/fill-in-blank/admin`,
    );
    return response.data;
  },

  getForUser: async (missionId: string): Promise<FillInBlankUserResponse> => {
    const response = await apiClient.get<FillInBlankUserResponse>(
      `/missions/${missionId}/fill-in-blank`,
    );
    return response.data;
  },

  submit: async (
    missionId: string,
    data: SubmitFillInBlankRequest,
  ): Promise<FillInBlankResultResponse> => {
    const response = await apiClient.post<FillInBlankResultResponse>(
      `/missions/${missionId}/fill-in-blank/submit`,
      data,
    );
    return response.data;
  },

  getLastAttempt: async (missionId: string): Promise<LastAttemptResponse> => {
    const response = await apiClient.get<LastAttemptResponse>(
      `/missions/${missionId}/fill-in-blank/last-attempt`,
    );
    return response.data;
  },
};

export const featureFlagApi = {
  /** Admin: összes feature flag listázása kezeléshez (feature_flag:read szükséges). */
  getAll: async (): Promise<FeatureFlagResponse[]> => {
    const response =
      await apiClient.get<FeatureFlagResponse[]>("/feature-flags");
    return response.data;
  },

  /** Bármely bejelentkezett felhasználó lekérheti egy flag aktuális értékét. */
  getByKey: async (key: string): Promise<FeatureFlagResponse> => {
    const response = await apiClient.get<FeatureFlagResponse>(
      `/feature-flags/${key}`,
    );
    return response.data;
  },

  /** Admin: flag engedélyezés/tiltás + leírás módosítása (feature_flag:write szükséges). */
  update: async (
    key: string,
    data: UpdateFeatureFlagRequest,
  ): Promise<FeatureFlagResponse> => {
    const response = await apiClient.put<FeatureFlagResponse>(
      `/feature-flags/${key}`,
      data,
    );
    return response.data;
  },
};

export const authApi = {
  /** A bejelentkezett kadét saját adatai (id, streak, téma-preferencia stb.). */
  getMe: async (): Promise<MeResponse> => {
    const response = await apiClient.get<MeResponse>("/auth/me");
    return response.data;
  },
};

export const socialApi = {
  getProfile: async (cadetId: string): Promise<CadetProfileResponse> => {
    const response = await apiClient.get<CadetProfileResponse>(
      `/cadets/${cadetId}/profile`,
    );
    return response.data;
  },

  follow: async (cadetId: string): Promise<void> => {
    await apiClient.post(`/cadets/${cadetId}/follow`);
  },

  unfollow: async (cadetId: string): Promise<void> => {
    await apiClient.delete(`/cadets/${cadetId}/follow`);
  },

  getFollowing: async (cadetId: string): Promise<CadetSummaryResponse[]> => {
    const response = await apiClient.get<CadetSummaryResponse[]>(
      `/cadets/${cadetId}/following`,
    );
    return response.data;
  },

  getFollowers: async (cadetId: string): Promise<CadetSummaryResponse[]> => {
    const response = await apiClient.get<CadetSummaryResponse[]>(
      `/cadets/${cadetId}/followers`,
    );
    return response.data;
  },

  search: async (username: string): Promise<CadetSummaryResponse[]> => {
    const response = await apiClient.get<CadetSummaryResponse[]>(
      "/cadets/search",
      { params: { username } },
    );
    return response.data;
  },

  /** A követett kadétok legutóbbi teljesítései, időrendben csökkenő sorrendben. */
  getActivityFeed: async (): Promise<ActivityFeedItemResponse[]> => {
    const response = await apiClient.get<ActivityFeedItemResponse[]>(
      "/social/activity-feed",
    );
    return response.data;
  },
};

export const dashboardApi = {
  /** A kadét legutóbb érintett missziója/csoportja — a "Folytasd onnan" kártyához. 404, ha nincs még aktivitás. */
  getContinue: async (): Promise<ContinueResponse> => {
    const response = await apiClient.get<ContinueResponse>(
      "/dashboard/continue",
    );
    return response.data;
  },
};

export const feedbackApi = {
  list: async (): Promise<FeedbackIssueResponse[]> => {
    const response = await apiClient.get<FeedbackIssueResponse[]>("/feedback");
    return response.data;
  },

  submit: async (
    data: CreateFeedbackRequest,
  ): Promise<FeedbackIssueResponse> => {
    const response = await apiClient.post<FeedbackIssueResponse>(
      "/feedback",
      data,
    );
    return response.data;
  },
};
