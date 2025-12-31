import React, { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Box, Typography, Paper, Chip, Container, alpha } from "@mui/material";
import {
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  TimelineOppositeContent,
} from "@mui/lab"; // Megjegyzés: ehhez kelleni fog a @mui/lab, ha nincs, telepítjük!
import {
  RocketLaunch as RocketIcon,
  Security as SecurityIcon,
  Code as CodeIcon,
  Storage as StorageIcon,
  Map as MapIcon,
  Build as BuildIcon,
  HistoryEdu as LogIcon,
} from "@mui/icons-material";

// Importáljuk a nyers markdown fájlt (Vite ?raw suffix)
import changelogRaw from "./CHANGELOG.md?raw";

// Típus a bejegyzésekhez
interface LogEntry {
  id: string;
  title: string;
  date: string;
  status: string;
  icon: React.ReactNode;
  content: string;
}

const ChangelogPage: React.FC = () => {
  const [entries, setEntries] = useState<LogEntry[]>([]);

  useEffect(() => {
    parseChangelog(changelogRaw);
  }, []);

  const getIconForTitle = (title: string) => {
    const lower = title.toLowerCase();
    if (lower.includes("security") || lower.includes("védelmi"))
      return <SecurityIcon />;
    if (lower.includes("klónozás") || lower.includes("mission"))
      return <RocketIcon />;
    if (lower.includes("kódraktár") || lower.includes("gitea"))
      return <StorageIcon />;
    if (lower.includes("térkép") || lower.includes("swagger"))
      return <MapIcon />;
    if (lower.includes("építés") || lower.includes("backend"))
      return <BuildIcon />;
    if (lower.includes("műszerfal") || lower.includes("ui"))
      return <CodeIcon />;
    return <LogIcon />;
  };

  const parseChangelog = (md: string) => {
    // Szétvágjuk a fájlt "## Bejegyzés" mentén
    const rawEntries = md.split(/^## /m).slice(1); // Az első elem a fejléc, eldobjuk

    const parsed: LogEntry[] = rawEntries.map((entryRaw, index) => {
      const lines = entryRaw.split("\n");
      // Az első sor a Cím (pl. "Bejegyzés #11: ...")
      const fullTitle = lines[0].trim();

      // Megkeressük a metaadatokat (Stardate, Status)
      const stardateLine = lines.find((l) => l.includes("**Stardate:**"));
      const statusLine = lines.find((l) => l.includes("**Status:**"));

      const stardate = stardateLine
        ? stardateLine.split("**Stardate:**")[1].trim()
        : "Unknown";
      const status = statusLine
        ? statusLine.split("**Status:**")[1].trim()
        : "Unknown";

      // A tartalom a metaadatok után jön
      // Kitöröljük a címet és a metaadat sorokat a nyers szövegből a tiszta tartalomhoz
      let content = entryRaw
        .replace(lines[0], "") // Cím törlése
        .replace(/.*\*\*Stardate:\*\*.*\n?/g, "")
        .replace(/.*\*\*Status:\*\*.*\n?/g, "")
        .trim();

      return {
        id: `entry-${index}`,
        title: fullTitle,
        date: stardate,
        status: status,
        icon: getIconForTitle(fullTitle),
        content: content,
      };
    });

    setEntries(parsed);
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#0a0b1e", // Mély űr kék
        backgroundImage:
          "radial-gradient(circle at 50% 50%, #1a1b3a 0%, #0a0b1e 100%)",
        color: "white",
        py: 8,
        fontFamily: "'Orbitron', sans-serif", // Ha van sci-fi fontunk
      }}
    >
      <Container maxWidth="lg">
        <Box textAlign="center" mb={8}>
          <Typography
            variant="h2"
            sx={{
              fontWeight: "bold",
              background: "linear-gradient(45deg, #00f2ff, #00c3ff)",
              backgroundClip: "text",
              textFillColor: "transparent",
              mb: 2,
              textShadow: "0 0 20px rgba(0, 242, 255, 0.5)",
            }}
          >
            CAPTAIN'S LOG
          </Typography>
          <Typography variant="h6" sx={{ color: alpha("#fff", 0.7) }}>
            LégyMérnök.hu Fejlesztési Napló
          </Typography>
        </Box>

        <Timeline position="alternate">
          {entries.map((entry) => (
            <TimelineItem key={entry.id}>
              <TimelineOppositeContent
                sx={{ m: "auto 0" }}
                align="right"
                variant="body2"
                color="text.secondary"
              >
                <Typography
                  variant="h6"
                  sx={{ color: "#00f2ff", fontWeight: "bold" }}
                >
                  {entry.date}
                </Typography>
                <Chip
                  label={entry.status}
                  size="small"
                  sx={{
                    mt: 1,
                    bgcolor: alpha(
                      entry.status.includes("KÉSZ") ||
                        entry.status.includes("Sikeres") ||
                        entry.status.includes("100%")
                        ? "#00ff88"
                        : "#ffbd2e",
                      0.2
                    ),
                    color:
                      entry.status.includes("KÉSZ") ||
                      entry.status.includes("Sikeres") ||
                      entry.status.includes("100%")
                        ? "#00ff88"
                        : "#ffbd2e",
                    border: "1px solid",
                    borderColor:
                      entry.status.includes("KÉSZ") ||
                      entry.status.includes("Sikeres") ||
                      entry.status.includes("100%")
                        ? "#00ff88"
                        : "#ffbd2e",
                  }}
                />
              </TimelineOppositeContent>
              <TimelineSeparator>
                <TimelineConnector sx={{ bgcolor: alpha("#fff", 0.2) }} />
                <TimelineDot
                  sx={{
                    bgcolor: "transparent",
                    border: "2px solid #00f2ff",
                    p: 2,
                    boxShadow: "0 0 15px rgba(0, 242, 255, 0.4)",
                    color: "#00f2ff", // Itt állítjuk be a színt, ami öröklődik az ikonra!
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                  }}
                >
                  {entry.icon}
                </TimelineDot>
                <TimelineConnector sx={{ bgcolor: alpha("#fff", 0.2) }} />
              </TimelineSeparator>
              <TimelineContent sx={{ py: "12px", px: 2 }}>
                <Paper
                  elevation={3}
                  sx={{
                    p: 3,
                    bgcolor: alpha("#1a1b3a", 0.8),
                    border: "1px solid rgba(255,255,255,0.1)",
                    backdropFilter: "blur(10px)",
                    color: "white",
                    borderRadius: "16px",
                    transition: "transform 0.3s",
                    "&:hover": {
                      transform: "scale(1.02)",
                      boxShadow: "0 0 20px rgba(0, 242, 255, 0.2)",
                      border: "1px solid rgba(0, 242, 255, 0.3)",
                    },
                  }}
                >
                  <Typography
                    variant="h6"
                    component="span"
                    sx={{
                      fontWeight: "bold",
                      display: "block",
                      mb: 2,
                      borderBottom: "1px solid rgba(255,255,255,0.1)",
                      pb: 1,
                    }}
                  >
                    {entry.title}
                  </Typography>
                  <Box
                    sx={{
                      "& p": { lineHeight: 1.6, color: alpha("#fff", 0.8) },
                      "& ul": { pl: 2 },
                      "& li": { mb: 0.5 },
                      "& strong": { color: "#fff" },
                      "& code": {
                        bgcolor: "rgba(0,0,0,0.3)",
                        p: "2px 6px",
                        borderRadius: "4px",
                        fontFamily: "monospace",
                        color: "#ffbd2e",
                      },
                    }}
                  >
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                      {entry.content}
                    </ReactMarkdown>
                  </Box>
                </Paper>
              </TimelineContent>
            </TimelineItem>
          ))}
        </Timeline>
      </Container>
    </Box>
  );
};

export default ChangelogPage;
