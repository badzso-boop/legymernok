import React, { useState, useEffect } from "react";
import {
  Box,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
  Grid,
  Alert,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { useForm, Controller } from "react-hook-form";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { forgeApi, starSystemApi } from "../../api/client";
import type {
  CreateMissionInitialRequest,
  MissionForgeResponse,
} from "../../types/mission-forge";
import type { StarSystemResponse } from "../../types/starSystem";
import "../../styles/RetroUI.css";
import RetroButton from "../RetroButton";
import { RetroPanel } from "./RetroPanel";

interface ForgeConfigPanelProps {
  onMissionInitialized: (mission: MissionForgeResponse) => void;
}

interface CombinedForgeRequest extends CreateMissionInitialRequest {
  newStarSystemName?: string;
  newStarSystemDescription?: string;
}

const ForgeConfigPanel: React.FC<ForgeConfigPanelProps> = ({
  onMissionInitialized,
}) => {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [isNewSystem, setIsNewSystem] = useState(false);

  const { control, handleSubmit, register, watch, setValue } =
    useForm<CombinedForgeRequest>({
      defaultValues: {
        starSystemId: "",
        name: "",
        descriptionMarkdown: "",
        missionType: "CODING",
        difficulty: "EASY",
        orderIndex: 1,
        templateLanguage: "javascript",
        newStarSystemName: "",
        newStarSystemDescription: "",
      },
    });

  const selectedStarSystemId = watch("starSystemId");

  const { data: starSystems } = useQuery<StarSystemResponse[]>({
    queryKey: ["myStarSystems"],
    queryFn: forgeApi.getMyStarSystems,
  });

  useEffect(() => {
    if (starSystems) {
      if (starSystems.length === 0) {
        setIsNewSystem(true);
      } else if (!selectedStarSystemId && !isNewSystem) {
        setValue("starSystemId", starSystems[0].id);
      }
    }
  }, [starSystems, setValue, selectedStarSystemId, isNewSystem]);

  const initializeMutation = useMutation({
    mutationFn: async (data: CombinedForgeRequest) => {
      let targetSystemId = data.starSystemId;
      if (isNewSystem && data.newStarSystemName) {
        const newSystem = await starSystemApi.create({
          name: data.newStarSystemName,
          description: data.newStarSystemDescription || "",
        });
        targetSystemId = newSystem.id;
      }
      return forgeApi.initializeMission({
        ...data,
        starSystemId: targetSystemId,
      });
    },
    onSuccess: (data) => {
      onMissionInitialized(data);
      queryClient.invalidateQueries({ queryKey: ["myStarSystems"] });
    },
  });

  const toggleLanguage = () => {
    const nextLang = i18n.language === "hu" ? "en" : "hu";
    i18n.changeLanguage(nextLang);
  };

  const onSubmit = (data: CombinedForgeRequest) => {
    initializeMutation.mutate(data);
  };

  const terminalInputSx = {
    "& .MuiInputBase-root": { color: "#fff", fontFamily: "monospace" },
    "& .MuiInputLabel-root": { color: "#888", fontFamily: "monospace" },
    "& .MuiFilledInput-underline:before": { borderBottomColor: "#333" },
    "& .MuiFilledInput-underline:after": { borderBottomColor: "#fff" },
    bgcolor: "#0a0a0a",
    mb: 2,
  };

  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        width: "100%",
      }}
    >
      <RetroPanel
        sx={{
          width: "85vw",
          height: "85vh",
          display: "flex",
          flexDirection: "column",
          padding: "40px",
        }}
      >
        <Box
          component="form"
          onSubmit={handleSubmit(onSubmit)}
          sx={{
            display: "flex",
            flexDirection: "column",
            height: "100%",
            gap: 3,
          }}
        >
          <Box sx={{ flex: 2, display: "flex", gap: 3 }}>
            {/* BAL OLDAL: SECTOR CONFIG */}
            <Box
              data-cy="sector-registry-panel"
              sx={{
                flex: 1,
                border: "2px solid #333",
                bgcolor: "rgba(0,0,0,0.4)",
                borderRadius: "5px",
                p: 3,
              }}
            >
              <div
                className="terminal-content"
                style={{ color: "#fff", textShadow: "none" }}
              >
                <Typography
                  variant="h6"
                  sx={{
                    mb: 3,
                    borderBottom: "1px solid #333",
                    fontFamily: "monospace",
                  }}
                >
                  {">"} {t("forge.sectorRegistry").toUpperCase()}
                </Typography>

                {!isNewSystem ? (
                  <Box>
                    <FormControl
                      fullWidth
                      variant="filled"
                      sx={terminalInputSx}
                    >
                      <InputLabel>
                        {t("forge.selectStarSystem").toUpperCase()}
                      </InputLabel>
                      <Controller
                        name="starSystemId"
                        control={control}
                        render={({ field }) => (
                          <Select {...field} fullWidth>
                            {starSystems?.map((s) => (
                              <MenuItem key={s.id} value={s.id}>
                                {s.name.toUpperCase()}
                              </MenuItem>
                            ))}
                          </Select>
                        )}
                      />
                    </FormControl>
                    <Typography
                      onClick={() => setIsNewSystem(true)}
                      sx={{
                        color: "#666",
                        cursor: "pointer",
                        "&:hover": { color: "#fff" },
                        fontSize: "0.8rem",
                        mt: 2,
                        fontFamily: "monospace",
                      }}
                    >
                      [+] {t("newStarSystem").toUpperCase()}
                    </Typography>
                  </Box>
                ) : (
                  <Box>
                    <Typography
                      variant="caption"
                      sx={{
                        color: "#aaa",
                        display: "block",
                        mb: 2,
                        fontFamily: "monospace",
                      }}
                    >
                      [MODE: {t("newStarSystemTitle").toUpperCase()}]
                    </Typography>
                    <TextField
                      fullWidth
                      label={t("name").toUpperCase()}
                      variant="filled"
                      {...register("newStarSystemName", {
                        required: isNewSystem,
                      })}
                      sx={terminalInputSx}
                    />
                    <TextField
                      fullWidth
                      label={t("description").toUpperCase()}
                      variant="filled"
                      multiline
                      rows={6}
                      {...register("newStarSystemDescription")}
                      sx={terminalInputSx}
                    />
                    {starSystems && starSystems.length > 0 && (
                      <Typography
                        onClick={() => setIsNewSystem(false)}
                        sx={{
                          color: "#888",
                          cursor: "pointer",
                          fontSize: "0.8rem",
                          mt: 2,
                          fontFamily: "monospace",
                        }}
                      >
                        {"<"} {t("starMap.back").toUpperCase()}
                      </Typography>
                    )}
                  </Box>
                )}
              </div>
            </Box>

            {/* JOBB OLDAL: MISSION CONFIG */}
            <Box
              data-cy="mission-spec-panel"
              sx={{
                flex: 1,
                border: "2px solid #333",
                bgcolor: "rgba(0,0,0,0.4)",
                borderRadius: "5px",
                p: 3,
              }}
            >
              <div
                className="terminal-content"
                style={{ color: "#fff", textShadow: "none" }}
              >
                <Typography
                  variant="h6"
                  sx={{
                    mb: 3,
                    borderBottom: "1px solid #333",
                    fontFamily: "monospace",
                  }}
                >
                  {">"} {t("forge.missionSpec").toUpperCase()}
                </Typography>

                <TextField
                  fullWidth
                  label={t("forge.missionName").toUpperCase()}
                  variant="filled"
                  {...register("name", { required: true, minLength: 3 })}
                  sx={terminalInputSx}
                />
                <TextField
                  fullWidth
                  label={t("forge.missionDescription").toUpperCase()}
                  variant="filled"
                  multiline
                  rows={4}
                  {...register("descriptionMarkdown")}
                  sx={terminalInputSx}
                />

                <Grid container spacing={2} sx={{ width: "100%", m: 0 }}>
                  <Grid size={6}>
                    <FormControl
                      fullWidth
                      variant="filled"
                      sx={terminalInputSx}
                    >
                      <InputLabel>
                        {t("forge.missionType").toUpperCase()}
                      </InputLabel>
                      <Controller
                        name="missionType"
                        control={control}
                        render={({ field }) => (
                          <Select {...field} fullWidth>
                            <MenuItem value="CODING">
                              {t("missionTypes.coding").toUpperCase()}
                            </MenuItem>
                            <MenuItem value="QUIZ">
                              {t("missionTypes.quiz").toUpperCase()}
                            </MenuItem>
                            <MenuItem value="CHALLENGE">
                              {t("missionTypes.challenge").toUpperCase()}
                            </MenuItem>
                          </Select>
                        )}
                      />
                    </FormControl>
                  </Grid>

                  <Grid size={6}>
                    {watch("missionType") !== "QUIZ" && (
                      <FormControl
                        fullWidth
                        variant="filled"
                        sx={terminalInputSx}
                      >
                        <InputLabel>
                          {t("forge.templateLanguage").toUpperCase()}
                        </InputLabel>
                        <Controller
                          name="templateLanguage"
                          control={control}
                          render={({ field }) => (
                            <Select {...field} fullWidth>
                              <MenuItem value="javascript">JAVASCRIPT</MenuItem>
                              <MenuItem value="python">PYTHON</MenuItem>
                            </Select>
                          )}
                        />
                      </FormControl>
                    )}
                  </Grid>

                  <Grid size={6}>
                    <FormControl
                      fullWidth
                      variant="filled"
                      sx={terminalInputSx}
                    >
                      <InputLabel>
                        {t("forge.difficulty").toUpperCase()}
                      </InputLabel>
                      <Controller
                        name="difficulty"
                        control={control}
                        render={({ field }) => (
                          <Select {...field} fullWidth>
                            <MenuItem value="EASY">
                              {t("difficultyType.easy").toUpperCase()}
                            </MenuItem>
                            <MenuItem value="MEDIUM">
                              {t("difficultyType.medium").toUpperCase()}
                            </MenuItem>
                            <MenuItem value="HARD">
                              {t("difficultyType.hard").toUpperCase()}
                            </MenuItem>
                            <MenuItem value="INSANE">
                              {t("difficultyType.insane").toUpperCase()}
                            </MenuItem>
                          </Select>
                        )}
                      />
                    </FormControl>
                  </Grid>

                  <Grid size={6}>
                    <TextField
                      fullWidth
                      label={t("forge.orderInSystem").toUpperCase()}
                      type="number"
                      variant="filled"
                      {...register("orderIndex")}
                      sx={terminalInputSx}
                    />
                  </Grid>
                </Grid>
              </div>
            </Box>
          </Box>

          {/* ALSÓ 1/3: VEZÉRLŐK */}
          <Box
            sx={{
              flex: 1,
              border: "2px solid #333",
              bgcolor: "#1a1a1a",
              borderRadius: "10px",
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              gap: 10,
            }}
          >
            <RetroButton
              color="yellow"
              labelKey="starMap.lang"
              onClick={toggleLanguage}
            />
            <RetroButton
              color="red"
              labelKey="starMap.back"
              onClick={() => window.history.back()}
            />
            <RetroButton
              color="green"
              labelKey="forge.initializeMission"
              type="submit"
              disabled={initializeMutation.isPending}
              active={initializeMutation.isPending}
              data-cy="forge-submit-btn"
            />
          </Box>

          {initializeMutation.isError && (
            <Alert
              severity="error"
              sx={{
                bgcolor: "#200",
                color: "#f88",
                fontFamily: "monospace",
                borderRadius: 0,
              }}
            >
              ERROR:{" "}
              {(initializeMutation.error as any).response?.data?.message ||
                initializeMutation.error.message}
            </Alert>
          )}
        </Box>
      </RetroPanel>
    </Box>
  );
};

export default ForgeConfigPanel;
