import { z } from "zod";

export const missionEditorBaseSchema = z.object({
  name: z.string().min(3, "validation.nameMinLength"),
  descriptionMarkdown: z.string(),
  difficulty: z.enum(["EASY", "MEDIUM", "HARD", "EXPERT"]),
  missionType: z.enum(["CODING", "CIRCUIT_SIMULATION", "QUIZ", "CONTENT", "FILL_IN_BLANK"]),
  orderIndex: z.number().int().min(0),
  // Üresen is engedélyezett: forge módban új csillagrendszer létrehozásakor
  // (isNewStarSystem) még nincs starSystemId, csak a mentés után jön létre —
  // a tényleges kötelezőség-ellenőrzést az onSubmit végzi a UI-állapot alapján.
  starSystemId: z.string(),
});

export type MissionEditorBaseFormValues = z.infer<typeof missionEditorBaseSchema>;
