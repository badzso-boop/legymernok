import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Button,
  LinearProgress,
  Alert,
  Chip,
} from "@mui/material";
import { DataGrid, GridToolbar } from "@mui/x-data-grid";
import type { GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import { huHU } from "@mui/x-data-grid/locales";
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
} from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { MissionResponse } from "../../../types/mission";
import type { StarSystemResponse } from "../../../types/starSystem";

// API URL (env-ből vagy fallback)
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

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

  // Segédfüggvény: ID alapján név keresése
  const getSystemName = (id: string) => {
    return starSystems.find((s) => s.id === id)?.name || id;
  };

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

  const LoadingOverlay = () => (
    <Box sx={{ position: "absolute", top: 0, width: "100%" }}>
      <LinearProgress />
    </Box>
  );

  // Oszlopok definíciója
  const columns: GridColDef[] = [
    { field: "name", headerName: t("missionName"), flex: 1, minWidth: 200 },
    {
      field: "starSystemId",
      headerName: t("starSystem"),
      flex: 1,
      minWidth: 150,
      valueGetter: (params: any) => getSystemName(params), // ID helyett név
    },
    {
      field: "orderInSystem",
      headerName: t("orderInSystem"),
      width: 100,
      type: "number",
      align: "center",
      headerAlign: "center",
    },
    {
      field: "difficulty",
      headerName: t("difficulty"),
      width: 130,
      renderCell: (params: GridRenderCellParams) => {
        console.log(params);
        const colors: Record<string, "success" | "warning" | "error" | "info"> =
          {
            EASY: "success",
            MEDIUM: "info",
            HARD: "warning",
            EXPERT: "error",
          };
        return (
          <Chip
            label={t(params.value.toString().toLowerCase())}
            color={colors[params.value as string] || "default"}
            size="small"
            variant="outlined"
          />
        );
      },
    },
    {
      field: "missionType",
      headerName: t("missionType"),
      width: 150,
      renderCell: (params) => t(params.value.toString().toLowerCase()),
    },
    {
      field: "actions",
      headerName: t("actions"),
      width: 120,
      sortable: false,
      filterable: false,
      renderCell: (params: GridRenderCellParams) => (
        <Box>
          <Button
            size="small"
            color="primary"
            onClick={() => navigate(`/admin/missions/${params.row.id}`)}
            style={{ minWidth: "30px", padding: "5px" }}
          >
            <EditIcon fontSize="small" />
          </Button>
          <Button
            size="small"
            color="error"
            onClick={() => handleDelete(params.row.id)}
            style={{ minWidth: "30px", padding: "5px" }}
          >
            <DeleteIcon fontSize="small" />
          </Button>
        </Box>
      ),
    },
  ];

  return (
    <Box sx={{ height: 650, width: "100%" }}>
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

      <DataGrid
        rows={missions}
        columns={columns}
        loading={loading}
        slots={{
          loadingOverlay: LoadingOverlay, // A fenti wrapper használata
          toolbar: GridToolbar,
        }}
        slotProps={{
          toolbar: {
            showQuickFilter: true, // Keresőmező
          },
        }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText} // Magyarítás
        initialState={{
          pagination: { paginationModel: { pageSize: 10 } },
          // Csoportosítás helyett alapból rendezzük Rendszer majd Sorszám szerint
          sorting: {
            sortModel: [{ field: "starSystemId", sort: "asc" }],
          },
        }}
        pageSizeOptions={[5, 10, 25, 100]}
        disableRowSelectionOnClick
        sx={{
          bgcolor: "background.paper",
          boxShadow: 3,
          borderRadius: 2,
          border: "none",
          "& .MuiDataGrid-cell:focus": { outline: "none" },
        }}
      />
    </Box>
  );
};

export default MissionList;
