import React, { useEffect, useState } from "react";
import {
  Box,
  Typography,
  LinearProgress,
  Alert,
  Switch,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Snackbar,
} from "@mui/material";
import { DataGrid, GridToolbar } from "@mui/x-data-grid";
import type { GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import { huHU } from "@mui/x-data-grid/locales";
import { Edit as EditIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import { featureFlagApi } from "../../../api/client";
import type { FeatureFlagResponse } from "../../../types/featureFlag";

const FeatureFlagList: React.FC = () => {
  const { t } = useTranslation();

  const [flags, setFlags] = useState<FeatureFlagResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);

  const [editingFlag, setEditingFlag] = useState<FeatureFlagResponse | null>(
    null,
  );
  const [descriptionDraft, setDescriptionDraft] = useState("");
  const [saving, setSaving] = useState(false);

  const fetchFlags = async () => {
    try {
      setLoading(true);
      const data = await featureFlagApi.getAll();
      setFlags(data);
      setError(null);
    } catch (err) {
      setError(t("errorFetchFeatureFlags"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFlags();
  }, []);

  const handleToggle = async (flag: FeatureFlagResponse) => {
    const nextEnabled = !flag.enabled;
    // Optimista UI-frissítés, hiba esetén visszaállítjuk.
    setFlags((prev) =>
      prev.map((f) => (f.key === flag.key ? { ...f, enabled: nextEnabled } : f)),
    );
    try {
      const updated = await featureFlagApi.update(flag.key, {
        enabled: nextEnabled,
        description: flag.description,
      });
      setFlags((prev) => prev.map((f) => (f.key === flag.key ? updated : f)));
    } catch (err) {
      setFlags((prev) =>
        prev.map((f) => (f.key === flag.key ? { ...f, enabled: flag.enabled } : f)),
      );
      setToggleError(t("errorUpdateFeatureFlag"));
    }
  };

  const openEditDialog = (flag: FeatureFlagResponse) => {
    setEditingFlag(flag);
    setDescriptionDraft(flag.description ?? "");
  };

  const closeEditDialog = () => {
    setEditingFlag(null);
    setDescriptionDraft("");
  };

  const handleSaveDescription = async () => {
    if (!editingFlag) return;
    setSaving(true);
    try {
      const updated = await featureFlagApi.update(editingFlag.key, {
        enabled: editingFlag.enabled,
        description: descriptionDraft,
      });
      setFlags((prev) =>
        prev.map((f) => (f.key === editingFlag.key ? updated : f)),
      );
      closeEditDialog();
    } catch (err) {
      setToggleError(t("errorUpdateFeatureFlag"));
    } finally {
      setSaving(false);
    }
  };

  const LoadingOverlay = () => (
    <Box sx={{ position: "absolute", top: 0, width: "100%" }}>
      <LinearProgress />
    </Box>
  );

  const columns: GridColDef[] = [
    { field: "key", headerName: t("featureFlagKey"), flex: 1, minWidth: 200 },
    {
      field: "description",
      headerName: t("description"),
      flex: 2,
      minWidth: 300,
    },
    {
      field: "enabled",
      headerName: t("enabled"),
      width: 140,
      sortable: false,
      filterable: false,
      renderCell: (params: GridRenderCellParams) => (
        <Switch
          checked={Boolean(params.row.enabled)}
          onChange={() => handleToggle(params.row as FeatureFlagResponse)}
          color="primary"
        />
      ),
    },
    {
      field: "actions",
      headerName: t("actions"),
      width: 100,
      sortable: false,
      filterable: false,
      renderCell: (params: GridRenderCellParams) => (
        <IconButton
          size="small"
          color="primary"
          onClick={() => openEditDialog(params.row as FeatureFlagResponse)}
        >
          <EditIcon fontSize="small" />
        </IconButton>
      ),
    },
  ];

  return (
    <Box sx={{ height: 650, width: "100%" }}>
      <Typography variant="h4" sx={{ fontWeight: "bold", mb: 2 }}>
        {t("manageFeatureFlags")}
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <DataGrid
        rows={flags}
        columns={columns}
        loading={loading}
        getRowId={(row) => row.key}
        slots={{ loadingOverlay: LoadingOverlay, toolbar: GridToolbar }}
        slotProps={{ toolbar: { showQuickFilter: true } }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText}
        initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
        disableRowSelectionOnClick
        sx={{
          bgcolor: "background.paper",
          boxShadow: 3,
          borderRadius: 2,
          border: "none",
        }}
      />

      <Dialog open={editingFlag !== null} onClose={closeEditDialog} fullWidth maxWidth="sm">
        <DialogTitle>
          {editingFlag ? `${t("editFeatureFlag")} — ${editingFlag.key}` : ""}
        </DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label={t("description")}
            fullWidth
            multiline
            rows={3}
            value={descriptionDraft}
            onChange={(e) => setDescriptionDraft(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeEditDialog}>{t("cancel")}</Button>
          <Button
            variant="contained"
            onClick={handleSaveDescription}
            disabled={saving}
          >
            {t("save")}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={toggleError !== null}
        autoHideDuration={4000}
        onClose={() => setToggleError(null)}
        message={toggleError}
      />
    </Box>
  );
};

export default FeatureFlagList;
