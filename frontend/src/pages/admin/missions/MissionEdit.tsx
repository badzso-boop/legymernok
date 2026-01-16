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
  type SelectChangeEvent,
} from "@mui/material";
import {
  ArrowBack as ArrowBackIcon,
  Save as SaveIcon,
  RocketLaunch as MissionIcon,
} from "@mui/icons-material";
import axios from "axios";
import type { StarSystemResponse } from "../../../types/starSystem";
import { useTranslation } from "react-i18next";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const DIFFICULTIES = ["EASY", "MEDIUM", "HARD", "EXPERT"];
const MISSION_TYPES = ["CODING", "CIRCUIT_SIMULATION"];

const MissionEdit: React.FC = () => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const isNew = !id;

  // Ha új küldetést hozunk létre, a query paramból kapjuk meg a starSystemId-t
  const queryParams = new URLSearchParams(location.search);
  const starSystemIdFromQuery = queryParams.get("starSystemId");

  const [starSystems, setStarSystems] = useState<StarSystemResponse[]>([]);
  const [mission, setMission] = useState({
    name: "",
    descriptionMarkdown: "",
    difficulty: "EASY",
    missionType: "CODING",
    orderInSystem: 1,
    starSystemId: starSystemIdFromQuery || "",
  });

  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchContext = async () => {
      const token = localStorage.getItem("token");

      try {
        setLoading(true);

        const systemsRes = await axios.get<StarSystemResponse[]>(
          `${API_URL}/star-systems`,
          {
            headers: { Authorization: `Bearer ${token}` },
          }
        );
        setStarSystems(systemsRes.data);
        if (!isNew) {
          // 1. Szerkesztés: A küldetést kérjük le változatlanul
          const response = await axios.get(`${API_URL}/missions/${id}`, {
            headers: { Authorization: `Bearer ${token}` },
          });
          setMission(response.data);
        } else if (starSystemIdFromQuery) {
          // 2. Új Létrehozás: Csak a KÖVETKEZŐ sorszámot kérjük le
          const response = await axios.get<number>(
            `${API_URL}/missions/next-order`,
            {
              params: { starSystemId: starSystemIdFromQuery },
              headers: { Authorization: `Bearer ${token}` },
            }
          );

          setMission((prev) => ({
            ...prev,
            orderInSystem: response.data, // A backend már a (max + 1)-et adja
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
    const token = localStorage.getItem("token");

    // Beállítjuk az ID-t
    setMission((prev) => ({ ...prev, starSystemId: newSystemId }));

    // Lekérjük az új rendszerhez tartozó következő sorszámot
    try {
      const res = await axios.get<number>(`${API_URL}/missions/next-order`, {
        params: { starSystemId: newSystemId },
        headers: { Authorization: `Bearer ${token}` },
      });
      setMission((prev) => ({ ...prev, orderInSystem: res.data }));
    } catch (err) {
      console.error(t("errorOrderTaken", { order: mission.orderInSystem }));
    }
  };

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => {
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
      const token = localStorage.getItem("token");
      const payload = {
        ...mission,
        templateFiles: {}, // Egyelőre üres, amíg nincs Monaco Editor
      };

      if (isNew) {
        await axios.post(`${API_URL}/missions`, payload, {
          headers: { Authorization: `Bearer ${token}` },
        });
      } else {
        await axios.put(`${API_URL}/missions/${id}`, payload, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }

      // Visszanavigálunk a csillagrendszer szerkesztőhöz
      navigate(`/admin/star-systems/${mission.starSystemId}`);
    } catch (err: any) {
      setError(err.response?.data?.message || t("errorSaveMission"));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <CircularProgress />;

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", mb: 3, gap: 2 }}>
        <IconButton
          onClick={() =>
            navigate(`/admin/star-systems/${mission.starSystemId}`)
          }
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
              <FormControl
                fullWidth
                disabled={!isNew && !starSystemIdFromQuery}
              >
                <InputLabel>{t("starSystem")}</InputLabel>
                <Select
                  name="starSystemId"
                  value={mission.starSystemId}
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
            <Typography variant="h6">
              {mission.name || t("unnamedMission")}
            </Typography>
            <Typography color="text.secondary">
              ID: {mission.starSystemId}
            </Typography>
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
                    {MISSION_TYPES.map((t) => (
                      <MenuItem key={t} value={t}>
                        {t.replace("_", " ")}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>

              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  name="orderInSystem"
                  label={t("orderInSystem")}
                  type="number"
                  fullWidth
                  value={mission.orderInSystem}
                  inputProps={{ min: 1 }}
                  onChange={handleChange}
                />
              </Grid>
            </Grid>

            <Box
              sx={{
                mt: 4,
                display: "flex",
                justifyContent: "flex-end",
                gap: 2,
              }}
            >
              <Button
                variant="outlined"
                onClick={() =>
                  navigate(`/admin/star-systems/${mission.starSystemId}`)
                }
              >
                {t("cancel")}
              </Button>
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
    </Box>
  );
};

export default MissionEdit;
