import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Paper,
  TextField,
  Button,
  CircularProgress,
  Alert,
  Grid,
  List,
  ListItem,
  ListItemText,
  Divider,
} from "@mui/material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { StarSystemWithMissionsResponse } from "../../../types/starSystem";

const API_URL = "http://localhost:8080/api";

const StarSystemEdit: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const isNew = !id;

  const [starSystem, setStarSystem] = useState<
    Partial<StarSystemWithMissionsResponse>
  >({
    name: "",
    description: "",
    iconUrl: "",
    missions: [],
  });
  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isNew) {
      const fetchStarSystem = async () => {
        try {
          setLoading(true);
          const token = localStorage.getItem("token");
          // Az új, komplex végpontot hívjuk
          const response = await axios.get(
            `${API_URL}/star-systems/${id}/with-missions`,
            {
              headers: { Authorization: `Bearer ${token}` },
            }
          );
          setStarSystem(response.data);
        } catch (err) {
          setError("Nem sikerült betölteni a csillagrendszer adatait.");
        } finally {
          setLoading(false);
        }
      };
      fetchStarSystem();
    }
  }, [id, isNew]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setStarSystem((prev) => ({ ...prev, [name]: value }));
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      const token = localStorage.getItem("token");
      const payload = {
        name: starSystem.name,
        description: starSystem.description,
        iconUrl: starSystem.iconUrl,
      };

      if (isNew) {
        await axios.post(`${API_URL}/star-systems`, payload, {
          headers: { Authorization: `Bearer ${token}` },
        });
      } else {
        await axios.put(`${API_URL}/star-systems/${id}`, payload, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }
      navigate("/admin/star-systems");
    } catch (err) {
      setError("Hiba történt mentés közben.");
    } finally {
      setSaving(false);
    }
  };

  if (loading)
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
        <CircularProgress />
      </Box>
    );

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 4, fontWeight: "bold" }}>
        {isNew ? "Új csillagrendszer" : "Csillagrendszer szerkesztése"}
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper sx={{ p: 4 }}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              label="Név"
              name="name"
              value={starSystem.name || ""}
              onChange={handleChange}
              variant="outlined"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              label="Leírás"
              name="description"
              value={starSystem.description || ""}
              onChange={handleChange}
              multiline
              rows={4}
              variant="outlined"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              label="Ikon URL"
              name="iconUrl"
              value={starSystem.iconUrl || ""}
              onChange={handleChange}
              variant="outlined"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }}>
              <Button
                variant="outlined"
                color="secondary"
                onClick={() => navigate("/admin/star-systems")}
              >
                Mégse
              </Button>
              <Button
                variant="contained"
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? <CircularProgress size={24} /> : "Mentés"}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {!isNew && starSystem.missions && (
        <Box mt={5}>
          <Typography variant="h5" sx={{ mb: 2, fontWeight: "bold" }}>
            Küldetések ebben a rendszerben
          </Typography>
          <Paper>
            <List>
              {starSystem.missions.length > 0 ? (
                starSystem.missions.map((mission, index) => (
                  <React.Fragment key={mission.id}>
                    <ListItem>
                      <ListItemText
                        primary={mission.name}
                        secondary={`Nehézség: ${mission.difficulty}`}
                      />
                    </ListItem>
                    {index < starSystem.missions!.length - 1 && <Divider />}
                  </React.Fragment>
                ))
              ) : (
                <ListItem>
                  <ListItemText primary="Nincsenek küldetések ebben a rendszerben." />
                </ListItem>
              )}
            </List>
          </Paper>
        </Box>
      )}
    </Box>
  );
};

export default StarSystemEdit;
