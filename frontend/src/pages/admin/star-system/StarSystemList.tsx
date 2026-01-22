import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Typography, Button, LinearProgress, Alert } from "@mui/material";
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
import type { StarSystemResponse } from "../../../types/starSystem";

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

  const LoadingOverlay = () => (
    <Box sx={{ position: "absolute", top: 0, width: "100%" }}>
      <LinearProgress />
    </Box>
  );

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

  const columns: GridColDef[] = [
    { field: "name", headerName: t("name"), flex: 1, minWidth: 150 },
    {
      field: "description",
      headerName: t("description"),
      flex: 2,
      minWidth: 200,
    },
    {
      field: "createdAt",
      headerName: t("createdAt"),
      width: 180,
      valueFormatter: (value: any) => formatDate(value as string),
    },
    {
      field: "updatedAt",
      headerName: t("updatedAt"),
      width: 180,
      valueFormatter: (value: any) => formatDate(value as string),
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
            onClick={() => navigate(`/admin/star-systems/${params.row.id}`)}
            style={{ minWidth: "30px", padding: "5px" }}
            aria-label={t("edit")}
          >
            <EditIcon fontSize="small" />
          </Button>
          <Button
            size="small"
            color="error"
            onClick={() => handleDelete(params.row.id, params.row.name)}
            style={{ minWidth: "30px", padding: "5px" }}
            aria-label={t("delete")}
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

      <DataGrid
        rows={starSystems}
        columns={columns}
        loading={loading}
        slots={{ loadingOverlay: LoadingOverlay, toolbar: GridToolbar }}
        slotProps={{ toolbar: { showQuickFilter: true } }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText}
        initialState={{ pagination: { paginationModel: { pageSize: 10 } } }}
        pageSizeOptions={[5, 10, 25]}
        disableRowSelectionOnClick
        sx={{
          bgcolor: "background.paper",
          boxShadow: 3,
          borderRadius: 2,
          border: "none",
        }}
      />
    </Box>
  );
};

export default StarSystemList;
