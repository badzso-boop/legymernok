import React, { useEffect, useState } from "react";
import { Box, Typography, Grid, Button, Paper } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import apiClient from "../../api/client";
import type { StarSystemWithMissionsResponse } from "../../types/starSystem";
import "../../styles/RetroUI.css"; // Az új közös stílus

const StarSystemDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [data, setData] = useState<StarSystemWithMissionsResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await apiClient.get<StarSystemWithMissionsResponse>(
          `/star-systems/${id}/with-missions`,
        );
        setData(response.data);
      } catch (error) {
        console.error(error);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id]);

  if (loading) return <div className="loading-screen">LOADING...</div>;
  if (!data) return <div className="error-screen">SYSTEM NOT FOUND</div>;

  return (
    <Box
      sx={{
        width: "100vw",
        height: "100vh",
        bgcolor: "#1a1a1a",
        p: 2,
        display: "flex",
        justifyContent: "center",
      }}
    >
      <div
        className="control-panel-casing"
        style={{
          width: "100%",
          maxWidth: "1200px",
          display: "flex",
          flexDirection: "column",
        }}
      >
        {/* Csavarok */}
        <div className="screw top-left" />
        <div className="screw top-right" />
        <div className="screw bottom-left" />
        <div className="screw bottom-right" />

        {/* Fejléc */}
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            mb: 2,
            borderBottom: "2px solid #333",
            pb: 1,
          }}
        >
          <Typography variant="h5" className="retro-font-header">
            SYSTEM_DATA: {data.name.toUpperCase()}
          </Typography>
        </Box>

        <Grid
          container
          spacing={2}
          sx={{ flexGrow: 1, overflow: "hidden", margin: "auto" }}
        >
          {/* BAL OLDAL */}
          <Grid
            sx={{
              display: "flex",
              flexDirection: "column",
              gap: 2,
              height: "100%",
              xs: 12,
              md: 4,
            }}
          >
            {/* Bal Felső: Adatok (CRT Monitor) */}
            <Box sx={{ flex: 1, minHeight: "200px" }}>
              <div className="crt-monitor" style={{ height: "100%" }}>
                <div className="screen-overlay" />
                <div className="terminal-content" style={{ padding: "20px" }}>
                  <Typography
                    variant="h4"
                    gutterBottom
                    sx={{ color: "#0f0", textShadow: "0 0 10px #0f0" }}
                  >
                    {data.name}
                  </Typography>
                  <Typography
                    variant="body1"
                    sx={{ fontFamily: "monospace", lineHeight: 1.5 }}
                  >
                    {data.description ||
                      "No description available for this sector."}
                  </Typography>
                  <Box sx={{ mt: 4 }}>
                    <Typography variant="caption">
                      SECTOR ID: {data.id.split("-")[0]}
                    </Typography>
                    <br />
                    <Typography variant="caption">
                      MISSIONS DETECTED: {data.missions.length}
                    </Typography>
                  </Box>
                </div>
              </div>
            </Box>

            {/* Bal Alsó: Gombok */}
            <Box
              sx={{
                height: "150px",
                bgcolor: "#222",
                borderRadius: "10px",
                border: "2px inset #111",
                p: 2,
                display: "flex",
                justifyContent: "space-around",
                alignItems: "center",
              }}
            >
              {/* Dísz gombok */}
              <div className="button-group">
                <button
                  className="retro-btn yellow"
                  onClick={() => alert("Analyzing...")}
                />
                <div className="label-plate">ANALYZE</div>
              </div>
              <div className="button-group">
                <button
                  className="retro-btn red"
                  onClick={() => navigate(-1)}
                />
                <div className="label-plate">BACK</div>
              </div>
              <div className="button-group">
                <button className="retro-btn green" />
                <div className="label-plate">LOG</div>
              </div>
            </Box>
          </Grid>

          {/* JOBB OLDAL: Missziók */}
          <Grid sx={{ height: "100%", xs: 12, md: 8 }}>
            <Box
              sx={{
                height: "100%",
                bgcolor: "#000",
                border: "2px solid #333",
                borderRadius: "5px",
                p: 2,
                overflowY: "auto",
                fontFamily: '"VT323", monospace',
              }}
            >
              <Typography
                variant="h5"
                sx={{ color: "#fff", mb: 2, borderBottom: "1px dashed #555" }}
              >
                AVAILABLE MISSIONS
              </Typography>

              {data.missions.map((mission) => (
                <Paper
                  key={mission.id}
                  sx={{
                    p: 2,
                    mb: 2,
                    bgcolor: "#111",
                    border: "1px solid #333",
                    color: "#0f0",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    "&:hover": {
                      borderColor: "#0f0",
                      boxShadow: "0 0 10px rgba(0,255,0,0.2)",
                    },
                  }}
                >
                  <Box>
                    <Typography variant="h6">{mission.name}</Typography>
                    <Typography variant="caption" sx={{ color: "#888" }}>
                      DIFFICULTY:{" "}
                      <span
                        style={{
                          color: getDifficultyColor(mission.difficulty),
                        }}
                      >
                        {mission.difficulty}
                      </span>
                    </Typography>
                  </Box>

                  <Button
                    variant="contained"
                    color="error"
                    onClick={() => alert(`Starting mission: ${mission.name}`)}
                    sx={{
                      fontFamily: "monospace",
                      fontWeight: "bold",
                      borderRadius: 0,
                      border: "2px solid #500",
                      boxShadow: "0 0 5px #f00",
                    }}
                  >
                    START
                  </Button>
                </Paper>
              ))}

              {data.missions.length === 0 && (
                <Typography sx={{ color: "#666", textAlign: "center", mt: 4 }}>
                  NO MISSIONS AVAILABLE IN THIS SYSTEM.
                </Typography>
              )}
            </Box>
          </Grid>
        </Grid>
      </div>
    </Box>
  );
};

const getDifficultyColor = (diff: string) => {
  if (diff === "EASY") return "#0f0";
  if (diff === "MEDIUM") return "#ff0";
  if (diff === "HARD") return "#f00";
  return "#fff";
};

export default StarSystemDetailPage;
