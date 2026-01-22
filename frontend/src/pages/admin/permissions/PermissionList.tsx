import React, { useEffect, useState } from "react";
import { Box, Typography, LinearProgress, Alert } from "@mui/material";
import { DataGrid, GridToolbar } from "@mui/x-data-grid";
import { huHU } from "@mui/x-data-grid/locales";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { PermissionResponse } from "../../../types/role";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const PermissionList: React.FC = () => {
  const { t } = useTranslation();
  const [permissions, setPermissions] = useState<PermissionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchPermissions = async () => {
      try {
        const token = localStorage.getItem("token");
        const response = await axios.get(`${API_URL}/roles/permissions`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        setPermissions(response.data);
      } catch (err) {
        setError(t("errorFetchPermissions"));
      } finally {
        setLoading(false);
      }
    };
    fetchPermissions();
  }, []);

  const LoadingOverlay = () => (
    <Box sx={{ position: "absolute", top: 0, width: "100%" }}>
      <LinearProgress />
    </Box>
  );

  const columns = [
    { field: "name", headerName: t("permissionName"), flex: 1, minWidth: 200 },
    {
      field: "description",
      headerName: t("description"),
      flex: 2,
      minWidth: 300,
    },
  ];

  return (
    <Box sx={{ height: 650, width: "100%" }}>
      <Typography variant="h4" sx={{ fontWeight: "bold", mb: 2 }}>
        {t("managePermissions")}
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <DataGrid
        rows={permissions}
        columns={columns}
        loading={loading}
        slots={{ loadingOverlay: LoadingOverlay, toolbar: GridToolbar }}
        slotProps={{ toolbar: { showQuickFilter: true } }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText}
        initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
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

export default PermissionList;
