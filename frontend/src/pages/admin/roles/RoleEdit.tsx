import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Paper,
  TextField,
  Button,
  IconButton,
  Grid,
  Divider,
  CircularProgress,
  Alert,
  FormGroup,
  FormControlLabel,
  Checkbox,
} from "@mui/material";
import {
  ArrowBack as ArrowBackIcon,
  Save as SaveIcon,
} from "@mui/icons-material";
import axios from "axios";
import { useTranslation } from "react-i18next";
import type { PermissionResponse, RoleResponse } from "../../../types/role";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const RoleEdit: React.FC = () => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isNew = !id;

  const [role, setRole] = useState({
    name: "",
    description: "",
    permissionIds: [] as string[],
  });
  const [allPermissions, setAllPermissions] = useState<PermissionResponse[]>(
    [],
  );
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const token = localStorage.getItem("token");
        const headers = { Authorization: `Bearer ${token}` };

        // 1. Összes elérhető jog lekérése
        const permsRes = await axios.get<PermissionResponse[]>(
          `${API_URL}/roles/permissions`,
          { headers },
        );
        setAllPermissions(permsRes.data);

        // 2. Ha szerkesztés, betöltjük a role adatait
        if (!isNew) {
          const roleRes = await axios.get<RoleResponse>(
            `${API_URL}/roles/${id}`,
            { headers },
          );
          setRole({
            name: roleRes.data.name,
            description: roleRes.data.description,
            permissionIds: roleRes.data.permissions.map((p) => p.id),
          });
        }
      } catch (err) {
        setError(t("errorFetchRoleDetails"));
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id, isNew]);

  const handleTogglePermission = (permId: string) => {
    setRole((prev) => {
      const exists = prev.permissionIds.includes(permId);
      return {
        ...prev,
        permissionIds: exists
          ? prev.permissionIds.filter((id) => id !== permId)
          : [...prev.permissionIds, permId],
      };
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const token = localStorage.getItem("token");
      const headers = { Authorization: `Bearer ${token}` };

      if (isNew) {
        await axios.post(`${API_URL}/roles`, role, { headers });
      } else {
        await axios.put(`${API_URL}/roles/${id}`, role, { headers });
      }
      navigate("/admin/roles");
    } catch (err: any) {
      setError(err.response?.data?.message || t("errorSaveRole"));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <CircularProgress />;

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", mb: 3, gap: 2 }}>
        <IconButton onClick={() => navigate("/admin/roles")} color="primary">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {isNew ? t("newRole") : t("editRole")}
        </Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper sx={{ p: 4 }}>
        <Grid container spacing={4}>
          <Grid size={{ xs: 12 }}>
            <TextField
              label={t("roleName")}
              fullWidth
              value={role.name}
              onChange={(e) => setRole({ ...role, name: e.target.value })}
              required
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              label={t("description")}
              fullWidth
              multiline
              rows={2}
              value={role.description}
              onChange={(e) =>
                setRole({ ...role, description: e.target.value })
              }
            />
          </Grid>

          <Grid size={{ xs: 12 }}>
            <Typography variant="h6" sx={{ mb: 2 }}>
              {t("permissions")}
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <FormGroup>
              <Grid container>
                {allPermissions.map((perm) => (
                  <Grid size={{ xs: 12, md: 6, lg: 4 }} key={perm.id}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={role.permissionIds.includes(perm.id)}
                          onChange={() => handleTogglePermission(perm.id)}
                        />
                      }
                      label={
                        <Box>
                          <Typography variant="body1" fontWeight="bold">
                            {perm.name}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {perm.description}
                          </Typography>
                        </Box>
                      }
                    />
                  </Grid>
                ))}
              </Grid>
            </FormGroup>
          </Grid>

          <Grid size={{ xs: 12 }}>
            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }}>
              <Button
                variant="outlined"
                onClick={() => navigate("/admin/roles")}
              >
                {t("cancel")}
              </Button>
              <Button
                variant="contained"
                startIcon={<SaveIcon />}
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? <CircularProgress size={24} /> : t("save")}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>
    </Box>
  );
};

export default RoleEdit;
