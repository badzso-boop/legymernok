import React, { useEffect, useState, useRef } from "react";
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
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useTranslation } from "react-i18next";
import apiClient from "../../../api/client";

// Log típus definíció
interface LogEntry {
  id: string;
  timestamp: string;
  level: "INFO" | "WARN" | "ERROR" | "DEBUG" | "TRACE" | "UNKNOWN";
  message: string;
}

// Segédfüggvény a log sorok parszolásához
const parseLogLine = (line: string): LogEntry => {
  // console.log("Parsing:", line); // Debugoláshoz

  // 1. Dátum keresése (Mindig az első 23 karakter, ha a formátum fix)
  // De biztonságosabb regex-el csak a dátumot kivenni.
  // Megengedjük a ' ' és 'T' elválasztót is, és a több szóközt.
  const dateRegex = /^(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}\.\d{3})/;
  const dateMatch = line.match(dateRegex);

  const uniqueId = Math.random().toString(36).substr(2, 9);

  if (!dateMatch) {
    // Ha még dátum sincs az elején, akkor ez egy stack trace sora vagy szemét
    return {
      id: uniqueId,
      timestamp: "",
      level: "UNKNOWN",
      message: line,
    };
  }

  const timestamp = dateMatch[1];

  // 2. A maradék szöveg (a dátum után)
  let remaining = line.substring(timestamp.length).trim();

  // 3. Szint keresése (INFO, WARN, ERROR, DEBUG, TRACE)
  // Az első szó a maradékban
  const levelMatch = remaining.match(/^([A-Z]+)\s+/);
  let level: any = "UNKNOWN";

  if (levelMatch) {
    level = levelMatch[1];
    // Vágjuk le a szintet a maradékból
    remaining = remaining.substring(level.length).trim();
  }

  // 4. Üzenet kinyerése
  // Keressük a " : " elválasztót.
  // Fájl: "1 --- [main] c.l... : Starting..."
  // WS: "--- [MessageBroker-2] ... : WebSocketSession..."

  const separatorIndex = remaining.indexOf(" : ");
  let message = "";

  if (separatorIndex !== -1) {
    // Ha megvan a ": ", akkor az üzenet onnantól kezdődik
    message = remaining.substring(separatorIndex + 3);
  } else {
    // Ha nincs ": " (pl. Spring startup), akkor próbáljuk meg a "---" utáni részt
    // de úgy, hogy a thread nevet is átugorjuk ha lehet.
    // Ha ez túl bonyolult, egyszerűen adjuk vissza a teljes maradékot,
    // mert abban benne van az infó.
    message = remaining;
  }

  return {
    id: uniqueId,
    timestamp: timestamp,
    level: level,
    message: message,
  };
};

const LogList: React.FC = () => {
  const { t } = useTranslation();
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const stompClient = useRef<Client | null>(null);

  // 1. Múltbeli logok betöltése (API)
  const fetchHistory = async () => {
    try {
      setLoading(true);
      // Itt hívjuk meg közvetlenül az apiClient-et
      const response = await apiClient.get<string[]>("/admin/logs", {
        params: { limit: 200 },
      });

      const parsedLogs = response.data.map(parseLogLine).reverse(); // Legfrissebb felül
      setLogs(parsedLogs);
    } catch (error) {
      console.error("Failed to load logs:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, []);

  // 2. WebSocket csatlakozás (Élő stream)
  useEffect(() => {
    // API URL meghatározása a WebSockethez
    const apiUrl = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
    // "/api" levágása és "/ws-log" hozzáadása -> http://localhost:8080/ws-log
    const wsUrl = apiUrl.replace(/\/api$/, "") + "/ws-log";

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        console.log("WS Connected");

        client.subscribe("/topic/logs", (message) => {
          if (message.body) {
            const newLog = parseLogLine(message.body);
            // Hozzáadjuk a lista elejéhez, és megtartjuk az utolsó 500-at
            setLogs((prev) => [newLog, ...prev].slice(0, 500));
          }
        });
      },
      onDisconnect: () => {
        setConnected(false);
        console.log("WS Disconnected");
      },
      // debug: (str) => console.log(str), // Fejlesztéshez, ha kell
    });

    client.activate();
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, []);

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
