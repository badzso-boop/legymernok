import type { MissionResponse } from "../types/mission";

/**
 * A route, ahol egy adott típusú misszió önállóan (nem Mission Group-on
 * belül) elindítható — `null`, ha ehhez a típushoz nincs dedikált kadét-oldali
 * lejátszó-útvonal (pl. FILL_IN_BLANK jelenleg csak Mission Group-on belül
 * játszható, ld. FillInBlankView; CIRCUIT_SIMULATION-höz még nincs kadét
 * munkakörnyezet). Egyetlen forrás erre a leképezésre — a StarSystemDetailPage
 * "KEZDD EL" gombja is ezt hívja, ne duplikáld máshol.
 */
export function getMissionPlayPath(mission: MissionResponse): string | null {
  switch (mission.missionType) {
    case "CONTENT":
      return `/play/content/${mission.id}`;
    case "QUIZ":
      return `/play/quiz/${mission.id}`;
    case "CODING":
      return `/play/coding/${mission.id}`;
    default:
      return null;
  }
}

/**
 * Megkeresi egy önálló (nem csoportosított) misszió után a csillagrendszer
 * `orderIndex` szerint következő, önállóan lejátszható missziót — átugorva
 * azokat, amikhez nincs dedikált lejátszó-útvonal (ld. `getMissionPlayPath`),
 * hogy a "Következő" gomb sose vigyen egy törött/nem-támogatott oldalra.
 * `null`-t ad vissza, ha nincs ilyen (az aktuális az utolsó lejátszható a
 * sorban) — ekkor a hívó a csillagrendszer áttekintő oldalára navigál vissza.
 */
export function findNextPlayableMission(
  missions: MissionResponse[],
  currentMissionId: string,
): MissionResponse | null {
  const sorted = [...missions].sort((a, b) => {
    const ai = a.orderIndex ?? Number.MAX_SAFE_INTEGER;
    const bi = b.orderIndex ?? Number.MAX_SAFE_INTEGER;
    return ai - bi;
  });

  const currentIndex = sorted.findIndex((m) => m.id === currentMissionId);
  if (currentIndex === -1) return null;

  for (let i = currentIndex + 1; i < sorted.length; i++) {
    if (getMissionPlayPath(sorted[i]) !== null) {
      return sorted[i];
    }
  }
  return null;
}
