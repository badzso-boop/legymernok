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

  const {
    control,
    handleSubmit,
    register,
    formState: { errors },
    watch,
    setValue,
  } = useForm<CombinedForgeRequest>({
    defaultValues: {
      starSystemId: "",
      name: "",
      descriptionMarkdown: "",
      missionType: "CODING",
      difficulty: "EASY",
      orderInSystem: 1,
      templateLanguage: "javascript",
      newStarSystemName: "",
      newStarSystemDescription: "",
    },
  });

  const selectedStarSystemId = watch("starSystemId");

  const { data: starSystems, isLoading: isLoadingStarSystems } = useQuery<
    StarSystemResponse[]
  >({
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

  // Stílus a fehér terminál bemenetekhez
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
        minHeight: "100vh",
      }}
    >
      <div
        className="control-panel-casing"
        style={{
          width: "80vw",
          height: "85vh",
          display: "flex",
          flexDirection: "column",
          position: "relative",
          padding: "40px",
        }}
      >
        <div className="screw top-left" />
        <div className="screw top-right" />
        <div className="screw bottom-left" />
        <div className="screw bottom-right" />

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
          {/* FELSŐ 2/3: ADATOK */}
          <Box sx={{ flex: 2, display: "flex", gap: 3 }}>
            {/* BAL OLDAL: SECTOR CONFIG */}
            <Box
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
                  {">"} SECTOR_CONFIG
                </Typography>

                {!isNewSystem ? (
                  <Box>
                    <FormControl
                      fullWidth
                      variant="filled"
                      sx={terminalInputSx}
                    >
                      <InputLabel>SELECT_SECTOR</InputLabel>
                      <Controller
                        name="starSystemId"
                        control={control}
                        render={({ field }) => (
                          <Select {...field}>
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
                      [+] REGISTER_NEW_SECTOR_PROTOCOL
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
                      [MODE: NEW_DEFINITION]
                    </Typography>
                    <TextField
                      fullWidth
                      label="SECTOR_NAME"
                      variant="filled"
                      {...register("newStarSystemName", {
                        required: isNewSystem,
                      })}
                      sx={terminalInputSx}
                    />
                    <TextField
                      fullWidth
                      label="SECTOR_DESCRIPTION"
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
                        {"<"} BACK_TO_REGISTRY
                      </Typography>
                    )}
                  </Box>
                )}
              </div>
            </Box>

            {/* JOBB OLDAL: MISSION CONFIG */}
            {/* MISSION CONFIG PANEL - JOBB OLDAL */}
            <Box
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
                  {">"} MISSION_SPEC
                </Typography>

                <TextField
                  fullWidth
                  label="MISSION_NAME"
                  variant="filled"
                  {...register("name", { required: true, minLength: 3 })}
                  sx={terminalInputSx}
                />
                <TextField
                  fullWidth
                  label="OBJECTIVES (MARKDOWN)"
                  variant="filled"
                  multiline
                  rows={4}
                  {...register("descriptionMarkdown")}
                  sx={terminalInputSx}
                />

                {/* JAVÍTOTT GRID - 2X2 ELOSZTÁS */}
                <Grid container spacing={2} sx={{ width: "100%", m: 0 }}>
                  <Grid size={6}>
                    <FormControl
                      fullWidth
                      variant="filled"
                      sx={terminalInputSx}
                    >
                      <InputLabel>TYPE</InputLabel>
                      <Controller
                        name="missionType"
                        control={control}
                        render={({ field }) => (
                          <Select {...field} fullWidth>
                            <MenuItem value="CODING">CODING</MenuItem>
                            <MenuItem value="QUIZ">QUIZ</MenuItem>
                          </Select>
                        )}
                      />
                    </FormControl>
                  </Grid>

                  <Grid size={6}>
                    <FormControl
                      fullWidth
                      variant="filled"
                      sx={terminalInputSx}
                    >
                      <InputLabel>LANGUAGE</InputLabel>
                      <Controller
                        name="templateLanguage"
                        control={control}
                        render={({ field }) => (
                          <Select {...field} fullWidth>
                            <MenuItem value="javascript">Javascript</MenuItem>
                            <MenuItem value="python">Python</MenuItem>
                          </Select>
                        )}
                      />
                    </FormControl>
                  </Grid>

                  <Grid size={6}>
                    <FormControl
                      fullWidth
                      variant="filled"
                      sx={terminalInputSx}
                    >
                      <InputLabel>DIFFICULTY</InputLabel>
                      <Controller
                        name="difficulty"
                        control={control}
                        render={({ field }) => (
                          <Select {...field} fullWidth>
                            <MenuItem value="EASY">EASY</MenuItem>
                            <MenuItem value="MEDIUM">MEDIUM</MenuItem>
                            <MenuItem value="HARD">HARD</MenuItem>
                          </Select>
                        )}
                      />
                    </FormControl>
                  </Grid>

                  <Grid size={6}>
                    <TextField
                      fullWidth
                      label="ORDER"
                      type="number"
                      variant="filled"
                      {...register("orderInSystem")}
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
            <div className="button-group">
              <button
                type="button"
                className="retro-btn yellow"
                onClick={toggleLanguage}
              />
              <div className="label-plate" style={{ color: "#fff" }}>
                LANG: {i18n.language.toUpperCase()}
              </div>
            </div>

            <div className="button-group">
              <button
                type="button"
                className="retro-btn red"
                onClick={() => window.history.back()}
              />
              <div className="label-plate" style={{ color: "#fff" }}>
                ABORT
              </div>
            </div>

            <div className="button-group">
              <button
                type="submit"
                className={`retro-btn green ${initializeMutation.isPending ? "active" : ""}`}
                disabled={initializeMutation.isPending}
              />
              <div className="label-plate" style={{ color: "#fff" }}>
                {initializeMutation.isPending
                  ? "INITIALIZING..."
                  : "START_FORGE"}
              </div>
            </div>
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
      </div>
    </Box>
  );
};

export default ForgeConfigPanel;
