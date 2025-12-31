import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Tooltip,
  CircularProgress,
  Alert,
  Button,
} from "@mui/material";
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  Add as AddIcon,
} from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { StarSystemResponse } from "../../../types/starSystem";

const API_URL = "http://localhost:8080/api";

const StarSystemList: React.FC = () => {
  const { t } = useTranslation();
  const [starSystems, setStarSystems] = useState<StarSystemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const fetchStarSystems = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem("token");
      const response = await axios.get(`${API_URL}/star-systems`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      setStarSystems(response.data);
      setError(null);
    } catch (err: any) {
      setError(t("errorFetchStarSystems"));
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStarSystems();
  }, []);

  const handleDelete = async (id: string, name: string) => {
    if (window.confirm(t("deleteStarSystemConfirm", { name: name }))) {
      try {
        const token = localStorage.getItem("token");
        await axios.delete(`${API_URL}/star-systems/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        setStarSystems(starSystems.filter((system) => system.id !== id));
      } catch (err) {
        alert(t("errorDelete"));
      }
    }
  };

  const formatDate = (dateString: string) => {
    if (!dateString) return "-";
    return new Date(dateString).toLocaleDateString("hu-HU", {
      year: "numeric",
      month: "long",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  if (loading)
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
        <CircularProgress />
      </Box>
    );

  return (
    <Box>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 4,
        }}
      >
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

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper} elevation={3}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead sx={{ bgcolor: "rgba(255,255,255,0.05)" }}>
            <TableRow>
              <TableCell>{t("name")}</TableCell>
              <TableCell>{t("description")}</TableCell>
              {/*
                // IDE JÖNNE A KÜLDETÉSEK SZÁMA OSZLOP, HA A BACKEND TÁMOGATNÁ
                // Ehhez a `GET /api/star-systems` végpontnak vissza kellene adnia
                // egy `missionCount` mezőt minden csillagrendszernél.
                <TableCell align="center">Küldetések</TableCell>
              */}
              <TableCell>{t("createdAt")}</TableCell>
              <TableCell>{t("updatedAt")}</TableCell>
              <TableCell align="right">{t("actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {starSystems.map((system) => (
              <TableRow key={system.id} hover>
                <TableCell sx={{ fontWeight: "bold" }}>{system.name}</TableCell>
                <TableCell>
                  {system.description?.substring(0, 100) || ""}
                  {system.description && system.description.length > 100
                    ? "..."
                    : ""}
                </TableCell>
                {/* <TableCell align="center">{system.missionCount ?? 0}</TableCell> */}
                <TableCell>{formatDate(system.createdAt)}</TableCell>
                <TableCell>{formatDate(system.updatedAt)}</TableCell>
                <TableCell align="right">
                  <Box
                    sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}
                  >
                    <Tooltip title={t("edit")}>
                      <IconButton
                        color="primary"
                        onClick={() =>
                          navigate(`/admin/star-systems/${system.id}`)
                        }
                      >
                        <EditIcon />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={t("delete")}>
                      <IconButton
                        color="error"
                        onClick={() => handleDelete(system.id, system.name)}
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default StarSystemList;
