import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Typography, Button, Alert } from "@mui/material";
import { Add as AddIcon } from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { MissionResponse } from "../../../types/mission";
import type { StarSystemResponse } from "../../../types/starSystem";
import MissionTable from "../../../components/mission/MissionTable";

// API URL (env-ből vagy fallback)
const API_URL = import.meta.env.VITE_API_URL || "/api";

const MissionList: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [missions, setMissions] = useState<MissionResponse[]>([]);
  const [starSystems, setStarSystems] = useState<StarSystemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem("token");
      const headers = { Authorization: `Bearer ${token}` };

      const [missionsRes, systemsRes] = await Promise.all([
        axios.get<MissionResponse[]>(`${API_URL}/missions`, { headers }),
        axios.get<StarSystemResponse[]>(`${API_URL}/star-systems`, { headers }),
      ]);

      setMissions(missionsRes.data);
      setStarSystems(systemsRes.data);
      setError(null);
    } catch (err) {
      setError(t("errorFetchMissions"));
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleDelete = async (id: string) => {
    if (window.confirm(t("deleteMissionConfirm"))) {
      try {
        const token = localStorage.getItem("token");
        await axios.delete(`${API_URL}/missions/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });

        // 2. JAVÍTVA: Újra lekérjük az adatokat, hogy frissüljenek a sorszámok
        await fetchData();
      } catch (err) {
        alert(t("errorDeleteMission"));
      }
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {t("manageMissions")}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate("/admin/missions/new")}
        >
          {t("newMission")}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {/* 2. HASZNÁLD A KOMPONENST */}
      <MissionTable
        missions={missions}
        starSystems={starSystems} // Átadjuk a rendszereket a nevek miatt
        loading={loading}
        isAdminView={true} // Admin nézet: látjuk a tulajdonost is
        storageKey="admin-missions"
        onEdit={(id) => navigate(`/admin/missions/${id}`)}
        onDelete={handleDelete}
        onForge={(id) => navigate(`/forge/${id}`)} // Itt navigálunk a Monaco-hoz
      />
    </Box>
  );
};

export default MissionList;
