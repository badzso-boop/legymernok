import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Typography, Button, Alert } from "@mui/material";
import { Add as AddIcon } from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { StarSystemResponse } from "../../../types/starSystem";
import StarSystemTable from "../../../components/star-system/StarSystemTable";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const StarSystemList: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [starSystems, setStarSystems] = useState<StarSystemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const fetchStarSystems = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem("token");
      const response = await axios.get(`${API_URL}/star-systems`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      setStarSystems(response.data);
      setError(null);
    } catch (err) {
      setError(t("errorFetchStarSystems")); // "Nem sikerült betölteni a csillagrendszereket"
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStarSystems();
  }, []);

  const handleDelete = async (id: string, name: string) => {
    if (window.confirm(t("deleteStarSystemConfirm", { name }))) {
      try {
        const token = localStorage.getItem("token");
        await axios.delete(`${API_URL}/star-systems/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        fetchStarSystems(); // Frissítés
      } catch (err) {
        alert(t("errorDelete"));
      }
    }
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {t("starSystems")}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate("/admin/star-systems/new")}
        >
          {t("newStarSystem")}
        </Button>
      </Box>

      {error && <Alert severity="error">{error}</Alert>}

      <StarSystemTable
        systems={starSystems}
        loading={loading}
        variant="modern" // Itt a kék admin dizájn marad
        storageKey="admin-star-systems"
        onEdit={(id) => navigate(`/admin/star-systems/${id}`)}
        onDelete={handleDelete}
      />
    </Box>
  );
};

export default StarSystemList;
