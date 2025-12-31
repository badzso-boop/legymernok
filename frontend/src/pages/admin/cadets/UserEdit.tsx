import React, { useEffect, useState, type ChangeEvent } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Paper,
  TextField,
  Button,
  IconButton,
  Grid,
  Avatar,
  Divider,
  CircularProgress,
  Alert,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  type SelectChangeEvent,
} from "@mui/material";
import {
  ArrowBack as ArrowBackIcon,
  Save as SaveIcon,
  Person as PersonIcon,
} from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { Role, UserResponse } from "../../../types/user";

const API_URL = "http://localhost:8080/api";

const AVAILABLE_ROLES: Role[] = ["ROLE_CADET", "ROLE_ADMIN"];

const UserEdit: React.FC = () => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const isNew = !id;
  const navigate = useNavigate();

  const [user, setUser] = useState<
    Partial<Omit<UserResponse, "roles"> & { password?: string; role?: Role }>
  >({});
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isNew) {
      const fetchUser = async () => {
        try {
          setLoading(true);
          const token = localStorage.getItem("token");
          const response = await axios.get<UserResponse>(
            `${API_URL}/users/${id}`,
            {
              headers: { Authorization: `Bearer ${token}` },
            }
          );
          // A backend 'roles' tömbjéből az elsőt vesszük a legördülőhöz
          setUser({ ...response.data, role: response.data.roles[0] });
        } catch (err) {
          setError(t("errorFetchUserDetails"));
        } finally {
          setLoading(false);
        }
      };
      fetchUser();
    } else {
      setUser({
        fullName: "",
        username: "",
        email: "",
        password: "",
        role: "ROLE_CADET",
      });
    }
  }, [id, isNew]);

  const handleChange = (
    e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => {
    const { name, value } = e.target;
    setUser((prev) => ({ ...prev, [name]: value }));
  };

  const handleRoleChange = (event: SelectChangeEvent<string>) => {
    setUser((prev) => ({ ...prev, role: event.target.value as Role }));
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);

    if (!user?.username || !user.email || (isNew && !user.password)) {
      setError(
        "Felhasználónév, email és (új felhasználónál) jelszó megadása kötelező."
      );
      setSaving(false);
      return;
    }

    const payload = {
      username: user.username,
      email: user.email,
      password: user.password,
      role: user.role ? user.role : "",
      fullName: user.fullName,
    };

    try {
      const token = localStorage.getItem("token");

      if (isNew) {
        await axios.post(`${API_URL}/users`, payload, {
          headers: { Authorization: `Bearer ${token}` },
        });
      } else {
        await axios.put(`${API_URL}/users/${id}`, payload, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }

      navigate("/admin/users");
    } catch (err: any) {
      setError(err.response?.data?.message || "Hiba történt mentés közben.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <CircularProgress />;
  if (error)
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {error}
      </Alert>
    );
  if (!user) return <Alert severity="warning">{t("userNotFound")}</Alert>;

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", mb: 3, gap: 2 }}>
        <IconButton onClick={() => navigate("/admin/cadets")} color="primary">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {isNew ? "Új kadét létrehozása" : t("editUser")}
        </Typography>
      </Box>

      <Paper sx={{ p: 4 }}>
        <Grid container spacing={4}>
          <Grid
            size={{ xs: 12, md: 4 }}
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              textAlign: "center",
            }}
          >
            <Avatar
              src={user.avatarUrl || undefined}
              sx={{ width: 150, height: 150, mb: 2 }}
            >
              <PersonIcon sx={{ fontSize: 80 }} />
            </Avatar>
            <Typography variant="h6">{user.username}</Typography>
            <Typography color="text.secondary">{user.email}</Typography>
          </Grid>

          <Grid size={{ xs: 12, md: 8 }}>
            <Typography variant="h6" gutterBottom>
              {t("basicInfo")}
            </Typography>
            <Divider sx={{ mb: 3 }} />
            <Grid container spacing={2}>
              <Grid size={{ xs: 12 }}>
                <TextField
                  name="fullName"
                  fullWidth
                  label={t("fullName")}
                  value={user.fullName || ""}
                  placeholder="Nincs megadva"
                  onChange={handleChange}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  name="username"
                  label={t("username")}
                  value={user.username || ""}
                  onChange={handleChange}
                  fullWidth
                  disabled={!isNew}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  name="email"
                  label={t("email")}
                  value={user.email || ""}
                  onChange={handleChange}
                  fullWidth
                />
              </Grid>
              {isNew && (
                <Grid size={{ xs: 12 }}>
                  <TextField
                    name="password"
                    label="Jelszó"
                    type="password"
                    value={user.password || ""}
                    onChange={handleChange}
                    fullWidth
                    helperText="Csak új felhasználó létrehozásakor kötelező."
                  />
                </Grid>
              )}
              <Grid size={{ xs: 12 }}>
                <FormControl fullWidth>
                  <InputLabel id="role-select-label">Szerepkör</InputLabel>
                  <Select
                    labelId="role-select-label"
                    value={user.role || ""}
                    label="Szerepkör"
                    onChange={handleRoleChange}
                  >
                    {AVAILABLE_ROLES.map((role) => (
                      <MenuItem key={role} value={role}>
                        {role.replace("ROLE_", "")}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            </Grid>

            <Box
              sx={{
                mt: 4,
                display: "flex",
                justifyContent: "flex-end",
                gap: 2,
              }}
            >
              <Button
                variant="outlined"
                onClick={() => navigate("/admin/users")}
              >
                {t("cancel")}
              </Button>
              <Button
                variant="contained"
                color="primary"
                startIcon={<SaveIcon />}
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? (
                  <CircularProgress size={24} />
                ) : isNew ? (
                  "Létrehozás"
                ) : (
                  t("save")
                )}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>
    </Box>
  );
};

export default UserEdit;
