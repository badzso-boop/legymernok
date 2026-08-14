import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Box,
  Typography,
  Paper,
  TextField,
  Button,
  IconButton,
  CircularProgress,
  Alert,
  Grid,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Snackbar,
  Chip,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from "@mui/material";
import {
  ArrowUpward,
  ArrowDownward,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Add as AddIcon,
  FolderOpen,
  Rocket,
  MoveToInbox,
  MoveUp,
  Psychology as PsychologyIcon,
  Code as CodeIcon,
  Quiz as QuizIcon,
  Description as DescriptionIcon,
  Extension as ExtensionIcon,
  ElectricBolt as ElectricBoltIcon,
} from "@mui/icons-material";
import apiClient, { missionGroupApi, starSystemApi, searchApi } from "../../../api/client";
import { useChatContext } from "../../../context/ChatContext";
import { useTranslation } from "react-i18next";
import type {
  StarSystemWithItemsResponse,
  StarSystemItemResponse,
} from "../../../types/starSystem";
import type { MissionResponse } from "../../../types/mission";
import type { MissionGroupResponse } from "../../../types/group";
import { GlowCard } from "../../../components/shared/GlowCard";

// ─────────────────────────────────────────────
// CreateGroupDialog
// ─────────────────────────────────────────────
interface CreateGroupDialogProps {
  open: boolean;
  starSystemId: string;
  onClose: () => void;
  onCreated: (group: MissionGroupResponse) => void;
}

