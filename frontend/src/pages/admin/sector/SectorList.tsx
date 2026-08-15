import React, { useEffect, useState } from "react";
import {
  Box,
  Typography,
  LinearProgress,
  Alert,
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
import { Edit as EditIcon, Delete as DeleteIcon, Add as AddIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import { sectorApi } from "../../../api/client";
import type { SectorResponse } from "../../../types/sector";
import { useDataGridPreferences } from "../../../hooks/useDataGridPreferences";

/**
 * Admin Szektor-kezelő — a FeatureFlagList mintáját követi (egy oldal,
 * DataGrid + dialog-alapú create/edit), nem a StarSystemList/Edit
 * kétoldalas mintáját, mert a Sector adatmodellje minimális (name/
 * description/iconUrl, nincsenek beágyazott elemek). Ld.
 * plans/sector_map_2026.md.
 */
const SectorList: React.FC = () => {
  const { t } = useTranslation();
  const gridPrefs = useDataGridPreferences("admin-sectors", 25);

  const [sectors, setSectors] = useState<SectorResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [snackMsg, setSnackMsg] = useState<string | null>(null);

  const [editingSector, setEditingSector] = useState<SectorResponse | "new" | null>(null);
  const [nameDraft, setNameDraft] = useState("");
  const [descriptionDraft, setDescriptionDraft] = useState("");
  const [iconUrlDraft, setIconUrlDraft] = useState("");
  const [saving, setSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<SectorResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const fetchSectors = async () => {
    try {
      setLoading(true);
      const data = await sectorApi.getAll();
      setSectors(data);
      setError(null);
    } catch {
      setError(t("errorFetchSectors"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSectors();
  }, []);

  const openCreateDialog = () => {
    setEditingSector("new");
    setNameDraft("");
    setDescriptionDraft("");
    setIconUrlDraft("");
  };

  const openEditDialog = (sector: SectorResponse) => {
    setEditingSector(sector);
    setNameDraft(sector.name);
    setDescriptionDraft(sector.description ?? "");
    setIconUrlDraft(sector.iconUrl ?? "");
  };

  const closeDialog = () => {
    setEditingSector(null);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = { name: nameDraft, description: descriptionDraft, iconUrl: iconUrlDraft };
      if (editingSector === "new") {
        await sectorApi.create(payload);
      } else if (editingSector) {
        await sectorApi.update(editingSector.id, payload);
      }
      closeDialog();
      await fetchSectors();
    } catch {
      setSnackMsg(t("errorSaveSector"));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await sectorApi.delete(deleteTarget.id);
      setDeleteTarget(null);
      await fetchSectors();
    } catch {
      setSnackMsg(t("errorDeleteSector"));
    } finally {
      setDeleting(false);
    }
  };

  const LoadingOverlay = () => (
    <Box sx={{ position: "absolute", top: 0, width: "100%" }}>
      <LinearProgress />
    </Box>
  );

  const columns: GridColDef[] = [
    { field: "name", headerName: t("sectorName"), flex: 1, minWidth: 200 },
    {
      field: "description",
      headerName: t("description"),
      flex: 2,
      minWidth: 300,
    },
    {
      field: "starSystemCount",
      headerName: t("sectorStarSystemCount"),
      width: 160,
    },
    {
      field: "actions",
      headerName: t("actions"),
      width: 120,
      sortable: false,
      filterable: false,
      renderCell: (params: GridRenderCellParams) => (
        <>
          <IconButton
            size="small"
            color="primary"
            onClick={() => openEditDialog(params.row as SectorResponse)}
          >
            <EditIcon fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            color="error"
            onClick={() => setDeleteTarget(params.row as SectorResponse)}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </>
      ),
    },
  ];

  return (
    <Box sx={{ height: 650, width: "100%" }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h4" sx={{ fontWeight: "bold" }}>
          {t("manageSectors")}
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreateDialog}>
          {t("createSector")}
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <DataGrid
        rows={sectors}
        columns={columns}
        loading={loading}
        getRowId={(row) => row.id}
        slots={{ loadingOverlay: LoadingOverlay, toolbar: GridToolbar }}
        slotProps={{ toolbar: { showQuickFilter: true } }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText}
        paginationModel={gridPrefs.paginationModel}
        onPaginationModelChange={gridPrefs.onPaginationModelChange}
        filterModel={gridPrefs.filterModel}
        onFilterModelChange={gridPrefs.onFilterModelChange}
        sortModel={gridPrefs.sortModel}
        onSortModelChange={gridPrefs.onSortModelChange}
        disableRowSelectionOnClick
        sx={{
          bgcolor: "background.paper",
          boxShadow: 3,
          borderRadius: 2,
          border: "none",
        }}
      />

      <Dialog open={editingSector !== null} onClose={closeDialog} fullWidth maxWidth="sm">
        <DialogTitle>
          {editingSector === "new" ? t("createSector") : t("editSector")}
        </DialogTitle>
        <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <TextField
            autoFocus
            label={t("sectorName")}
            fullWidth
            value={nameDraft}
            onChange={(e) => setNameDraft(e.target.value)}
          />
          <TextField
            label={t("description")}
            fullWidth
            multiline
            rows={3}
            value={descriptionDraft}
            onChange={(e) => setDescriptionDraft(e.target.value)}
          />
          <TextField
            label={t("iconUrl")}
            fullWidth
            value={iconUrlDraft}
            onChange={(e) => setIconUrlDraft(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>{t("cancel")}</Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={saving || nameDraft.trim() === ""}
          >
            {t("save")}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteTarget !== null} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>{t("deleteSectorConfirmTitle")}</DialogTitle>
        <DialogContent>
          <Typography>
            {t("deleteSectorConfirmBody", { name: deleteTarget?.name })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>{t("cancel")}</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={deleting}>
            {t("delete")}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackMsg !== null}
        autoHideDuration={4000}
        onClose={() => setSnackMsg(null)}
        message={snackMsg}
      />
    </Box>
  );
};

export default SectorList;
