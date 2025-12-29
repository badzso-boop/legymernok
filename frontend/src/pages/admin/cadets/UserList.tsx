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
  Avatar,
  IconButton,
  Chip,
  Tooltip,
  CircularProgress,
  Alert,
  Button,
} from "@mui/material";
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  Person as PersonIcon,
  Add as AddIcon,
} from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { UserResponse } from "../../../types/user";
import { useAuth } from "../../../context/AuthContext";

// API alap URL (a .env fájlból vagy fixen, ha nincs)
const API_URL = "http://localhost:8080/api";

const UserList: React.FC = () => {
  const { t } = useTranslation();
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const { hasRole, isLoading: isAuthLoading } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

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
      setError("Nem sikerült betölteni a felhasználókat.");
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
      window.confirm(`Biztosan törölni szeretnéd ${username} felhasználót?`)
    ) {
      try {
        const token = localStorage.getItem("token");
        await axios.delete(`${API_URL}/users/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        setUsers(users.filter((user) => user.id !== id));
      } catch (err) {
        alert("Hiba történt a törlés során.");
      }
    }
  };

  const formatDate = (dateString: string) => {
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
          {t("manageUsers")}
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate("/admin/users/new")}
        >
          Új felhasználó
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
              <TableCell>{t("user")}</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>{t("registered")}</TableCell>
              <TableCell>{t("lastModified")}</TableCell>
              <TableCell>{t("roles")}</TableCell>
              <TableCell align="right">{t("actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id} hover>
                <TableCell>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                    <Avatar src={user.avatarUrl || undefined}>
                      <PersonIcon />
                    </Avatar>
                    <Box>
                      <Typography
                        variant="subtitle2"
                        sx={{ fontWeight: "bold" }}
                      >
                        {user.fullName || user.username}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {user.fullName
                          ? `@${user.username}`
                          : t("noNameProvided")}
                      </Typography>
                    </Box>
                  </Box>
                </TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>{formatDate(user.createdAt)}</TableCell>
                <TableCell>{formatDate(user.updatedAt)}</TableCell>
                <TableCell>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                    {user.roles.map((role, index) => (
                      <Chip
                        key={index} // Mivel string, használhatjuk az index vagy magát a stringet key-nek
                        label={role.toString().replace("ROLE_", "")}
                        size="small"
                        color={
                          role.toString() === "ROLE_ADMIN"
                            ? "secondary"
                            : "default"
                        }
                        variant="outlined"
                      />
                    ))}
                  </Box>
                </TableCell>
                <TableCell align="right">
                  <Box
                    sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}
                  >
                    <Tooltip title={t("edit")}>
                      <IconButton
                        color="primary"
                        onClick={() => navigate(`/admin/users/${user.id}`)}
                      >
                        <EditIcon />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={t("delete")}>
                      <IconButton
                        color="error"
                        onClick={() => handleDelete(user.id, user.username)}
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

export default UserList;
