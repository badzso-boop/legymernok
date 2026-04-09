import React, { useState } from "react";
import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Typography,
  type SelectChangeEvent,
} from "@mui/material";
import { Add as AddIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import type {
  AddVerificationCheckRequest,
  CheckSeverity,
  CheckType,
  CircuitDefComponentResponse,
} from "../../../types/circuit";

interface VerificationCheckFormProps {
  definitionId: string;
  components: CircuitDefComponentResponse[];
  nextOrderIndex: number;
  submitting: boolean;
  onSubmit: (data: AddVerificationCheckRequest) => Promise<void>;
}

const CHECK_TYPES: CheckType[] = [
  "CIRCUIT_TOPOLOGY",
  "PATH_EXISTS",
  "GPIO_BEHAVIOR",
  "SERIAL_OUTPUT",
  "PWM",
];
const SEVERITIES: CheckSeverity[] = ["INFO", "WARNING", "ERROR"];

const DEFAULT_FORM: AddVerificationCheckRequest = {
  checkType: "CIRCUIT_TOPOLOGY",
  severity: "ERROR",
  i18nKey: "",
  orderIndex: 0,
};

const VerificationCheckForm: React.FC<VerificationCheckFormProps> = ({
  components,
  nextOrderIndex,
  submitting,
  onSubmit,
}) => {
  const { t } = useTranslation();
  const [form, setForm] = useState<AddVerificationCheckRequest>({
    ...DEFAULT_FORM,
    orderIndex: nextOrderIndex,
  });

  const set = (field: keyof AddVerificationCheckRequest, value: string | number) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  const handleCheckTypeChange = (e: SelectChangeEvent) => {
    const ct = e.target.value as CheckType;
    setForm({
      checkType: ct,
      severity: form.severity,
      i18nKey: form.i18nKey,
      orderIndex: form.orderIndex,
    });
  };

  const handleSubmit = async () => {
    if (!form.i18nKey.trim()) return;
    await onSubmit({ ...form, orderIndex: nextOrderIndex });
    setForm({ ...DEFAULT_FORM, orderIndex: nextOrderIndex + 1 });
  };

  const componentLabels = components.map((c) => c.label);
  const needsPathFields =
    form.checkType === "PATH_EXISTS";
  const needsGpioFields =
    form.checkType === "GPIO_BEHAVIOR" || form.checkType === "PWM";
  const needsExpectedValue =
    form.checkType === "GPIO_BEHAVIOR" ||
    form.checkType === "SERIAL_OUTPUT" ||
    form.checkType === "PWM";

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5, mt: 1 }}>
      <Typography variant="caption" sx={{ fontWeight: 600, color: "text.secondary" }}>
        {t("circuit.addCheck")}
      </Typography>

      <FormControl size="small" fullWidth>
        <InputLabel>{t("circuit.checkType")}</InputLabel>
        <Select
          data-cy="check-type-select"
          value={form.checkType}
          label={t("circuit.checkType")}
          onChange={handleCheckTypeChange}
        >
          {CHECK_TYPES.map((ct) => (
            <MenuItem key={ct} value={ct}>
              {ct}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      <FormControl size="small" fullWidth>
        <InputLabel>{t("circuit.severity")}</InputLabel>
        <Select
          value={form.severity}
          label={t("circuit.severity")}
          onChange={(e) => set("severity", e.target.value)}
        >
          {SEVERITIES.map((s) => (
            <MenuItem key={s} value={s}>
              {s}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      <TextField
        data-cy="i18n-key-input"
        size="small"
        label={t("circuit.i18nKey")}
        value={form.i18nKey}
        onChange={(e) => set("i18nKey", e.target.value)}
        required
        error={!form.i18nKey.trim()}
      />

      {(needsPathFields || needsGpioFields) && (
        <FormControl size="small" fullWidth>
          <InputLabel>{t("circuit.labelFrom")}</InputLabel>
          <Select
            value={form.labelFrom ?? ""}
            label={t("circuit.labelFrom")}
            onChange={(e) => set("labelFrom", e.target.value)}
          >
            {componentLabels.map((lbl) => (
              <MenuItem key={lbl} value={lbl}>
                {lbl}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      )}

      {needsPathFields && (
        <FormControl size="small" fullWidth>
          <InputLabel>{t("circuit.labelTo")}</InputLabel>
          <Select
            value={form.labelTo ?? ""}
            label={t("circuit.labelTo")}
            onChange={(e) => set("labelTo", e.target.value)}
          >
            {componentLabels.map((lbl) => (
              <MenuItem key={lbl} value={lbl}>
                {lbl}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      )}

      {needsGpioFields && (
        <TextField
          size="small"
          label={t("circuit.pinFrom")}
          value={form.pinFrom ?? ""}
          onChange={(e) => set("pinFrom", e.target.value)}
        />
      )}

      {needsExpectedValue && (
        <TextField
          size="small"
          label={t("circuit.expectedValue")}
          value={form.expectedValue ?? ""}
          onChange={(e) => set("expectedValue", e.target.value)}
        />
      )}

      <Button
        data-cy="add-check-btn"
        variant="contained"
        size="small"
        startIcon={<AddIcon />}
        disabled={submitting || !form.i18nKey.trim()}
        onClick={handleSubmit}
        fullWidth
      >
        {t("circuit.addCheck")}
      </Button>
    </Box>
  );
};

export default VerificationCheckForm;
