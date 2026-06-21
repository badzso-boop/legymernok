import React, { useState, useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  Box,
  Fab,
  Paper,
  Typography,
  TextField,
  IconButton,
  CircularProgress,
} from "@mui/material";
import {
  SmartToy as BotIcon,
  Close as CloseIcon,
  Send as SendIcon,
} from "@mui/icons-material";
import { chatApi } from "../../api/client";
import { useChatContext } from "../../context/ChatContext";
import { useAuth } from "../../context/AuthContext";

interface Message {
  role: "user" | "ai";
  text: string;
}

const HISTORY_KEY = "legymernok_chat_history";
const MAX_HISTORY = 10;

function getPageType(pathname: string): string {
  if (pathname === "/admin/star-systems/new") return "STAR_SYSTEM_CREATE";
  if (/\/admin\/star-systems\/.+/.test(pathname)) return "STAR_SYSTEM_EDIT";
  if (pathname === "/admin/missions/new") return "MISSION_CREATE";
  if (/\/admin\/missions\/.+/.test(pathname)) return "MISSION_EDIT";
  if (pathname === "/star-map") return "STAR_MAP";
  if (/\/star-systems\/.+/.test(pathname)) return "STAR_SYSTEM_DETAIL";
  if (pathname.startsWith("/forge")) return "MISSION_FORGE";
  return "GENERAL";
}

const ChatWidget: React.FC = () => {
  const { i18n } = useTranslation();
  const location = useLocation();
  const { isAuthenticated } = useAuth();
  const { formFields, triggerFill } = useChatContext();

  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState<Message[]>(() => {
    try {
      const saved = localStorage.getItem(HISTORY_KEY);
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(messages.slice(-MAX_HISTORY)));
  }, [messages]);

  useEffect(() => {
    if (open) {
      messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, open]);

  if (!isAuthenticated) return null;

  const handleSend = async () => {
    const text = input.trim();
    if (!text || loading) return;
    setInput("");
    setMessages((prev) => [...prev, { role: "user", text }]);
    setLoading(true);
    try {
      const result = await chatApi.send(text, {
        currentPage: location.pathname,
        pageType: getPageType(location.pathname),
        formFields,
        language: i18n.language,
      });
      setMessages((prev) => [...prev, { role: "ai", text: result.response }]);
      if (result.action?.type === "FILL_FORM") {
        triggerFill(result.action.fields);
      }
    } catch {
      setMessages((prev) => [
        ...prev,
        { role: "ai", text: "Hiba történt a válasz lekérésekor. Próbáld újra!" },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <>
      {open && (
        <Paper
          elevation={8}
          sx={{
            position: "fixed",
            bottom: 88,
            right: 24,
            width: 360,
            height: 480,
            display: "flex",
            flexDirection: "column",
            zIndex: 1400,
            bgcolor: "#0d1117",
            border: "1px solid #30363d",
            borderRadius: 2,
            overflow: "hidden",
          }}
        >
          {/* Header */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1,
              px: 2,
              py: 1.5,
              borderBottom: "1px solid #30363d",
              bgcolor: "#161b22",
            }}
          >
            <BotIcon sx={{ color: "#90caf9", fontSize: 20 }} />
            <Typography
              sx={{
                flex: 1,
                fontSize: "13px",
                fontFamily: '"Share Tech Mono", monospace',
                color: "#90caf9",
                letterSpacing: 1,
              }}
            >
              LÉGYMÉRNÖK ASSZISZTENS
            </Typography>
            <IconButton size="small" onClick={() => setOpen(false)} sx={{ color: "#8b949e" }}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>

          {/* Messages */}
          <Box sx={{ flex: 1, overflowY: "auto", p: 2, display: "flex", flexDirection: "column", gap: 1.5 }}>
            {messages.length === 0 && (
              <Typography sx={{ color: "#8b949e", fontSize: "13px", fontFamily: "monospace", textAlign: "center", mt: 4 }}>
                Szia! Hogyan segíthetek?
              </Typography>
            )}
            {messages.map((msg, i) => (
              <Box
                key={i}
                sx={{
                  display: "flex",
                  justifyContent: msg.role === "user" ? "flex-end" : "flex-start",
                }}
              >
                <Box
                  sx={{
                    maxWidth: "85%",
                    px: 1.5,
                    py: 1,
                    borderRadius: msg.role === "user" ? "12px 12px 2px 12px" : "12px 12px 12px 2px",
                    bgcolor: msg.role === "user" ? "#1f6feb" : "#21262d",
                    color: "#e6edf3",
                    fontSize: "13px",
                    lineHeight: 1.6,
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                  }}
                >
                  {msg.text}
                </Box>
              </Box>
            ))}
            {loading && (
              <Box sx={{ display: "flex", justifyContent: "flex-start" }}>
                <Box sx={{ px: 2, py: 1, bgcolor: "#21262d", borderRadius: "12px 12px 12px 2px" }}>
                  <CircularProgress size={14} sx={{ color: "#8b949e" }} />
                </Box>
              </Box>
            )}
            <div ref={messagesEndRef} />
          </Box>

          {/* Input */}
          <Box
            sx={{
              display: "flex",
              gap: 1,
              p: 1.5,
              borderTop: "1px solid #30363d",
              bgcolor: "#161b22",
            }}
          >
            <TextField
              fullWidth
              size="small"
              multiline
              maxRows={3}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Írj valamit... (Enter = küld)"
              disabled={loading}
              sx={{
                "& .MuiOutlinedInput-root": {
                  bgcolor: "#0d1117",
                  color: "#e6edf3",
                  fontSize: "13px",
                  "& fieldset": { borderColor: "#30363d" },
                  "&:hover fieldset": { borderColor: "#8b949e" },
                  "&.Mui-focused fieldset": { borderColor: "#90caf9" },
                },
                "& .MuiInputBase-input::placeholder": { color: "#484f58" },
              }}
            />
            <IconButton
              onClick={handleSend}
              disabled={loading || !input.trim()}
              sx={{ color: "#90caf9", alignSelf: "flex-end" }}
            >
              <SendIcon fontSize="small" />
            </IconButton>
          </Box>
        </Paper>
      )}

      <Fab
        onClick={() => setOpen((o) => !o)}
        sx={{
          position: "fixed",
          bottom: 24,
          right: 24,
          zIndex: 1400,
          bgcolor: open ? "#1f6feb" : "#161b22",
          border: "1px solid #30363d",
          color: "#90caf9",
          "&:hover": { bgcolor: "#1f6feb" },
        }}
      >
        {open ? <CloseIcon /> : <BotIcon />}
      </Fab>
    </>
  );
};

export default ChatWidget;
