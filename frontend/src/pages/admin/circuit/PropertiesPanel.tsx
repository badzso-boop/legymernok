import React from "react";
import {
  Box,
  Typography,
  TextField,
  Divider,
  IconButton,
  Chip,
  Alert,
} from "@mui/material";
import { Delete as DeleteIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import type { Node } from "@xyflow/react";
import type {
  CircuitDefinitionResponse,
  CircuitNodeData,
  AddVerificationCheckRequest,
  CircuitVerificationCheckResponse,
} from "../../../types/circuit";
import VerificationCheckForm from "./VerificationCheckForm";

const SEVERITY_COLOR: Record<string, "default" | "info" | "warning" | "error"> = {
  INFO: "info",
  WARNING: "warning",
  ERROR: "error",
};

interface PropertiesPanelProps {
  selectedNode: Node<CircuitNodeData> | null;
  definition: CircuitDefinitionResponse;
  addingCheck: boolean;
  onLabelChange: (nodeId: string, newLabel: string) => void;
  onAddCheck: (data: AddVerificationCheckRequest) => Promise<void>;
  onDeleteCheck: (checkId: string) => void;
}

const PropertiesPanel: React.FC<PropertiesPanelProps> = ({
  selectedNode,
  definition,
  addingCheck,
  onLabelChange,
  onAddCheck,
  onDeleteCheck,
}) => {
  const { t } = useTranslation();

  const sortedChecks = [...definition.checks].sort(
    (a, b) => a.orderIndex - b.orderIndex
  );

  return (
    <Box
      sx={{
        width: 260,
        flexShrink: 0,
        borderLeft: "1px solid",
        borderColor: "divider",
        overflowY: "auto",
        p: 1.5,
        bgcolor: "background.paper",
        display: "flex",
        flexDirection: "column",
        gap: 1.5,
      }}
    >
      {selectedNode !== null ? (
        <>
          <Typography variant="overline" sx={{ fontWeight: 700, color: "text.secondary" }}>
            {t("circuit.propertiesTitle")}
          </Typography>

          <Typography variant="caption" color="text.disabled">
            {selectedNode.data.componentType}
          </Typography>

          <TextField
            size="small"
            label={t("circuit.labelField")}
            value={selectedNode.data.label}
            onChange={(e) => onLabelChange(selectedNode.id, e.target.value)}
            disabled={definition.status === "PUBLISHED"}
          />

          {selectedNode.data.properties.length > 0 && (
            <>
              <Divider />
              <Typography variant="caption" color="text.secondary">
                Properties
              </Typography>
              {selectedNode.data.properties.map((prop) => (
                <Box key={prop.id} sx={{ display: "flex", gap: 1, alignItems: "center" }}>
                  <Typography variant="caption" sx={{ fontFamily: "monospace", flex: 1 }}>
                    {prop.key}
                  </Typography>
                  <Typography variant="caption" color="primary">
                    {prop.value}
                    {prop.unitOfMeasure ? ` ${prop.unitOfMeasure.symbol}` : ""}
                  </Typography>
                </Box>
              ))}
            </>
          )}
        </>
      ) : (
        <>
          <Typography variant="overline" sx={{ fontWeight: 700, color: "text.secondary" }}>
            {t("circuit.checksTitle")}
          </Typography>

          {sortedChecks.length === 0 ? (
            <Typography variant="caption" color="text.disabled">
              {t("circuit.noChecks")}
            </Typography>
          ) : (
            sortedChecks.map((check: CircuitVerificationCheckResponse) => (
              <Box
                key={check.id}
                sx={{
                  border: "1px solid",
                  borderColor: "divider",
                  borderRadius: 1,
                  p: 1,
                  display: "flex",
                  alignItems: "flex-start",
                  gap: 0.5,
                }}
              >
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", mb: 0.5 }}>
                    <Chip
                      label={check.checkType}
                      size="small"
                      sx={{ fontSize: "0.6rem" }}
                    />
                    <Chip
                      label={check.severity}
                      size="small"
                      color={SEVERITY_COLOR[check.severity] ?? "default"}
                      sx={{ fontSize: "0.6rem" }}
                    />
                  </Box>
                  <Typography
                    variant="caption"
                    sx={{ fontFamily: "monospace", fontSize: "0.65rem", color: "text.secondary" }}
                  >
                    {check.i18nKey}
                  </Typography>
                </Box>
                <IconButton
                  data-cy={`delete-check-btn-${check.id}`}
                  size="small"
                  onClick={() => {
                    if (window.confirm(t("circuit.confirmDeleteCheck"))) {
                      onDeleteCheck(check.id);
                    }
                  }}
                  disabled={definition.status === "PUBLISHED"}
                >
                  <DeleteIcon fontSize="inherit" />
                </IconButton>
              </Box>
            ))
          )}

          {definition.status !== "PUBLISHED" && (
            <>
              <Divider />
              <VerificationCheckForm
                definitionId={definition.id}
                components={definition.components}
                nextOrderIndex={sortedChecks.length}
                submitting={addingCheck}
                onSubmit={onAddCheck}
              />
            </>
          )}

          <Divider sx={{ mt: "auto" }} />

          {definition.status === "PUBLISHED" ? (
            <Alert severity="success" sx={{ fontSize: "0.75rem", p: 1 }}>
              {t("circuit.publishedReadonly")}
            </Alert>
          ) : (
            definition.status === "STALE" && (
              <Alert severity="warning" sx={{ fontSize: "0.75rem", p: 1 }}>
                {t("circuit.staleWarning")}
              </Alert>
            )
          )}
        </>
      )}
    </Box>
  );
};

export default PropertiesPanel;
