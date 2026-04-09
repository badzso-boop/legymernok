import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Typography,
  type SelectChangeEvent,
} from "@mui/material";
import {
  ArrowBack as ArrowBackIcon,
  Save as SaveIcon,
  Publish as PublishIcon,
  Unpublished as UnpublishedIcon,
  Warning as WarningIcon,
} from "@mui/icons-material";
import {
  useNodesState,
  useEdgesState,
  addEdge,
  type Node,
  type Edge,
  type Connection,
  useReactFlow,
  ReactFlowProvider,
} from "@xyflow/react";
import { useTranslation } from "react-i18next";

import {
  getCircuitDefinitionsByMission,
  createCircuitDefinition,
  saveCanvas as saveCanvasApi,
  addVerificationCheck,
  deleteVerificationCheck,
  publishCircuitDefinition,
  unpublishCircuitDefinition,
  getAllPinDefinitions,
} from "../../../api/circuitApi";
import type {
  BoardType,
  CircuitDefinitionResponse,
  CircuitNodeData,
  AddVerificationCheckRequest,
  CircuitDefComponentResponse,
  CircuitDefConnectionResponse,
  ComponentPinDefinitionResponse,
  SaveCanvasRequest,
} from "../../../types/circuit";

import ComponentPalette from "./ComponentPalette";
import CircuitCanvas from "./CircuitCanvas";
import PropertiesPanel from "./PropertiesPanel";
import { CircuitContext, getPins } from "./CircuitContext";

// ---------------------------------------------------------------------------
// Pure mapping functions — exported for unit testing
// ---------------------------------------------------------------------------

export function mapComponentsToNodes(
  components: CircuitDefComponentResponse[]
): Node<CircuitNodeData>[] {
  return components.map((c) => ({
    id: c.id,
    type: c.componentType === "BOARD" ? "boardNode" : "circuitNode",
    position: { x: c.posX, y: c.posY },
    data: {
      componentType: c.componentType,
      label: c.label,
      properties: c.properties,
    },
  }));
}

export function mapConnectionsToEdges(
  connections: CircuitDefConnectionResponse[]
): Edge[] {
  return connections.map((conn) => ({
    id: conn.id,
    source: conn.fromComponentId,
    sourceHandle: conn.fromPinName,
    target: conn.toComponentId,
    targetHandle: conn.toPinName,
    data: {
      fromPinName: conn.fromPinName,
      toPinName: conn.toPinName,
    },
    label: `${conn.fromPinName} → ${conn.toPinName}`,
  }));
}

export function mapNodesToSaveRequest(
  nodes: Node<CircuitNodeData>[],
  edges: Edge[]
): SaveCanvasRequest {
  return {
    components: nodes.map((n) => ({
      id: n.id,
      componentType: n.data.componentType,
      label: n.data.label,
      posX: Math.round(n.position.x),
      posY: Math.round(n.position.y),
      properties: n.data.properties.map((p) => ({
        propertyKey: p.key,
        propertyValue: p.value,
        unitOfMeasureId: p.unitOfMeasure?.id,
      })),
    })),
    connections: edges.map((e) => ({
      fromComponentId: e.source,
      fromPinName: (e.data as Record<string, string> | undefined)?.fromPinName ?? "",
      toComponentId: e.target,
      toPinName: (e.data as Record<string, string> | undefined)?.toPinName ?? "",
    })),
  };
}

// ---------------------------------------------------------------------------
// Board types for the creation form
// ---------------------------------------------------------------------------
const BOARD_TYPES: BoardType[] = [
  "ARDUINO_UNO",
  "ARDUINO_MEGA_2560",
  "ESP8266",
  "ESP32",
];

