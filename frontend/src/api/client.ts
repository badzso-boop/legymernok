import axios from "axios";
import type {
  CreateMissionInitialRequest,
  MissionForgeContentRequest,
  MissionForgeResponse,
} from "../types/mission-forge";
import type { StarSystemResponse } from "../types/starSystem";
import type { MissionResponse } from "../types/mission";
import type { QuizDefinition, MissionResult } from "../types/quiz";

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
};

export const starSystemApi = {
  /**
   * Létrehoz egy új csillagrendszert.
   */
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

  // Összes aktív session törlése (csak misszió tulajdonosa / admin)
  clearSessions: async (missionId: string): Promise<void> => {
    await apiClient.delete(`/quiz/${missionId}/sessions`);
  },
};
