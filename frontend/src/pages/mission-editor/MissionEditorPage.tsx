import React, { useEffect, useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, Controller } from "react-hook-form";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Box,
  Typography,
  TextField,
  Button,
  IconButton,
  Grid,
  Divider,
  CircularProgress,
  Alert,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from "@mui/material";
import { ArrowBack as ArrowBackIcon, Save as SaveIcon, RocketLaunch as MissionIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import apiClient, { forgeApi, starSystemApi } from "../../api/client";
import { useChatContext } from "../../context/ChatContext";
import { GlowCard } from "../../components/shared/GlowCard";
import MarkdownStudio from "../../components/domain/mission/MarkdownStudio";
import QuizContentEditor from "../../components/domain/mission/QuizContentEditor";
import CodeMissionEditor from "../../components/domain/mission/CodeMissionEditor";
import FillInBlankEditor from "../../components/admin/FillInBlankEditor";
import { missionEditorBaseSchema, type MissionEditorBaseFormValues } from "../../schemas/missionEditor";
import type { StarSystemResponse } from "../../types/starSystem";

const DIFFICULTIES = ["EASY", "MEDIUM", "HARD", "EXPERT"] as const;

/** Admin módban minden típus elérhető; Forge (kadét self-service) módban csak a Gitea-alapú típusok — ugyanaz a szűkítés, mint a régi ForgeConfigPanel-ben. */
const ADMIN_MISSION_TYPES = ["CODING", "CIRCUIT_SIMULATION", "QUIZ", "CONTENT", "FILL_IN_BLANK"] as const;
const FORGE_MISSION_TYPES = ["CODING", "CIRCUIT_SIMULATION", "QUIZ"] as const;

interface MissionEditorPageProps {
  mode: "admin" | "forge";
}

/**
 * Egységes misszió-szerkesztő (terv 4.1) — kiváltja a korábbi
 * MissionForgePage/MissionEdit kettősséget. Ugyanaz a komponens-készlet
 * szolgálja ki az admin (`/admin/missions/:id`) és a kadét Forge
 * (`/forge/:missionId`) route-okat, a `mode` prop dönti el a
 * jogosultsági/UI-különbségeket.
 */
const MissionEditorPage: React.FC<MissionEditorPageProps> = ({ mode }) => {
  const { t } = useTranslation();
  const { id, missionId } = useParams<{ id?: string; missionId?: string }>();
  const currentId = mode === "admin" ? id : missionId;
  const isNew = !currentId;
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { setFormFields, registerFillCallback } = useChatContext();

  const queryParams = new URLSearchParams(location.search);
  const starSystemIdFromQuery = queryParams.get("starSystemId");

  const [isNewStarSystem, setIsNewStarSystem] = useState(false);
  const [newStarSystemName, setNewStarSystemName] = useState("");
  const [newStarSystemDescription, setNewStarSystemDescription] = useState("");
  const [templateLanguage, setTemplateLanguage] = useState<"javascript" | "python">("javascript");
  const [content, setContent] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const { data: starSystems } = useQuery<StarSystemResponse[]>({
    queryKey: mode === "admin" ? ["starSystems"] : ["myStarSystems"],
    queryFn: async () => {
      if (mode === "admin") {
        const res = await apiClient.get<StarSystemResponse[]>("/star-systems");
        return res.data;
      }
      return forgeApi.getMyStarSystems();
    },
  });

  const { data: existingMission, isLoading: isLoadingMission } = useQuery({
    queryKey: ["mission", currentId],
    queryFn: async () => {
      if (mode === "admin") {
        const res = await apiClient.get(`/missions/${currentId}`);
        return res.data as MissionEditorBaseFormValues & { id: string; content?: string };
      }
      return forgeApi.getMissionById(currentId!);
    },
    enabled: !isNew,
  });

  const { data: nextOrder } = useQuery({
    queryKey: ["nextOrder", starSystemIdFromQuery],
    queryFn: async () => {
      const res = await apiClient.get<number>("/missions/next-order", {
        params: { starSystemId: starSystemIdFromQuery },
      });
      return res.data;
    },
    enabled: mode === "admin" && isNew && !!starSystemIdFromQuery,
  });

  const { control, handleSubmit, watch, setValue, reset, formState } = useForm<MissionEditorBaseFormValues>({
    resolver: zodResolver(missionEditorBaseSchema),
    defaultValues: {
      name: "",
      descriptionMarkdown: "",
      difficulty: "EASY",
      missionType: "CODING",
      orderIndex: 0,
      starSystemId: starSystemIdFromQuery || "",
    },
  });

  const missionType = watch("missionType");
  const starSystemId = watch("starSystemId");

  useEffect(() => {
    if (existingMission) {
      reset({
        name: existingMission.name,
        descriptionMarkdown: existingMission.descriptionMarkdown || "",
        difficulty: existingMission.difficulty,
        missionType: existingMission.missionType,
        orderIndex: existingMission.orderIndex ?? 0,
        starSystemId: existingMission.starSystemId,
      });
      if ("content" in existingMission && existingMission.content) {
        setContent(existingMission.content);
      }
    }
  }, [existingMission, reset]);

  useEffect(() => {
    if (isNew && nextOrder !== undefined) {
      setValue("orderIndex", nextOrder);
    }
  }, [isNew, nextOrder, setValue]);

  useEffect(() => {
    if (mode === "forge" && isNew && starSystems && starSystems.length === 0) {
      setIsNewStarSystem(true);
    }
  }, [mode, isNew, starSystems]);

  useEffect(() => {
    if (mode !== "admin") return;
    const sub = watch((values) => {
      setFormFields({
        name: values.name || "",
        descriptionMarkdown: values.descriptionMarkdown || "",
        difficulty: values.difficulty || "",
        missionType: values.missionType || "",
      });
    });
    return () => sub.unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  useEffect(() => {
    if (mode !== "admin") return;
    registerFillCallback((fields) => {
      if (fields.name !== undefined) setValue("name", fields.name);
      if (fields.descriptionMarkdown !== undefined) setValue("descriptionMarkdown", fields.descriptionMarkdown);
      if (fields.difficulty !== undefined) setValue("difficulty", fields.difficulty as any);
      if (fields.missionType !== undefined) setValue("missionType", fields.missionType as any);
    });
    return () => registerFillCallback(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  const createMutation = useMutation({
    mutationFn: async (values: MissionEditorBaseFormValues) => {
      if (mode === "forge") {
        let targetSystemId = values.starSystemId;
        if (isNewStarSystem && newStarSystemName) {
          const newSystem = await starSystemApi.create({
            name: newStarSystemName,
            description: newStarSystemDescription || "",
          });
          targetSystemId = newSystem.id;
        }
        return forgeApi.initializeMission({
          starSystemId: targetSystemId,
          name: values.name,
          descriptionMarkdown: values.descriptionMarkdown,
          missionType: values.missionType as any,
          difficulty: values.difficulty,
          orderIndex: values.orderIndex,
          templateLanguage,
        });
      }

      if (values.missionType === "QUIZ") {
        return forgeApi.initializeMission({
          starSystemId: values.starSystemId,
          name: values.name,
          descriptionMarkdown: values.descriptionMarkdown,
          missionType: "QUIZ",
          difficulty: values.difficulty,
          orderIndex: values.orderIndex,
          templateLanguage: "javascript",
        });
      }
      const res = await apiClient.post<{ id: string }>("/missions", { ...values, templateFiles: {} });
      return { id: res.data.id } as any;
    },
    onSuccess: (created: any) => {
      queryClient.invalidateQueries({ queryKey: mode === "admin" ? ["starSystems"] : ["myStarSystems"] });
      const basePath = mode === "admin" ? "/admin/missions" : "/forge";
      navigate(`${basePath}/${created.id}`, { replace: true });
    },
    onError: (err: any) => {
      setSaveError(err.response?.data?.message || t("errorSaveMission"));
    },
  });

  const updateMutation = useMutation({
    mutationFn: async (values: MissionEditorBaseFormValues) => {
      await apiClient.put(`/missions/${currentId}`, { ...values, templateFiles: {} });
    },
    onSuccess: () => {
      setSaved(true);
      setSaveError(null);
      queryClient.invalidateQueries({ queryKey: ["mission", currentId] });
      setTimeout(() => setSaved(false), 3000);
    },
    onError: (err: any) => {
      setSaveError(err.response?.data?.message || t("errorSaveMission"));
    },
  });

  const contentSaveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.patch(`/missions/${currentId}/content`, { content });
    },
    onSuccess: () => {
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    },
  });

  const onSubmit = (values: MissionEditorBaseFormValues) => {
    setSaveError(null);
    if (isNew) {
      createMutation.mutate(values);
    } else {
      updateMutation.mutate(values);
    }
  };

  const handleBack = () => {
    if (mode === "admin") {
      navigate(starSystemId ? `/admin/star-systems/${starSystemId}` : "/admin/star-systems");
    } else {
      navigate("/my-forge");
    }
  };

  // Forge módban, ha a misszió MÁR létezik, a régi flow sosem engedte
  // szerkeszteni az alapadatokat (csak a tartalmat/kódot) — ezt megtartjuk.
  const showBaseForm = mode === "admin" || isNew;
  const availableTypes = mode === "admin" ? ADMIN_MISSION_TYPES : FORGE_MISSION_TYPES;

  if ((mode === "admin" && isLoadingMission) || (mode === "forge" && !isNew && isLoadingMission)) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  const effectiveType = mode === "forge" && !isNew ? existingMission?.missionType : missionType;

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", mb: 3, gap: 2 }}>
        <IconButton onClick={handleBack} color="primary" data-cy="forge-back-btn">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {isNew ? t("newMission") : existingMission?.name || t("editMission")}
        </Typography>
      </Box>

      {saveError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {saveError}
        </Alert>
      )}
      {saved && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {t("save")}
        </Alert>
      )}

      {showBaseForm && (
        <GlowCard component="form" onSubmit={handleSubmit(onSubmit)} sx={{ p: 4, mb: 3 }}>
          <Grid container spacing={4}>
            <Grid size={{ xs: 12, md: 4 }} sx={{ textAlign: "center" }} data-cy="sector-registry-panel">
              <Box
                sx={{
                  width: 120,
                  height: 120,
                  borderRadius: "50%",
                  bgcolor: "primary.main",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  margin: "0 auto 20px",
                  boxShadow: "var(--glow-md)",
                }}
              >
                <MissionIcon sx={{ fontSize: 60, color: "white" }} />
              </Box>

              {mode === "forge" && isNewStarSystem ? (
                <Box sx={{ textAlign: "left" }}>
                  <TextField
                    fullWidth
                    label={t("newStarSystemTitle")}
                    value={newStarSystemName}
                    onChange={(e) => setNewStarSystemName(e.target.value)}
                    sx={{ mb: 2 }}
                  />
                  <TextField
                    fullWidth
                    multiline
                    rows={4}
                    label={t("description")}
                    value={newStarSystemDescription}
                    onChange={(e) => setNewStarSystemDescription(e.target.value)}
                  />
                  {starSystems && starSystems.length > 0 && (
                    <Button size="small" onClick={() => setIsNewStarSystem(false)} sx={{ mt: 1 }}>
                      {t("starMap.back")}
                    </Button>
                  )}
                </Box>
              ) : (
                <Controller
                  name="starSystemId"
                  control={control}
                  render={({ field }) => (
                    <FormControl fullWidth disabled={mode === "admin" && !isNew && !starSystemIdFromQuery}>
                      <InputLabel>{t("starSystem")}</InputLabel>
                      <Select {...field} label={t("starSystem")}>
                        {starSystems?.map((sys) => (
                          <MenuItem key={sys.id} value={sys.id}>
                            {sys.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  )}
                />
              )}
              {mode === "forge" && !isNewStarSystem && (
                <Typography
                  onClick={() => setIsNewStarSystem(true)}
                  sx={{ color: "text.secondary", cursor: "pointer", mt: 1, fontSize: "0.85rem" }}
                >
                  [+] {t("newStarSystem")}
                </Typography>
              )}
            </Grid>

            <Grid size={{ xs: 12, md: 8 }} data-cy="mission-spec-panel">
              <Typography variant="h6" gutterBottom>
                {t("basicInfo")}
              </Typography>
              <Divider sx={{ mb: 3 }} />

              <Grid container spacing={2}>
                <Grid size={{ xs: 12 }}>
                  <Controller
                    name="name"
                    control={control}
                    render={({ field }) => (
                      <TextField
                        {...field}
                        label={t("missionName")}
                        fullWidth
                        required
                        error={!!formState.errors.name}
                        helperText={formState.errors.name ? t(formState.errors.name.message as string) : undefined}
                      />
                    )}
                  />
                </Grid>

                <Grid size={{ xs: 12 }}>
                  <Controller
                    name="descriptionMarkdown"
                    control={control}
                    render={({ field }) => (
                      <TextField
                        {...field}
                        label={t("descriptionMarkdown")}
                        fullWidth
                        multiline
                        rows={4}
                        placeholder={t("missionDescriptionPlaceholder")}
                      />
                    )}
                  />
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                  <Controller
                    name="difficulty"
                    control={control}
                    render={({ field }) => (
                      <FormControl fullWidth>
                        <InputLabel>{t("difficulty")}</InputLabel>
                        <Select {...field} label={t("difficulty")}>
                          {DIFFICULTIES.map((d) => (
                            <MenuItem key={d} value={d}>
                              {d}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    )}
                  />
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                  <Controller
                    name="missionType"
                    control={control}
                    render={({ field }) => (
                      <FormControl fullWidth disabled={!isNew}>
                        <InputLabel>{t("missionType")}</InputLabel>
                        <Select {...field} label={t("missionType")}>
                          {availableTypes.map((mt) => (
                            <MenuItem key={mt} value={mt}>
                              {mt.replace(/_/g, " ")}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    )}
                  />
                </Grid>

                {mode === "forge" && missionType !== "QUIZ" && (
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControl fullWidth>
                      <InputLabel>{t("forge.templateLanguage")}</InputLabel>
                      <Select
                        value={templateLanguage}
                        label={t("forge.templateLanguage")}
                        onChange={(e) => setTemplateLanguage(e.target.value as "javascript" | "python")}
                      >
                        <MenuItem value="javascript">JavaScript</MenuItem>
                        <MenuItem value="python">Python</MenuItem>
                      </Select>
                    </FormControl>
                  </Grid>
                )}

                <Grid size={{ xs: 12, md: 4 }}>
                  <Controller
                    name="orderIndex"
                    control={control}
                    render={({ field }) => (
                      <TextField
                        {...field}
                        label={t("orderInSystem")}
                        type="number"
                        fullWidth
                        inputProps={{ min: 0 }}
                        onChange={(e) => field.onChange(Number(e.target.value) || 0)}
                      />
                    )}
                  />
                </Grid>
              </Grid>

              <Box sx={{ mt: 4, display: "flex", justifyContent: "flex-end", gap: 2 }}>
                <Button variant="outlined" onClick={handleBack}>
                  {t("cancel")}
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  color="primary"
                  startIcon={<SaveIcon />}
                  disabled={createMutation.isPending || updateMutation.isPending}
                  data-cy="forge-submit-btn"
                >
                  {createMutation.isPending || updateMutation.isPending ? (
                    <CircularProgress size={24} />
                  ) : isNew ? (
                    t("create")
                  ) : (
                    t("save")
                  )}
                </Button>
              </Box>
            </Grid>
          </Grid>
        </GlowCard>
      )}

      {!isNew && currentId && effectiveType === "CONTENT" && (
        <GlowCard sx={{ p: 4 }}>
          <MarkdownStudio value={content} onChange={setContent} data-cy="mission-content-studio" />
          <Box sx={{ mt: 2, display: "flex", gap: 2, alignItems: "center" }}>
            <Button
              variant="contained"
              startIcon={<SaveIcon />}
              onClick={() => contentSaveMutation.mutate()}
              disabled={contentSaveMutation.isPending}
            >
              {t("save")}
            </Button>
          </Box>
        </GlowCard>
      )}

      {!isNew && currentId && effectiveType === "FILL_IN_BLANK" && (
        <GlowCard sx={{ p: 4 }}>
          <FillInBlankEditor missionId={currentId} />
        </GlowCard>
      )}

      {!isNew && currentId && effectiveType === "QUIZ" && (
        <GlowCard sx={{ p: 4 }}>
          <QuizContentEditor missionId={currentId} />
        </GlowCard>
      )}

      {!isNew && currentId && (effectiveType === "CODING" || effectiveType === "CIRCUIT_SIMULATION") && (
        <GlowCard sx={{ p: 4 }}>
          <CodeMissionEditor missionId={currentId} mode={mode === "admin" ? "template" : "workspace"} />
        </GlowCard>
      )}

      {isNew && mode === "admin" && missionType === "FILL_IN_BLANK" && (
        <Alert severity="info" sx={{ mt: 2 }}>
          {t("fillInBlank.saveFirst")}
        </Alert>
      )}
    </Box>
  );
};

export default MissionEditorPage;
