import React from "react";
import { Box, Button, Typography } from "@mui/material";
import { DataGrid, GridToolbar } from "@mui/x-data-grid";
import type { GridColDef, GridRenderCellParams } from "@mui/x-data-grid";
import { huHU } from "@mui/x-data-grid/locales";
import { Edit as EditIcon, Delete as DeleteIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import type { StarSystemResponse } from "../../types/starSystem";

interface StarSystemTableProps {
  systems: StarSystemResponse[];
  loading: boolean;
  variant?: "modern" | "retro";
  onEdit: (id: string) => void;
  onDelete?: (id: string, name: string) => void;
}

const StarSystemTable: React.FC<StarSystemTableProps> = ({
  systems,
  loading,
  variant = "modern",
  onEdit,
  onDelete,
}) => {
  const { t } = useTranslation();
  const isRetro = variant === "retro";

  const formatDate = (dateString: string) => {
    if (!dateString) return "-";
    return new Date(dateString).toLocaleDateString("hu-HU", {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  };

  const columns: GridColDef[] = [
    {
      field: "name",
      headerName: t("name").toUpperCase(),
      flex: 1,
      minWidth: 150,
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
      field: "description",
      headerName: t("description").toUpperCase(),
      flex: 2,
      minWidth: 200,
    },
    // Admin nézetben (modern variáns) mutathatjuk a dátumokat is
    ...(!isRetro
      ? [
          {
            field: "createdAt",
            headerName: t("createdAt").toUpperCase(),
            width: 180,
            valueFormatter: (value: any) => formatDate(value as string),
          },
        ]
      : []),
    {
      field: "actions",
      headerName: t("actions").toUpperCase(),
      width: 120,
      sortable: false,
      renderCell: (params: GridRenderCellParams) => (
        <Box
          sx={{ display: "flex", gap: 1, height: "100%", alignItems: "center" }}
        >
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
              onClick={() => onDelete(params.row.id, params.row.name)}
              sx={{ minWidth: "30px", p: 0.5, borderRadius: isRetro ? 0 : 1 }}
              aria-label="delete"
            >
              <DeleteIcon fontSize="small" />
            </Button>
          )}
        </Box>
      ),
    },
  ];

  // --- Stílusok ---
  const modernSx = {
    bgcolor: "background.paper",
    boxShadow: 3,
    borderRadius: 2,
    border: "none",
    "& .MuiDataGrid-cell:focus": { outline: "none" },
  };

  const retroSx = {
    bgcolor: "#0a0a0a",
    color: "#fff",
    border: "2px solid #333",
    fontFamily: "monospace",
    "& .MuiDataGrid-cell": { borderBottom: "1px solid #222", color: "#eee" },
    "& .MuiDataGrid-columnHeaders": {
      bgcolor: "#1a1a1a",
      borderBottom: "2px solid #fff",
      borderRadius: 0,
    },
    "& .MuiDataGrid-footerContainer": {
      bgcolor: "#1a1a1a",
      borderTop: "2px solid #fff",
    },
    "& .MuiTablePagination-root": { color: "#fff" },
    "& .MuiDataGrid-virtualScroller": { bgcolor: "#000" },
    "& .MuiDataGrid-toolbarContainer": { bgcolor: "#1a1a1a", p: 1 },
    "& .MuiButton-root": { color: "#fff" },
  };

  return (
    <Box sx={{ height: 450, width: "100%" }}>
      <DataGrid
        rows={systems}
        columns={columns}
        loading={loading}
        sx={isRetro ? retroSx : modernSx}
        slots={{ toolbar: GridToolbar }}
        slotProps={{ toolbar: { showQuickFilter: true } }}
        localeText={huHU.components.MuiDataGrid.defaultProps.localeText}
        initialState={{ pagination: { paginationModel: { pageSize: 5 } } }}
        pageSizeOptions={[5, 10, 25]}
        disableRowSelectionOnClick
      />
    </Box>
  );
};

export default StarSystemTable;
