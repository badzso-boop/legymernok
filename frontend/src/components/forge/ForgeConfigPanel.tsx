import React, { useState, useEffect } from "react";
import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
  CircularProgress,
  Alert,
  Snackbar,
} from "@mui/material";
import type {
  SelectChangeEvent, // Import SelectChangeEvent
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { useForm } from "react-hook-form"; // Controller egyelőre nem kell a minimális JSX-hez
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { forgeApi } from "../../api/client";
import type {
  CreateMissionInitialRequest,
  MissionForgeResponse,
  // VerificationStatus, // Nem közvetlenül használatos ebben a panel logikájában
} from "../../types/mission-forge"; // Helyesbített importálási útvonal a missionForge.ts-hez
import type { StarSystemResponse } from "../../types/starSystem";
// A MissionType és Difficulty importálása tényleges futásidejű értékként, ha defaultValues-ben használjuk
// Mivel string literál típusok, közvetlenül a literális stringeket használjuk
import type { MissionType, Difficulty } from "../../types/mission";

interface ForgeConfigPanelProps {
  onMissionInitialized: (mission: MissionForgeResponse) => void;
}

const ForgeConfigPanel: React.FC<ForgeConfigPanelProps> = ({
  onMissionInitialized,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  // --- useForm beállítása értelmes alapértelmezett értékekkel ---
  const {
    control,
    handleSubmit,
    register,
    formState: { errors },
    reset,
    watch,
    setValue,
  } = useForm<CreateMissionInitialRequest>({
    defaultValues: {
      starSystemId: "",
      name: "",
      descriptionMarkdown: "",
      missionType: "CODING", // Javítva: literális stringet használ
      difficulty: "EASY", // Javítva: literális stringet használ
      orderInSystem: 1,
      templateLanguage: "javascript",
    },
  });

  // Állapot a Snackbar visszajelzéshez (egyelőre nincs renderelve, de a logika itt van)
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [snackbarSeverity, setSnackbarSeverity] = useState<"success" | "error">(
    "success",
  );

  // --- A kiválasztott starSystemId figyelése az alapértelmezett sorrend frissítéséhez ---
  const selectedStarSystemId = watch("starSystemId");

  // --- Lekérdezés a felhasználó csillagrendszereinek lekéréséhez ---
  const {
    data: starSystems,
    isLoading: isLoadingStarSystems,
    error: starSystemsError,
  } = useQuery<StarSystemResponse[], Error, StarSystemResponse[]>({
    queryKey: ["myStarSystems"],
    queryFn: forgeApi.getMyStarSystems,
    enabled: true,
  });

  useEffect(() => {
    if (starSystems && starSystems.length > 0 && !watch("starSystemId")) {
      setValue("starSystemId", starSystems[0].id);
    }
  }, [starSystems, watch, setValue]);

  useEffect(() => {
    if (starSystemsError) {
      console.error("Failed to fetch star systems:", starSystemsError);
      let errorMessage = t("forge.errorFetchingStarSystems");
      if ((starSystemsError as any).response?.data?.message) {
        errorMessage += `: ${(starSystemsError as any).response.data.message}`;
      } else {
        errorMessage += `: ${starSystemsError.message}`;
      }
      setSnackbarMessage(errorMessage);
      setSnackbarSeverity("error");
      setSnackbarOpen(true);
    }
  }, [starSystemsError, t]);

  // --- Mutáció egy új misszió inicializálásához ---
  const initializeMissionMutation = useMutation<
    MissionForgeResponse,
    Error, // A hibaobjektum típusa
    CreateMissionInitialRequest // A mutate-nak átadott változók típusa
  >({
    // Egyetlen opciók objektum átadása
    mutationFn: forgeApi.initializeMission,
    onSuccess: (data) => {
      onMissionInitialized(data);
      setSnackbarMessage(
        t("forge.missionInitializedSuccess", { missionName: data.name }),
      );
      setSnackbarSeverity("success");
      setSnackbarOpen(true);
      reset();
      queryClient.invalidateQueries({ queryKey: ["myStarSystems"] }); // Helyesbített invalidateQueries szintaxis
    },
    onError: (error: Error) => {
      // Explicit módon típusozva az error-t
      let errorMessage = t("forge.errorInitializingMission");
      if ((error as any).response?.data?.message) {
        errorMessage += `: ${(error as any).response.data.message}`;
      } else {
        errorMessage += `: ${error.message}`;
      }
      setSnackbarMessage(errorMessage);
      setSnackbarSeverity("error");
      setSnackbarOpen(true);
    },
  });

  // --- Űrlap elküldési kezelő ---
  const onSubmit = (data: CreateMissionInitialRequest) => {
    initializeMissionMutation.mutate(data);
  };

  // --- Snackbar bezárási kezelő ---
  const handleCloseSnackbar = () => {
    setSnackbarOpen(false);
  };

  // --- Egyelőre minimális JSX visszatérési érték ---
  return (
    <Box
      sx={{
        p: 3,
        bgcolor: "background.paper",
        borderRadius: 2,
        boxShadow: 3,
        maxWidth: 500,
        mx: "auto",
        mt: 4,
      }}
    >
      <Typography variant="h5" component="h2" gutterBottom>
        {t("forge.newMission")}
      </Typography>

      <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
        {/* Star System kiválasztás */}
        <FormControl fullWidth margin="normal" error={!!errors.starSystemId}>
          <InputLabel id="star-system-select-label">
            {t("forge.selectStarSystem")}
          </InputLabel>
          <Select
            labelId="star-system-select-label"
            id="starSystemId"
            label={t("forge.selectStarSystem")}
            {...register("starSystemId", { required: true })}
            value={selectedStarSystemId}
            onChange={(event: SelectChangeEvent) => {
              setValue("starSystemId", event.target.value);
            }}
            disabled={isLoadingStarSystems}
          >
            {isLoadingStarSystems && (
              <MenuItem disabled>
                <CircularProgress size={20} />
              </MenuItem>
            )}
            {starSystems?.map((system) => (
              <MenuItem key={system.id} value={system.id}>
                {system.name}
              </MenuItem>
            ))}
          </Select>
          {errors.starSystemId && (
            <Typography color="error" variant="caption">
              {t("forge.starSystemRequired")}
            </Typography>
          )}
        </FormControl>

        {/* Misszió neve */}
        <TextField
          fullWidth
          margin="normal"
          label={t("forge.missionName")}
          {...register("name", { required: true, minLength: 3 })}
          error={!!errors.name}
          helperText={
            errors.name?.type === "required"
              ? t("forge.missionNameRequired")
              : errors.name?.type === "minLength"
                ? t("forge.missionNameMinLength", { count: 3 })
                : ""
          }
        />

        {/* Misszió leírása (Markdown) */}
        <TextField
          fullWidth
          margin="normal"
          label={t("forge.missionDescription")}
          multiline
          rows={4}
          {...register("descriptionMarkdown")}
        />

        {/* Nehézség kiválasztás */}
        <FormControl fullWidth margin="normal">
          <InputLabel id="difficulty-select-label">
            {t("forge.difficulty")}
          </InputLabel>
          <Select
            labelId="difficulty-select-label"
            id="difficulty"
            label={t("forge.difficulty")}
            {...register("difficulty")}
          >
            {["EASY", "MEDIUM", "HARD", "INSANE"].map((level) => (
              <MenuItem key={level} value={level}>
                {t(`difficultyType.${level.toLowerCase()}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* Misszió típusa */}
        <FormControl fullWidth margin="normal">
          <InputLabel id="mission-type-select-label">
            {t("forge.missionType")}
          </InputLabel>
          <Select
            labelId="mission-type-select-label"
            id="missionType"
            label={t("forge.missionType")}
            {...register("missionType")}
          >
            {["CODING", "QUIZ", "CHALLENGE"].map((type) => (
              <MenuItem key={type} value={type}>
                {t(`missionTypes.${type.toLowerCase()}`)}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {/* Sorrend a rendszerben */}
        <TextField
          fullWidth
          margin="normal"
          label={t("forge.orderInSystem")}
          type="number"
          {...register("orderInSystem", { valueAsNumber: true, min: 1 })}
          error={!!errors.orderInSystem}
          helperText={
            errors.orderInSystem?.type === "min"
              ? t("forge.orderMin", { count: 1 })
              : ""
          }
        />

        {/* Template nyelv kiválasztása */}
        <FormControl fullWidth margin="normal">
          <InputLabel id="template-language-select-label">
            {t("forge.templateLanguage")}
          </InputLabel>
          <Select
            labelId="template-language-select-label"
            id="templateLanguage"
            label={t("forge.templateLanguage")}
            {...register("templateLanguage")}
          >
            <MenuItem value="javascript">JavaScript</MenuItem>
            <MenuItem value="python">Python</MenuItem>
          </Select>
        </FormControl>

        <Button
          type="submit"
          fullWidth
          variant="contained"
          sx={{ mt: 3, mb: 2 }}
          disabled={initializeMissionMutation.isPending}
        >
          {initializeMissionMutation.isPending ? (
            <CircularProgress size={24} color="inherit" />
          ) : (
            t("forge.initializeMission")
          )}
        </Button>
      </Box>

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={6000}
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
      >
        <Alert
          onClose={handleCloseSnackbar}
          severity={snackbarSeverity}
          sx={{ width: "100%" }}
        >
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default ForgeConfigPanel;
