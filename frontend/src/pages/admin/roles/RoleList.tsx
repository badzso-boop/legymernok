import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Button,
  LinearProgress,
  Alert,
  Chip,
  Stack,
  Tooltip,
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
import type { RoleResponse } from "../../../types/role";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const RoleList: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchRoles = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem("token");
      const response = await axios.get<RoleResponse[]>(`${API_URL}/roles`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      setRoles(response.data);
      setError(null);
    } catch (err) {
      setError(t("errorFetchRoles"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRoles();
  }, []);

  const handleDelete = async (id: string) => {
    if (window.confirm(t("deleteRoleConfirm"))) {
      try {
        const token = localStorage.getItem("token");
        await axios.delete(`${API_URL}/roles/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        fetchRoles(); // Újratöltés
      } catch (err) {
        alert(t("errorDeleteRole"));
      }
    }
  };

  const LoadingOverlay = () => (
    <Box sx={{ position: "absolute", top: 0, width: "100%" }}>
      <LinearProgress />
    </Box>
  );

  const columns: GridColDef[] = [
    { field: "name", headerName: t("roleName"), flex: 1, minWidth: 150 },
    {
      field: "description",
      headerName: t("description"),
      flex: 2,
      minWidth: 200,
    },
    {
      field: "permissions",
      headerName: t("permissions"),
      flex: 3,
      minWidth: 300,
      renderCell: (params: GridRenderCellParams) => {
        const perms = params.value as RoleResponse["permissions"];
        if (!perms || perms.length === 0) return "-";

        const LIMIT = 3; // Mennyit mutassunk?
        const visiblePerms = perms.slice(0, LIMIT);
        const remainingCount = perms.length - LIMIT;

        // Tooltip tartalma: az összes jog felsorolva
        const tooltipText = perms.map((p) => p.name).join(", ");

        return (
          <Tooltip title={tooltipText} arrow>
            <Stack
              direction="row"
              spacing={0.5}
              sx={{ overflow: "hidden", py: 1 }}
            >
              {visiblePerms.map((perm) => (
                <Chip
                  key={perm.id}
                  label={perm.name}
                  size="small"
                  variant="outlined"
                  // Opcionális: Ha hosszú a név, vágjuk le
                  sx={{ maxWidth: 150 }}
                />
              ))}
              {remainingCount > 0 && (
                <Chip
                  label={`+${remainingCount}`}
                  size="small"
                  color="primary"
                  variant="filled"
                />
              )}
            </Stack>
          </Tooltip>
        );
      },
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
            onClick={() => navigate(`/admin/roles/${params.row.id}`)}
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
          {t("manageRoles")}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate("/admin/roles/new")}
        >
          {t("newRole")}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <DataGrid
        rows={roles}
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

export default RoleList;
