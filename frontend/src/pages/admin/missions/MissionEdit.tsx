import React, { useEffect, useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import {
  Box,
  Typography,
  Paper,
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
  Tab,
  Tabs,
  type SelectChangeEvent,
} from "@mui/material";
import {
  ArrowBack as ArrowBackIcon,
  Save as SaveIcon,
  RocketLaunch as MissionIcon,
} from "@mui/icons-material";
import apiClient, { forgeApi } from "../../../api/client";
import type { StarSystemResponse } from "../../../types/starSystem";
import { useTranslation } from "react-i18next";
import ContentEditor from "../../../components/admin/ContentEditor";
import FillInBlankEditor from "../../../components/admin/FillInBlankEditor";

const DIFFICULTIES = ["EASY", "MEDIUM", "HARD", "EXPERT"];
const MISSION_TYPES = ["CODING", "CIRCUIT_SIMULATION", "QUIZ", "CONTENT", "FILL_IN_BLANK"];

interface MissionFormState {
  name: string;
  descriptionMarkdown: string;
  difficulty: string;
  missionType: string;
  orderIndex: number;
  starSystemId: string;
}

const MissionEdit: React.FC = () => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isNew = !id;

  const queryParams = new URLSearchParams(location.search);
  const starSystemIdFromQuery = queryParams.get("starSystemId");

  const [starSystems, setStarSystems] = useState<StarSystemResponse[]>([]);
  const [mission, setMission] = useState<MissionFormState>({
    name: "",
    descriptionMarkdown: "",
    difficulty: "EASY",
    missionType: "CODING",
    orderIndex: 0,
    starSystemId: starSystemIdFromQuery || "",
  });
  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchContext = async () => {
      try {
        setLoading(true);

        const systemsRes = await apiClient.get<StarSystemResponse[]>("/star-systems");
        setStarSystems(systemsRes.data);

        if (!isNew) {
          const response = await apiClient.get<MissionFormState>(`/missions/${id}`);
          setMission(response.data);
        } else if (starSystemIdFromQuery) {
          const response = await apiClient.get<number>("/missions/next-order", {
            params: { starSystemId: starSystemIdFromQuery },
          });
          setMission((prev) => ({
            ...prev,
            orderIndex: response.data,
            starSystemId: starSystemIdFromQuery,
          }));
        }
      } catch (err) {
        setError(t("errorFetchMissionDetails"));
      } finally {
        setLoading(false);
      }
    };

    fetchContext();
  }, [id, isNew, starSystemIdFromQuery]);

  const handleSystemChange = async (e: SelectChangeEvent<string>) => {
    const newSystemId = e.target.value;
    setMission((prev) => ({ ...prev, starSystemId: newSystemId }));
    try {
      const res = await apiClient.get<number>("/missions/next-order", {
        params: { starSystemId: newSystemId },
      });
      setMission((prev) => ({ ...prev, orderIndex: res.data }));
    } catch {
      // silent
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setMission((prev) => ({ ...prev, [name]: value }));
  };

  const handleSelectChange = (e: SelectChangeEvent<string>) => {
    const { name, value } = e.target;
    setMission((prev) => ({ ...prev, [name]: value }));
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const payload = { ...mission, templateFiles: {} };
      if (isNew) {
        if (mission.missionType === "QUIZ") {
          const res = await forgeApi.initializeMission({
            starSystemId: mission.starSystemId,
            name: mission.name,
            descriptionMarkdown: mission.descriptionMarkdown,
            missionType: "QUIZ",
            difficulty: mission.difficulty as any,
            orderIndex: mission.orderIndex,
            templateLanguage: "javascript",
          });
          navigate(`/forge/${res.id}`);
        } else {
          const res = await apiClient.post<{ id: string }>("/missions", payload);
          if (mission.missionType === "FILL_IN_BLANK") {
            navigate(`/admin/missions/${res.data.id}`);
          } else {
            navigate(`/admin/star-systems/${mission.starSystemId}`);
          }
        }
      } else {
        await apiClient.put(`/missions/${id}`, payload);
        navigate(`/admin/star-systems/${mission.starSystemId}`);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || t("errorSaveMission"));
    } finally {
      setSaving(false);
    }
  };

  const showContentTab = !isNew && mission.missionType === "CONTENT";
  const showFillInBlankTab = mission.missionType === "FILL_IN_BLANK";

  if (loading) return <CircularProgress />;

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", mb: 3, gap: 2 }}>
        <IconButton
          onClick={() => navigate(`/admin/star-systems/${mission.starSystemId}`)}
          color="primary"
        >
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {isNew ? t("newMission") : t("editMission")}
        </Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {(showContentTab || showFillInBlankTab) && (
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label="Alap adatok" />
          {showContentTab && <Tab label="Tartalom" />}
          {showFillInBlankTab && <Tab label="Kitöltős szerkesztő" />}
        </Tabs>
      )}

      {tab === 0 && (
        <Paper sx={{ p: 4 }}>
          <Grid container spacing={4}>
            <Grid size={{ xs: 12, md: 4 }} sx={{ textAlign: "center" }}>
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
                  boxShadow: "0 0 20px rgba(0, 242, 255, 0.3)",
                }}
              >
                <MissionIcon sx={{ fontSize: 60, color: "white" }} />
              </Box>
              <Grid size={{ xs: 12 }}>
                <FormControl fullWidth disabled={!isNew && !starSystemIdFromQuery}>
                  <InputLabel>{t("starSystem")}</InputLabel>
                  <Select
                    name="starSystemId"
                    value={starSystems.length === 0 ? "" : mission.starSystemId}
                    label="Csillagrendszer"
                    onChange={handleSystemChange}
                  >
                    {starSystems.map((sys) => (
                      <MenuItem key={sys.id} value={sys.id}>
                        {sys.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
              <Typography variant="h6">{mission.name || t("unnamedMission")}</Typography>
              <Typography color="text.secondary">ID: {mission.starSystemId}</Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 8 }}>
              <Typography variant="h6" gutterBottom>
                {t("basicInfo")}
              </Typography>
              <Divider sx={{ mb: 3 }} />

              <Grid container spacing={2}>
                <Grid size={{ xs: 12 }}>
                  <TextField
                    name="name"
                    label={t("missionName")}
                    fullWidth
                    value={mission.name}
                    onChange={handleChange}
                    required
                  />
                </Grid>

                <Grid size={{ xs: 12 }}>
                  <TextField
                    name="descriptionMarkdown"
                    label={t("descriptionMarkdown")}
                    fullWidth
                    multiline
                    rows={6}
                    value={mission.descriptionMarkdown}
                    onChange={handleChange}
                    placeholder={t("missionDescriptionPlaceholder")}
                  />
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth>
                    <InputLabel>{t("difficulty")}</InputLabel>
                    <Select
                      name="difficulty"
                      value={mission.difficulty}
                      label="Nehézség"
                      onChange={handleSelectChange}
                    >
                      {DIFFICULTIES.map((d) => (
                        <MenuItem key={d} value={d}>
                          {d}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                  <FormControl fullWidth>
                    <InputLabel>{t("missionType")}</InputLabel>
                    <Select
                      name="missionType"
                      value={mission.missionType}
                      label="Típus"
                      onChange={handleSelectChange}
                    >
                      {MISSION_TYPES.map((mt) => (
                        <MenuItem key={mt} value={mt}>
                          {mt.replace(/_/g, " ")}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField
                    name="orderIndex"
                    label={t("orderInSystem")}
                    type="number"
                    fullWidth
                    value={mission.orderIndex}
                    inputProps={{ min: 0 }}
                    onChange={handleChange}
                  />
                </Grid>
              </Grid>

              <Box sx={{ mt: 4, display: "flex", justifyContent: "flex-end", gap: 2 }}>
                <Button
                  variant="outlined"
                  onClick={() => navigate(`/admin/star-systems/${mission.starSystemId}`)}
                >
                  {t("cancel")}
                </Button>
                {!isNew && mission.missionType === "QUIZ" && (
                  <Button
                    variant="outlined"
                    color="secondary"
                    onClick={() => navigate(`/forge/${id}`)}
                  >
                    Kvíz szerkesztő
                  </Button>
                )}
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={<SaveIcon />}
                  onClick={handleSave}
                  disabled={saving}
                >
                  {saving ? (
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
        </Paper>
      )}

      {tab === 1 && showContentTab && id && (
        <Paper sx={{ p: 4 }}>
          <ContentEditor
            missionId={id}
          />
        </Paper>
      )}

      {tab === 1 && showFillInBlankTab && (
        <Paper sx={{ p: 4 }}>
          {id ? (
            <FillInBlankEditor missionId={id} />
          ) : (
            <Alert severity="info">
              Mentsd el először az alap adatokat, majd automatikusan ide kerülsz a szerkesztőbe.
            </Alert>
          )}
        </Paper>
      )}
    </Box>
  );
};

export default MissionEdit;