const CreateGroupDialog: React.FC<CreateGroupDialogProps> = ({
  open,
  starSystemId,
  onClose,
  onCreated,
}) => {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [loading, setLoading] = useState(false);

  const handleCreate = async () => {
    if (!name.trim()) return;
    setLoading(true);
    try {
      const group = await missionGroupApi.create({ name, description, starSystemId });
      onCreated(group);
      setName("");
      setDescription("");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Új csoport létrehozása</DialogTitle>
      <DialogContent>
        <TextField
          label="Csoport neve"
          fullWidth
          value={name}
          onChange={(e) => setName(e.target.value)}
          sx={{ mt: 1, mb: 2 }}
        />
        <TextField
          label="Leírás (opcionális)"
          fullWidth
          multiline
          rows={2}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Mégse</Button>
        <Button variant="contained" onClick={handleCreate} disabled={loading || !name.trim()}>
          {loading ? <CircularProgress size={20} /> : "Létrehozás"}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ─────────────────────────────────────────────
// AddMissionToGroupDialog
// ─────────────────────────────────────────────
interface AddMissionToGroupDialogProps {
  open: boolean;
  groupId: string;
  standaloneMissions: MissionResponse[];
  onClose: () => void;
  onAdded: () => void;
  onConflict: (msg: string) => void;
}

const AddMissionToGroupDialog: React.FC<AddMissionToGroupDialogProps> = ({
  open,
  groupId,
  standaloneMissions,
  onClose,
  onAdded,
  onConflict,
}) => {
  const [missionId, setMissionId] = useState("");
  const [loading, setLoading] = useState(false);

  const handleAdd = async () => {
    if (!missionId) return;
    setLoading(true);
    try {
      await missionGroupApi.addMission(groupId, missionId);
      setMissionId("");
      onAdded();
    } catch (err: any) {
      if (err.response?.status === 409) {
        const data = err.response.data?.data;
        const conflictName = data?.conflictingGroupName ?? "másik csoport";
        onConflict(`Ez a misszió már a '${conflictName}' csoporthoz tartozik.`);
      } else {
        onConflict("Nem sikerült a misszió hozzáadása.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Misszió hozzáadása csoporthoz</DialogTitle>
      <DialogContent>
        <FormControl fullWidth sx={{ mt: 1 }}>
          <InputLabel>Misszió</InputLabel>
          <Select
            value={missionId}
            label="Misszió"
            onChange={(e) => setMissionId(e.target.value)}
          >
            {standaloneMissions.map((m) => (
              <MenuItem key={m.id} value={m.id}>
                {m.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Mégse</Button>
        <Button variant="contained" onClick={handleAdd} disabled={loading || !missionId}>
          {loading ? <CircularProgress size={20} /> : "Hozzáadás"}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ─────────────────────────────────────────────
// MissionTypeChip
// ─────────────────────────────────────────────
const TYPE_COLORS: Record<string, "default" | "primary" | "secondary" | "success" | "warning" | "error" | "info"> = {
  CODING: "primary",
  QUIZ: "secondary",
  CONTENT: "success",
  FILL_IN_BLANK: "warning",
  CIRCUIT_SIMULATION: "info",
};

const MissionTypeChip: React.FC<{ type: string }> = ({ type }) => (
  <Chip
    label={type.replace(/_/g, " ")}
    color={TYPE_COLORS[type] ?? "default"}
    size="small"
    sx={{ fontFamily: "monospace", fontSize: "0.7rem" }}
  />
);

// ─────────────────────────────────────────────
// Misszió-típus ikonok — vizuális gyorstájékozódás a fa-nézetben
// ─────────────────────────────────────────────
const TYPE_ICONS: Record<string, React.ReactElement> = {
  CODING: <CodeIcon fontSize="small" sx={{ color: "text.secondary" }} />,
  QUIZ: <QuizIcon fontSize="small" sx={{ color: "text.secondary" }} />,
  CONTENT: <DescriptionIcon fontSize="small" sx={{ color: "text.secondary" }} />,
  FILL_IN_BLANK: <ExtensionIcon fontSize="small" sx={{ color: "text.secondary" }} />,
  CIRCUIT_SIMULATION: <ElectricBoltIcon fontSize="small" sx={{ color: "text.secondary" }} />,
};

const MissionTypeIcon: React.FC<{ type: string }> = ({ type }) =>
  TYPE_ICONS[type] ?? <Rocket fontSize="small" sx={{ color: "text.secondary" }} />;

// ─────────────────────────────────────────────
// StarSystemEdit
// ─────────────────────────────────────────────
const StarSystemEdit: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const isNew = !id;
  const { setFormFields, registerFillCallback } = useChatContext();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [iconUrl, setIconUrl] = useState("");
  const [items, setItems] = useState<StarSystemItemResponse[]>([]);

  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [snackMsg, setSnackMsg] = useState<string | null>(null);
  const [createGroupOpen, setCreateGroupOpen] = useState(false);
  const [hasEmbedding, setHasEmbedding] = useState<boolean | null>(null);
  const [embedding, setEmbedding] = useState(false);
  const [deletingEmbedding, setDeletingEmbedding] = useState(false);
  const [addToGroupState, setAddToGroupState] = useState<{ open: boolean; groupId: string } | null>(null);

  useEffect(() => {
    if (!isNew) {
      const load = async () => {
        try {
          setLoading(true);
          const data: StarSystemWithItemsResponse = (
            await apiClient.get(`/star-systems/${id}/with-missions`)
          ).data;
          setName(data.name);
          setDescription(data.description ?? "");
          setIconUrl(data.iconUrl ?? "");
          setItems(data.items);
          const statusData = await searchApi.getEmbeddingStatus(id!);
          setHasEmbedding(statusData.hasEmbedding);
        } catch {
          setError(t("errorFetchStarSystemDetails"));
        } finally {
          setLoading(false);
        }
      };
      load();
    }
  }, [id, isNew]);

  useEffect(() => {
    setFormFields({ name, description, iconUrl });
  }, [name, description, iconUrl]);

  useEffect(() => {
    registerFillCallback((fields) => {
      if (fields.name !== undefined) setName(fields.name);
      if (fields.description !== undefined) setDescription(fields.description);
      if (fields.iconUrl !== undefined) setIconUrl(fields.iconUrl);
    });
    return () => registerFillCallback(null);
  }, []);

  const handleEmbed = async () => {
    if (!id) return;
    setEmbedding(true);
    try {
      await searchApi.embedStarSystem(id);
      setHasEmbedding(true);
      setSnackMsg(t("embeddingSuccess"));
    } catch {
      setSnackMsg(t("embeddingError"));
    } finally {
      setEmbedding(false);
    }
  };

  const handleDeleteEmbedding = async () => {
    if (!id) return;
    setDeletingEmbedding(true);
    try {
      await searchApi.deleteEmbedding(id);
      setHasEmbedding(false);
      setSnackMsg(t("embeddingDeleted"));
    } catch {
      setSnackMsg(t("embeddingError"));
    } finally {
      setDeletingEmbedding(false);
    }
  };

  const reload = async () => {
    if (!id) return;
    const data: StarSystemWithItemsResponse = (
      await apiClient.get(`/star-systems/${id}/with-missions`)
    ).data;
    setItems(data.items);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = { name, description, iconUrl };
      if (isNew) {
        await apiClient.post("/star-systems", payload);
      } else {
        await apiClient.put(`/star-systems/${id}`, payload);
      }
      navigate("/admin/star-systems");
    } catch {
      setError(t("errorSave"));
    } finally {
      setSaving(false);
    }
  };

  // ── Group delete ──
  const handleDeleteGroup = async (groupId: string) => {
    try {
      await missionGroupApi.delete(groupId);
      await reload();
    } catch {
      setSnackMsg("Hiba a csoport törlésekor.");
    }
  };

  // ── Mission remove from group ──
  const handleRemoveFromGroup = async (groupId: string, missionId: string) => {
    try {
      await missionGroupApi.removeMission(groupId, missionId);
      await reload();
    } catch {
      setSnackMsg("Hiba a misszió eltávolításakor.");
    }
  };

  // ── Mission delete ──
  const handleDeleteMission = async (missionId: string) => {
    try {
      await apiClient.delete(`/missions/${missionId}`);
      await reload();
    } catch {
      setSnackMsg("Hiba a misszió törlésekor.");
    }
  };

  // ── Cross-type reorder (standalone missions and groups) ──
  const handleReorderItem = async (
    item1Id: string, item1Type: "MISSION" | "GROUP",
    item2Id: string, item2Type: "MISSION" | "GROUP",
  ) => {
    if (!id) return;
    try {
      await starSystemApi.reorderItems(id, item1Id, item1Type, item2Id, item2Type);
      await reload();
    } catch {
      setSnackMsg("Hiba az átrendezéskor.");
    }
  };

  // ── In-group mission reorder ──
  const handleReorderMissionInGroup = async (groupId: string, missionId: string, targetMissionId: string) => {
    try {
      await missionGroupApi.reorderMissionInGroup(groupId, missionId, targetMissionId);
      await reload();
    } catch {
      setSnackMsg("Hiba az átrendezéskor.");
    }
  };

  const standaloneMissions = items
    .filter((item) => item.type === "MISSION" && item.mission)
    .map((item) => item.mission!);

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 4, fontWeight: "bold" }}>
        {isNew ? t("newStarSystemTitle") : t("editStarSystemTitle")}
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Paper sx={{ p: 4, mb: 4 }}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12 }}>
            <TextField fullWidth label={t("name")} name="name" value={name} onChange={(e) => setName(e.target.value)} />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              label={t("description")}
              name="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              multiline
              rows={3}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              label={t("iconUrl")}
              name="iconUrl"
              value={iconUrl}
              onChange={(e) => setIconUrl(e.target.value)}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }}>
              <Button variant="outlined" onClick={() => navigate("/admin/star-systems")}>
                {t("cancel")}
              </Button>
              <Button variant="contained" onClick={handleSave} disabled={saving}>
                {saving ? <CircularProgress size={24} /> : t("save")}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {!isNew && (
        <Paper sx={{ p: 3, mb: 4, display: "flex", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
          <PsychologyIcon color="action" />
          <Box sx={{ flex: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: "bold" }}>
              {t("embeddingStatus")}
            </Typography>
            {hasEmbedding === null ? (
              <CircularProgress size={16} />
            ) : hasEmbedding ? (
              <Chip label={t("hasEmbedding")} color="success" size="small" />
            ) : (
              <Chip label={t("noEmbedding")} color="default" size="small" />
            )}
          </Box>
          <Button
            variant="outlined"
            size="small"
            startIcon={embedding ? <CircularProgress size={16} /> : <PsychologyIcon />}
            onClick={handleEmbed}
            disabled={embedding || deletingEmbedding}
          >
            {hasEmbedding ? t("updateEmbedding") : t("generateEmbedding")}
          </Button>
          {hasEmbedding && (
            <Button
              variant="outlined"
              size="small"
              color="error"
              startIcon={deletingEmbedding ? <CircularProgress size={16} /> : <DeleteIcon />}
              onClick={handleDeleteEmbedding}
              disabled={embedding || deletingEmbedding}
            >
              {t("deleteEmbedding")}
            </Button>
          )}
        </Paper>
      )}

      {!isNew && (
        <Box>
          <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
            <Typography variant="h5" sx={{ fontWeight: "bold" }}>
              Tartalom
            </Typography>
            <Box sx={{ display: "flex", gap: 1 }}>
              <Button
                variant="outlined"
                startIcon={<FolderOpen />}
                onClick={() => setCreateGroupOpen(true)}
                size="small"
              >
                Új csoport
              </Button>
              <Button
                variant="outlined"
                startIcon={<AddIcon />}
                onClick={() => navigate(`/admin/missions/new?starSystemId=${id}`)}
                size="small"
              >
                Új misszió
              </Button>
            </Box>
          </Box>

          {/* Interleaved items (orderIndex szerint rendezve) */}
          {items.length === 0 && (
            <Typography color="text.secondary">Még nincs tartalom ebben a rendszerben.</Typography>
          )}

          {items.map((item, idx) => {
            const prev = items[idx - 1];
            const next = items[idx + 1];

            if (item.type === "GROUP") {
              const group = item.group!;
              const groupMissions = item.groupMissions ?? [];
              return (
                <GlowCard key={group.id} sx={{ mb: 2, p: 2 }}>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
                    <FolderOpen color="primary" />
                    <Typography variant="h6" sx={{ flex: 1, fontWeight: "bold" }}>
                      {group.name}
                    </Typography>
                    <IconButton
                      size="small"
                      disabled={!prev}
                      onClick={() => prev && handleReorderItem(
                        group.id, "GROUP",
                        (prev.type === "GROUP" ? prev.group!.id : prev.mission!.id),
                        prev.type as "MISSION" | "GROUP",
                      )}
                    >
                      <ArrowUpward fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      disabled={!next}
                      aria-label="Le"
                      onClick={() => next && handleReorderItem(
                        group.id, "GROUP",
                        (next.type === "GROUP" ? next.group!.id : next.mission!.id),
                        next.type as "MISSION" | "GROUP",
                      )}
                    >
                      <ArrowDownward fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => setAddToGroupState({ open: true, groupId: group.id })}
                      title="Misszió hozzáadása"
                    >
                      <MoveToInbox fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      color="error"
                      aria-label="Törlés"
                      onClick={() => handleDeleteGroup(group.id)}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Box>

                  {groupMissions.length > 0 ? (
                    groupMissions.map((mission, mIdx) => {
                      const prevM = groupMissions[mIdx - 1];
                      const nextM = groupMissions[mIdx + 1];
                      return (
                        <Box
                          key={mission.id}
                          sx={{
                            display: "flex",
                            alignItems: "center",
                            gap: 1,
                            pl: 3,
                            py: 0.5,
                            borderLeft: "2px solid",
                            borderColor: "divider",
                            ml: 1,
                            mb: 0.5,
                          }}
                        >
                          <MissionTypeIcon type={mission.missionType} />
                          <Typography sx={{ flex: 1 }}>{mission.name}</Typography>
                          <MissionTypeChip type={mission.missionType} />
                          <IconButton
                            size="small"
                            disabled={!prevM}
                            onClick={() => prevM && handleReorderMissionInGroup(group.id, mission.id, prevM.id)}
                          >
                            <ArrowUpward fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            disabled={!nextM}
                            onClick={() => nextM && handleReorderMissionInGroup(group.id, mission.id, nextM.id)}
                          >
                            <ArrowDownward fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            onClick={() => navigate(`/admin/missions/${mission.id}`)}
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            title="Kivétel csoportból"
                            onClick={() => handleRemoveFromGroup(group.id, mission.id)}
                          >
                            <MoveUp fontSize="small" />
                          </IconButton>
                        </Box>
                      );
                    })
                  ) : (
                    <Typography sx={{ pl: 3, color: "text.secondary", fontSize: "0.85rem" }}>
                      (üres csoport)
                    </Typography>
                  )}
                </GlowCard>
              );
            }

            // MISSION (standalone)
            const mission = item.mission!;
            return (
              <GlowCard key={mission.id} sx={{ mb: 1, p: 1.5 }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <MissionTypeIcon type={mission.missionType} />
                  <Typography sx={{ flex: 1 }}>{mission.name}</Typography>
                  <MissionTypeChip type={mission.missionType} />
                  <IconButton
                    size="small"
                    disabled={!prev}
                    onClick={() => prev && handleReorderItem(
                      mission.id, "MISSION",
                      (prev.type === "GROUP" ? prev.group!.id : prev.mission!.id),
                      prev.type as "MISSION" | "GROUP",
                    )}
                  >
                    <ArrowUpward fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    disabled={!next}
                    onClick={() => next && handleReorderItem(
                      mission.id, "MISSION",
                      (next.type === "GROUP" ? next.group!.id : next.mission!.id),
                      next.type as "MISSION" | "GROUP",
                    )}
                  >
                    <ArrowDownward fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={() => navigate(`/admin/missions/${mission.id}`)}
                  >
                    <EditIcon fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleDeleteMission(mission.id)}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Box>
              </GlowCard>
            );
          })}
        </Box>
      )}

      {/* Dialogs */}
      {id && (
        <CreateGroupDialog
          open={createGroupOpen}
          starSystemId={id}
          onClose={() => setCreateGroupOpen(false)}
          onCreated={() => {
            setCreateGroupOpen(false);
            reload();
          }}
        />
      )}

      {addToGroupState && (
        <AddMissionToGroupDialog
          open={addToGroupState.open}
          groupId={addToGroupState.groupId}
          standaloneMissions={standaloneMissions}
          onClose={() => setAddToGroupState(null)}
          onAdded={() => {
            setAddToGroupState(null);
            reload();
          }}
          onConflict={(msg) => {
            setAddToGroupState(null);
            setSnackMsg(msg);
          }}
        />
      )}

      <Snackbar
        open={!!snackMsg}
        autoHideDuration={5000}
        onClose={() => setSnackMsg(null)}
        message={snackMsg}
      />
    </Box>
  );
};

export default StarSystemEdit;
