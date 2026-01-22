import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Button,
  LinearProgress,
  Alert,
  Avatar,
  Chip,
  Stack,
} from "@mui/material";
import { DataGrid, GridToolbar } from "@mui/x-data-grid";
import type { GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import { huHU } from "@mui/x-data-grid/locales";
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Person as PersonIcon,
} from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { UserResponse } from "../../../types/user";
import { useAuth } from "../../../context/AuthContext";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const UserList: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hasRole, isLoading: isAuthLoading } = useAuth();

  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchUsers = async () => {
    try {
      setLoading(true);
      const token = localStorage.getItem("token");
      const response = await axios.get(`${API_URL}/users`, {
        headers: { Authorization: `Bearer ${token}` },
      });

      setUsers(response.data);
      setError(null);
    } catch (err: any) {
      setError(t("errorFetchUsers"));
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isAuthLoading) {
      setLoading(true);
      return;
    }

    if (hasRole("ROLE_ADMIN")) {
      fetchUsers();
    } else {
      setError("Nincs jogosultságod ennek az oldalnak a megtekintéséhez.");
      setLoading(false);
    }
  }, [isAuthLoading]);

  const handleDelete = async (id: string, username: string) => {
    if (
      window.confirm(
        t("deleteConfirm", { username }), // "Biztosan törölni szeretnéd {{username}} felhasználót?"
      )
    ) {
      try {
        const token = localStorage.getItem("token");
        await axios.delete(`${API_URL}/users/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        // Újratöltés helyett lokális szűrés is elég lehet, de a fetch biztosabb
        fetchUsers();
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

  const LoadingOverlay = () => (
    <Box sx={{ position: "absolute", top: 0, width: "100%" }}>
      <LinearProgress />
    </Box>
  );

  const columns: GridColDef[] = [
    {
      field: "avatarUrl",
      headerName: "",
      width: 60,
      sortable: false,
      filterable: false,
      align: "center",
      renderCell: (params: GridRenderCellParams) => (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            height: "100%",
            justifyContent: "center",
          }}
        >
          <Avatar src={params.value as string} sx={{ width: 32, height: 32 }}>
            <PersonIcon fontSize="small" />
          </Avatar>
        </Box>
      ),
    },
    { field: "username", headerName: t("username"), flex: 1, minWidth: 120 },
    { field: "fullName", headerName: t("fullName"), flex: 1, minWidth: 150 },
    { field: "email", headerName: t("email"), flex: 1.5, minWidth: 200 },
    {
      field: "roles",
      headerName: t("roles"),
      flex: 2,
      minWidth: 200,
      sortable: false, // Tömböt nehéz rendezni
      renderCell: (params: GridRenderCellParams) => (
        <Stack
          direction="row"
          spacing={0.5}
          sx={{ height: "100%", alignItems: "center", overflow: "hidden" }}
        >
          {(params.value as string[]).map((role, index) => (
            <Chip
              key={index}
              label={role.replace("ROLE_", "")}
              size="small"
              color={role === "ROLE_ADMIN" ? "secondary" : "default"}
              variant="outlined"
            />
          ))}
        </Stack>
      ),
    },
    {
      field: "createdAt",
      headerName: t("registered"),
      width: 160,
      valueFormatter: (value: any) => formatDate(value as string),
    },
    {
      field: "updatedAt",
      headerName: t("lastModified"),
      width: 160,
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
            onClick={() => navigate(`/admin/users/${params.row.id}`)}
            style={{ minWidth: "30px", padding: "5px" }}
          >
            <EditIcon fontSize="small" />
          </Button>
          <Button
            size="small"
            color="error"
            onClick={() => handleDelete(params.row.id, params.row.username)}
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
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 2,
        }}
      >
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {t("manageUsers")}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate("/admin/users/new")}
        >
          {t("newMission").replace("küldetés", "felhasználó")}{" "}
          {/* Vagy külön kulcs: t("newUser") */}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <DataGrid
        rows={users}
        columns={columns}
        loading={loading}
        slots={{
          loadingOverlay: LoadingOverlay,
          toolbar: GridToolbar,
        }}
        slotProps={{
          toolbar: {
            showQuickFilter: true,
          },
        }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText}
        initialState={{
          pagination: { paginationModel: { pageSize: 10 } },
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

export default UserList;
