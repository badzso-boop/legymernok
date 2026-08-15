import { useEffect, useRef, useState, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import apiClient from "../api/client";

export interface LogEntry {
  id: string;
  timestamp: string;
  level: "INFO" | "WARN" | "ERROR" | "DEBUG" | "TRACE" | "UNKNOWN";
  message: string;
}

const parseLogLine = (line: string): LogEntry => {
  const dateRegex = /^(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}\.\d{3})/;
  const dateMatch = line.match(dateRegex);
  const uniqueId = Math.random().toString(36).substr(2, 9);

  if (!dateMatch) {
    return { id: uniqueId, timestamp: "", level: "UNKNOWN", message: line };
  }

  const timestamp = dateMatch[1];
  let remaining = line.substring(timestamp.length).trim();

  const levelMatch = remaining.match(/^([A-Z]+)\s+/);
  let level: LogEntry["level"] = "UNKNOWN";
  if (levelMatch) {
    level = levelMatch[1] as LogEntry["level"];
    remaining = remaining.substring(level.length).trim();
  }

  const separatorIndex = remaining.indexOf(" : ");
  const message = separatorIndex !== -1 ? remaining.substring(separatorIndex + 3) : remaining;

  return { id: uniqueId, timestamp, level, message };
};

interface UseLiveLogsOptions {
  /** Hány historikus sort töltsünk be induláskor a REST endpointról. */
  historyLimit?: number;
  /** Legfeljebb ennyi sort tartunk memóriában (élő + historikus együtt). */
  maxKept?: number;
}

/**
 * Élő rendszernapló-stream — STOMP/SockJS a `/ws-log` végponton, + REST
 * historikus betöltés. A `LogList.tsx` (teljes admin oldal) és az
 * `AdminLogSnippetCard` (dashboard-kártya, ld. terv) is ezt a hook-ot
 * használja, hogy a kapcsolódási/parse-logika egy helyen éljen.
 */
export function useLiveLogs({ historyLimit = 200, maxKept = 500 }: UseLiveLogsOptions = {}) {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const stompClient = useRef<Client | null>(null);

  const fetchHistory = useCallback(async () => {
    try {
      setLoading(true);
      const response = await apiClient.get<string[]>("/admin/logs", {
        params: { limit: historyLimit },
      });
      setLogs(response.data.map(parseLogLine).reverse());
    } catch (error) {
      console.error("Failed to load logs:", error);
    } finally {
      setLoading(false);
    }
  }, [historyLimit]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  useEffect(() => {
    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    const wsUrl = apiUrl.replace(/\/api$/, "") + "/ws-log";
    const token = localStorage.getItem("token");

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/logs", (message) => {
          if (message.body) {
            const newLog = parseLogLine(message.body);
            setLogs((prev) => [newLog, ...prev].slice(0, maxKept));
          }
        });
      },
      onDisconnect: () => setConnected(false),
    });

    client.activate();
    stompClient.current = client;

    return () => {
      stompClient.current?.deactivate();
    };
  }, [maxKept]);

  return { logs, connected, loading, fetchHistory, setLogs };
}
