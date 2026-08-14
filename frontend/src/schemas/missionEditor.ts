import { z } from "zod";

export const missionEditorBaseSchema = z.object({
  name: z.string().min(3, "validation.nameMinLength"),
  descriptionMarkdown: z.string(),
  difficulty: z.enum(["EASY", "MEDIUM", "HARD", "EXPERT"]),
  missionType: z.enum(["CODING", "CIRCUIT_SIMULATION", "QUIZ", "CONTENT", "FILL_IN_BLANK"]),
  orderIndex: z.number().int().min(0),
  starSystemId: z.string().min(1, "validation.starSystemRequired"),
});

export type MissionEditorBaseFormValues = z.infer<typeof missionEditorBaseSchema>;
