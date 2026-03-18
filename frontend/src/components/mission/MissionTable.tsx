import React from "react";
import { Box, Button, Chip, Typography } from "@mui/material";
import { DataGrid, GridToolbar } from "@mui/x-data-grid";
import type { GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import { huHU } from "@mui/x-data-grid/locales";
import {
  Edit as EditIcon,
  Delete as DeleteIcon,
  Build as ForgeIcon,
} from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import type { MissionResponse } from "../../types/mission";
import type { StarSystemResponse } from "../../types/starSystem";
import "../../styles/RetroUI.css";

interface MissionTableProps {
  missions: MissionResponse[];
  starSystems: StarSystemResponse[];
  loading: boolean;
  isAdminView?: boolean;
  variant?: "modern" | "retro"; // Új paraméter a stílusváltáshoz
  onEdit: (id: string) => void;
  onDelete?: (id: string) => void;
  onForge?: (id: string) => void;
}

const MissionTable: React.FC<MissionTableProps> = ({
  missions,
  starSystems,
  loading,
  isAdminView = false,
  variant = "modern",
  onEdit,
  onDelete,
  onForge,
}) => {
  const { t } = useTranslation();
  const isRetro = variant === "retro";

  const getSystemName = (id: string) => {
    return starSystems.find((s) => s.id === id)?.name || id;
  };

  // --- Oszlop definíciók ---
  const columns: GridColDef[] = [
    {
      field: "name",
      headerName: t("missionName").toUpperCase(),
      flex: 1,
      minWidth: 200,
      renderCell: (params) => (
        <Box sx={{ display: "flex", alignItems: "center", height: "100%" }}>
          <Typography
            sx={{
              fontFamily: isRetro ? "'Share Tech Mono', monospace" : "inherit",
              fontWeight: isRetro ? "bold" : "normal",
            }}
          >
            {params.value}
          </Typography>
        </Box>
      ),
    },
    {
      field: "starSystemId",
      headerName: t("starSystem").toUpperCase(),
      flex: 1,
      minWidth: 150,
      valueGetter: (value: any) => getSystemName(value),
    },
    {
      field: "orderInSystem",
      headerName: "#",
      width: 80,
      type: "number",
      align: "center",
      headerAlign: "center",
    },
    {
      field: "difficulty",
      headerName: t("difficulty").toUpperCase(),
      width: 130,
      renderCell: (params: GridRenderCellParams) => {
        const colors: Record<string, "success" | "info" | "warning" | "error"> =
          {
            EASY: "success",
            MEDIUM: "info",
            HARD: "warning",
            EXPERT: "error",
          };
        return (
          <Chip
            label={t(params.value.toString().toLowerCase()).toUpperCase()}
            color={colors[params.value as string] || "default"}
            size="small"
            variant={isRetro ? "filled" : "outlined"}
            sx={{
              borderRadius: isRetro ? 0 : 1,
              fontFamily: isRetro ? "monospace" : "inherit",
            }}
          />
        );
      },
    },
    {
      field: "missionType",
      headerName: t("missionType").toUpperCase(),
      width: 150,
      renderCell: (params) => (
        <Box sx={{ display: "flex", alignItems: "center", height: "100%" }}>
          <Typography
            variant="body2"
            sx={{ fontFamily: isRetro ? "monospace" : "inherit" }}
          >
            {t(params.value.toString().toLowerCase()).toUpperCase()}
          </Typography>
        </Box>
      ),
    },
    ...(isAdminView
      ? [
          {
            field: "ownerUsername",
            headerName: t("user").toUpperCase(),
            width: 150,
          },
        ]
      : []),
    {
      field: "actions",
      headerName: t("actions").toUpperCase(),
      width: 160,
      sortable: false,
      filterable: false,
      renderCell: (params: GridRenderCellParams) => (
        <Box
          sx={{
            display: "flex",
            gap: 0.5,
            height: "100%",
            alignItems: "center",
          }}
        >
          {onForge && (
            <Button
              size="small"
              variant={isRetro ? "contained" : "text"}
              color="secondary"
              onClick={() => onForge(params.row.id)}
              data-cy="forge-btn"
              sx={{ minWidth: "30px", p: 0.5, borderRadius: isRetro ? 0 : 1 }}
            >
              <ForgeIcon fontSize="small" />
            </Button>
          )}
          <Button
            size="small"
            variant={isRetro ? "contained" : "text"}
            color="primary"
            onClick={() => onEdit(params.row.id)}
            sx={{ minWidth: "30px", p: 0.5, borderRadius: isRetro ? 0 : 1 }}
          >
            <EditIcon fontSize="small" />
          </Button>
          {onDelete && (
            <Button
              size="small"
              variant={isRetro ? "contained" : "text"}
              color="error"
              onClick={() => onDelete(params.row.id)}
              data-cy="delete-mission-btn"
              aria-label="delete-mission"
              sx={{ minWidth: "30px", p: 0.5, borderRadius: isRetro ? 0 : 1 }}
            >
              <DeleteIcon fontSize="small" />
            </Button>
          )}
        </Box>
      ),
    },
  ];

  // --- Dinamikus SX stílusok ---
  const modernSx = {
    bgcolor: "background.paper",
    boxShadow: 3,
    borderRadius: 2,
    border: "none",
    "& .MuiDataGrid-cell:focus": { outline: "none" },
    "& .MuiDataGrid-cell": {
      display: "flex",
      alignItems: "center",
      outline: "none !important",
    },
  };

  const retroSx = {
    bgcolor: "#0a0a0a",
    color: "#fff",
    border: "2px solid #333",
    fontFamily: "monospace",
    "& .MuiDataGrid-main": { borderBottom: "1px solid #333" },
    "& .MuiDataGrid-cell": {
      display: "flex",
      alignItems: "center",
      borderBottom: "1px solid #222",
      color: "#eee",
      fontFamily: "monospace",
    },
    "& .MuiDataGrid-columnHeaders": {
      display: "flex",
      alignItems: "center",
      bgcolor: "#1a1a1a",
      color: "#fff",
      borderBottom: "2px solid #fff",
      borderRadius: 0,
    },
    "& .MuiDataGrid-columnHeaderTitle": {
      fontWeight: "bold",
      letterSpacing: "1px",
    },
    "& .MuiDataGrid-footerContainer": {
      bgcolor: "#1a1a1a",
      color: "#fff",
      borderTop: "2px solid #fff",
    },
    "& .MuiTablePagination-root": { color: "#fff" },
    "& .MuiDataGrid-virtualScroller": { bgcolor: "#000" },
    "& .MuiDataGrid-toolbarContainer": { bgcolor: "#1a1a1a", p: 1 },
    "& .MuiButton-root": { color: "#fff", fontFamily: "monospace" },
    "& .MuiInputBase-root": { color: "#fff" },
  };

  return (
    <Box sx={{ height: 650, width: "100%" }}>
      <DataGrid
        rows={missions}
        columns={columns}
        loading={loading}
        slots={{ toolbar: GridToolbar }}
        slotProps={{ toolbar: { showQuickFilter: true } }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText}
        initialState={{
          pagination: { paginationModel: { pageSize: 10 } },
          sorting: {
            sortModel: [{ field: "orderInSystem", sort: "asc" }],
          },
        }}
        pageSizeOptions={[5, 10, 25, 100]}
        disableRowSelectionOnClick
        sx={isRetro ? retroSx : modernSx}
      />
    </Box>
  );
};

export default MissionTable;