// ---------------------------------------------------------------------------
// Inner component (needs ReactFlowProvider context for useReactFlow)
// ---------------------------------------------------------------------------
const CircuitForgeInner: React.FC = () => {
  const { t } = useTranslation();
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();
  const reactFlow = useReactFlow();

  // --- Domain state ---
  const [definition, setDefinition] = useState<CircuitDefinitionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [addingCheck, setAddingCheck] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<string | null>(null);

  // --- Creation form ---
  const [selectedBoardType, setSelectedBoardType] = useState<BoardType>("ARDUINO_UNO");
  const [creating, setCreating] = useState(false);

  // --- xyflow state ---
  // useNodesState<T> expects T to be the full Node type, not just the data shape
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<CircuitNodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  // --- Pin catalog ---
  const [pinCatalog, setPinCatalog] = useState<Map<string, ComponentPinDefinitionResponse[]>>(new Map());

  // --- UI state ---
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [unsavedChanges, setUnsavedChanges] = useState(false);
  const initialDataRef = useRef<string | null>(null);

  // --- Derived ---
  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedNodeId) ?? null,
    [nodes, selectedNodeId]
  );
  const isReadonly = definition?.status === "PUBLISHED";

  // ---------------------------------------------------------------------------
  // Load pin catalog (once on mount)
  // ---------------------------------------------------------------------------
  useEffect(() => {
    getAllPinDefinitions().then((pins) => {
      const catalog = new Map<string, ComponentPinDefinitionResponse[]>();
      for (const pin of pins) {
        const key = pin.boardType ? `${pin.componentType}:${pin.boardType}` : pin.componentType;
        if (!catalog.has(key)) catalog.set(key, []);
        catalog.get(key)!.push(pin);
      }
      setPinCatalog(catalog);
    });
  }, []);

  // ---------------------------------------------------------------------------
  // Load
  // ---------------------------------------------------------------------------
  useEffect(() => {
    if (!missionId) return;
    setLoading(true);
    getCircuitDefinitionsByMission(missionId)
      .then((defs) => {
        if (defs.length === 0) {
          setDefinition(null);
        } else {
          const def = defs[0];
          setDefinition(def);
          const n = mapComponentsToNodes(def.components);
          const e = mapConnectionsToEdges(def.connections);
          setNodes(n);
          setEdges(e);
          initialDataRef.current = JSON.stringify({ nodes: n, edges: e });
        }
      })
      .catch(() => setError(t("circuit.errorLoad")))
      .finally(() => setLoading(false));
  }, [missionId, t, setNodes, setEdges]);

  // ---------------------------------------------------------------------------
  // Unsaved changes tracking
  // ---------------------------------------------------------------------------
  useEffect(() => {
    if (initialDataRef.current === null) return;
    const current = JSON.stringify({ nodes, edges });
    setUnsavedChanges(current !== initialDataRef.current);
  }, [nodes, edges]);

  useEffect(() => {
    if (!unsavedChanges) return;
    const handler = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [unsavedChanges]);

  // ---------------------------------------------------------------------------
  // Create definition
  // ---------------------------------------------------------------------------
  const handleCreate = async () => {
    if (!missionId) return;
    setCreating(true);
    try {
      const def = await createCircuitDefinition({
        missionId,
        boardType: selectedBoardType,
      });
      setDefinition(def);
      const n = mapComponentsToNodes(def.components);
      const e = mapConnectionsToEdges(def.connections);
      setNodes(n);
      setEdges(e);
      initialDataRef.current = JSON.stringify({ nodes: n, edges: e });
    } catch {
      setError(t("circuit.errorCreate"));
    } finally {
      setCreating(false);
    }
  };

  // ---------------------------------------------------------------------------
  // Save canvas
  // ---------------------------------------------------------------------------
  const handleSave = async () => {
    if (!definition) return;
    setSaving(true);
    try {
      const updated = await saveCanvasApi(
        definition.id,
        mapNodesToSaveRequest(nodes, edges)
      );
      setDefinition(updated);
      const n = mapComponentsToNodes(updated.components);
      const e = mapConnectionsToEdges(updated.connections);
      setNodes(n);
      setEdges(e);
      initialDataRef.current = JSON.stringify({ nodes: n, edges: e });
      setUnsavedChanges(false);
    } catch {
      setError(t("circuit.errorSave"));
    } finally {
      setSaving(false);
    }
  };

  // ---------------------------------------------------------------------------
  // Publish / Unpublish
  // ---------------------------------------------------------------------------
  const handlePublish = async () => {
    if (!definition) return;
    setPublishing(true);
    try {
      const updated = await publishCircuitDefinition(definition.id);
      setDefinition(updated);
    } catch {
      setError(t("circuit.errorPublish"));
    } finally {
      setPublishing(false);
    }
  };

  const handleUnpublish = async () => {
    if (!definition) return;
    setPublishing(true);
    try {
      const updated = await unpublishCircuitDefinition(definition.id);
      setDefinition(updated);
    } catch {
      setError(t("circuit.errorUnpublish"));
    } finally {
      setPublishing(false);
    }
  };

  // ---------------------------------------------------------------------------
  // Verification checks
  // ---------------------------------------------------------------------------
  const handleAddCheck = async (data: AddVerificationCheckRequest) => {
    if (!definition) return;
    setAddingCheck(true);
    try {
      const check = await addVerificationCheck(definition.id, data);
      setDefinition((prev) =>
        prev ? { ...prev, checks: [...prev.checks, check] } : prev
      );
    } catch {
      setError(t("circuit.errorAddCheck"));
    } finally {
      setAddingCheck(false);
    }
  };

  const handleDeleteCheck = async (checkId: string) => {
    if (!definition) return;
    try {
      await deleteVerificationCheck(definition.id, checkId);
      setDefinition((prev) =>
        prev ? { ...prev, checks: prev.checks.filter((c) => c.id !== checkId) } : prev
      );
    } catch {
      setError(t("circuit.errorDeleteCheck"));
    }
  };

  // ---------------------------------------------------------------------------
  // Canvas interactions
  // ---------------------------------------------------------------------------
  const onConnect = useCallback(
    (params: Connection) => {
      if (isReadonly) return;
      const fromPin = params.sourceHandle ?? "";
      const toPin = params.targetHandle ?? "";
      if (!fromPin || !toPin) return;

      // Check if source pin is already in use (single-connection pins only)
      const sourceNode = nodes.find((n) => n.id === params.source);
      if (sourceNode) {
        const sourcePinDefs = getPins(
          pinCatalog,
          (sourceNode.data as CircuitNodeData).componentType,
          (sourceNode.data as CircuitNodeData).componentType === "BOARD" ? definition?.boardType : undefined
        );
        const pinDef = sourcePinDefs.find((p) => p.pinName === fromPin);
        if (pinDef && !pinDef.allowMultipleConnections) {
          const alreadyUsed = edges.some(
            (e) =>
              (e.source === params.source && e.sourceHandle === fromPin) ||
              (e.target === params.source && e.targetHandle === fromPin)
          );
          if (alreadyUsed) {
            setSnackbar(t("circuit.pinAlreadyConnected"));
            return;
          }
        }
      }

      setEdges((eds) =>
        addEdge(
          {
            ...params,
            id: crypto.randomUUID(),
            data: { fromPinName: fromPin, toPinName: toPin },
            label: `${fromPin} → ${toPin}`,
          },
          eds
        )
      );
    },
    [isReadonly, edges, nodes, pinCatalog, definition, t, setEdges]
  );

  const handleDrop = useCallback(
    (e: React.DragEvent<HTMLDivElement>) => {
      if (isReadonly) return;
      const componentType = e.dataTransfer.getData("application/circuitNodeType");
      if (!componentType) return;
      const position = reactFlow.screenToFlowPosition({ x: e.clientX, y: e.clientY });
      const newNode: Node<CircuitNodeData> = {
        id: crypto.randomUUID(),
        type: componentType === "BOARD" ? "boardNode" : "circuitNode",
        position,
        data: {
          componentType: componentType as CircuitNodeData["componentType"],
          label: componentType,
          properties: [],
        },
      };
      setNodes((nds) => [...nds, newNode]);
    },
    [isReadonly, reactFlow, setNodes]
  );

  const handleNodeClick = useCallback((_e: React.MouseEvent, node: Node) => {
    setSelectedNodeId((prev) => (prev === node.id ? null : node.id));
  }, []);

  const handleLabelChange = useCallback(
    (nodeId: string, newLabel: string) => {
      setNodes((nds) =>
        nds.map((n) => {
          if (n.id !== nodeId) return n;
          const data: CircuitNodeData = {
            ...(n.data as CircuitNodeData),
            label: newLabel,
          };
          return { ...n, data };
        })
      );
    },
    [setNodes]
  );

  // Click on canvas background → deselect node
  const handlePaneClick = useCallback(() => setSelectedNodeId(null), []);

  // ---------------------------------------------------------------------------
  // Navigation guard
  // ---------------------------------------------------------------------------
  const handleBack = () => {
    if (
      unsavedChanges &&
      !window.confirm(t("circuit.unsavedChangesConfirm"))
    ) {
      return;
    }
    navigate(`/admin/missions/${missionId}`);
  };

  // ---------------------------------------------------------------------------
  // Render helpers
  // ---------------------------------------------------------------------------
  const statusColor: Record<string, "default" | "info" | "warning" | "success"> = {
    IN_WORK: "info",
    PUBLISHED: "success",
    STALE: "warning",
  };

  if (loading) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%" }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error && !definition) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{error}</Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mt: 2 }}>
          {t("nav.back")}
        </Button>
      </Box>
    );
  }

  // No definition yet — creation UI
  if (!definition) {
    return (
      <Box sx={{ p: 3, maxWidth: 400 }}>
        <Button startIcon={<ArrowBackIcon />} onClick={handleBack} sx={{ mb: 2 }}>
          {t("nav.back")}
        </Button>
        <Typography variant="h6" gutterBottom>
          {t("circuit.pageTitle")}
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          {t("circuit.noDefinition")}
        </Typography>
        <FormControl fullWidth sx={{ mt: 2 }}>
          <InputLabel>{t("circuit.boardType")}</InputLabel>
          <Select
            value={selectedBoardType}
            label={t("circuit.boardType")}
            onChange={(e: SelectChangeEvent) =>
              setSelectedBoardType(e.target.value as BoardType)
            }
          >
            {BOARD_TYPES.map((bt) => (
              <MenuItem key={bt} value={bt}>
                {bt}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <Button
          data-cy="create-definition-btn"
          variant="contained"
          onClick={handleCreate}
          disabled={creating}
          sx={{ mt: 2 }}
          fullWidth
        >
          {creating ? <CircularProgress size={20} /> : t("circuit.createDefinition")}
        </Button>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </Box>
    );
  }

  // Main editor layout
  return (
    <Box sx={{ display: "flex", flexDirection: "column", height: "calc(100vh - 64px)", mx: -3, my: -3 }}>
      {/* Toolbar */}
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1,
          px: 2,
          height: 56,
          flexShrink: 0,
          borderBottom: "1px solid",
          borderColor: "divider",
          bgcolor: "background.paper",
        }}
      >
        <Button size="small" startIcon={<ArrowBackIcon />} onClick={handleBack}>
          {t("nav.back")}
        </Button>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mx: 1 }}>
          {t("circuit.pageTitle")}
        </Typography>

        <Chip
          label={definition.status}
          size="small"
          color={statusColor[definition.status] ?? "default"}
        />

        {unsavedChanges && (
          <Chip
            data-cy="unsaved-changes-chip"
            icon={<WarningIcon fontSize="small" />}
            label={t("circuit.unsavedChanges")}
            size="small"
            color="warning"
            variant="outlined"
          />
        )}

        <Box sx={{ flex: 1 }} />

        {error && (
          <Alert severity="error" sx={{ py: 0, px: 1 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <Button
          size="small"
          variant="outlined"
          startIcon={<SaveIcon />}
          onClick={handleSave}
          disabled={saving || isReadonly}
          data-cy="save-canvas-btn"
        >
          {saving ? <CircularProgress size={16} /> : t("circuit.saveCanvas")}
        </Button>

        {definition.status === "PUBLISHED" ? (
          <Button
            size="small"
            variant="outlined"
            color="warning"
            startIcon={<UnpublishedIcon />}
            onClick={handleUnpublish}
            disabled={publishing}
          >
            {t("circuit.unpublish")}
          </Button>
        ) : (
          <Button
            size="small"
            variant="contained"
            color="success"
            startIcon={<PublishIcon />}
            onClick={handlePublish}
            disabled={publishing || unsavedChanges}
          >
            {t("circuit.publish")}
          </Button>
        )}
      </Box>

      {/* Main 3-panel area — wrapped in CircuitContext */}
      <CircuitContext.Provider value={{ pinCatalog, boardType: definition.boardType }}>
        <Box sx={{ display: "flex", flex: 1, overflow: "hidden" }}>
          <ComponentPalette boardType={definition.boardType} disabled={isReadonly} />

          <Box sx={{ flex: 1, position: "relative" }} onClick={handlePaneClick}>
            <CircuitCanvas
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={handleNodeClick}
              onDrop={handleDrop}
              readonly={isReadonly}
            />
          </Box>

          {definition && (
            <PropertiesPanel
              selectedNode={selectedNode}
              definition={definition}
              addingCheck={addingCheck}
              onLabelChange={handleLabelChange}
              onAddCheck={handleAddCheck}
              onDeleteCheck={handleDeleteCheck}
            />
          )}
        </Box>
      </CircuitContext.Provider>

      {/* Snackbar for non-critical messages */}
      <Snackbar
        open={snackbar !== null}
        autoHideDuration={3000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
      />
    </Box>
  );
};

// Wrap in ReactFlowProvider so useReactFlow() works
const CircuitForgeAdminPage: React.FC = () => (
  <ReactFlowProvider>
    <CircuitForgeInner />
  </ReactFlowProvider>
);

export default CircuitForgeAdminPage;
