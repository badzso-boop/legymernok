import React from "react";
import {
  Box,
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Button,
  CircularProgress,
  IconButton,
  Tooltip,
} from "@mui/material";
import DeleteSweepIcon from "@mui/icons-material/DeleteSweep";
import RefreshIcon from "@mui/icons-material/Refresh";
import { useTranslation } from "react-i18next";
import { useLiveLogs } from "../../../hooks/useLiveLogs";

const LogList: React.FC = () => {
  const { t } = useTranslation();
  const { logs, connected, loading, fetchHistory, setLogs } = useLiveLogs({
    historyLimit: 200,
    maxKept: 500,
  });

  const getLevelColor = (level: string) => {
    switch (level) {
      case "ERROR":
        return "error";
      case "WARN":
        return "warning";
      case "INFO":
        return "info";
      case "DEBUG":
        return "default";
      default:
        return "default";
    }
  };

  return (
    <Box>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 3,
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Typography variant="h4" component="h1">
            {t("logsTitle")}
          </Typography>
          <Chip
            label={connected ? t("logsConnected") : t("logsDisconnected")}
            color={connected ? "success" : "error"}
            size="small"
            variant="outlined"
          />
        </Box>
        <Box>
          <Tooltip title="Reload History">
            <IconButton onClick={fetchHistory} disabled={loading}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
          <Button
            variant="outlined"
            color="error"
            startIcon={<DeleteSweepIcon />}
            onClick={() => setLogs([])}
            sx={{ ml: 2 }}
          >
            {t("logsClear")}
          </Button>
        </Box>
      </Box>

      {loading && logs.length === 0 ? (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
          <CircularProgress />
        </Box>
      ) : (
        <TableContainer
          component={Paper}
          sx={{ maxHeight: "calc(100vh - 200px)", boxShadow: 3 }}
        >
          <Table stickyHeader size="small">
            <TableHead>
              <TableRow>
                <TableCell
                  sx={{
                    fontWeight: "bold",
                    width: "180px",
                    bgcolor: "background.paper",
                  }}
                >
                  {t("logsTimestamp")}
                </TableCell>
                <TableCell
                  sx={{
                    fontWeight: "bold",
                    width: "100px",
                    bgcolor: "background.paper",
                  }}
                >
                  {t("logsLevel")}
                </TableCell>
                <TableCell
                  sx={{ fontWeight: "bold", bgcolor: "background.paper" }}
                >
                  {t("logsMessage")}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {logs.length === 0 ? (
                <TableRow>
                  <TableCell
                    colSpan={3}
                    align="center"
                    sx={{ py: 4, color: "text.secondary" }}
                  >
                    {t("logsNoData")}
                  </TableCell>
                </TableRow>
              ) : (
                logs.map((log) => (
                  <TableRow key={log.id} hover>
                    <TableCell
                      sx={{
                        fontFamily: "monospace",
                        fontSize: "0.8rem",
                        color: "text.secondary",
                      }}
                    >
                      {log.timestamp}
                    </TableCell>
                    <TableCell>
                      {log.level !== "UNKNOWN" && (
                        <Chip
                          label={log.level}
                          color={getLevelColor(log.level) as any}
                          size="small"
                          variant={log.level === "INFO" ? "outlined" : "filled"}
                          sx={{ minWidth: 60, fontWeight: "bold", height: 24 }}
                        />
                      )}
                    </TableCell>
                    <TableCell
                      sx={{
                        fontFamily: "monospace",
                        fontSize: "0.85rem",
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-word",
                      }}
                    >
                      {log.message}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
};

export default LogList;
