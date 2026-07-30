import React, { useState } from "react";
import {
  Box,
  Typography,
  TextField,
  Button,
  Paper,
  Chip,
  Alert,
  CircularProgress,
  Link as MuiLink,
  Stack,
} from "@mui/material";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { feedbackApi } from "../../api/client";

const FeedbackPage: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);

  const {
    data: issues,
    isLoading: isListLoading,
    isError: isListError,
  } = useQuery({
    queryKey: ["feedback"],
    queryFn: feedbackApi.list,
  });

  const submitMutation = useMutation({
    mutationFn: feedbackApi.submit,
    onSuccess: () => {
      setTitle("");
      setDescription("");
      queryClient.invalidateQueries({ queryKey: ["feedback"] });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !description.trim()) {
      setValidationError(t("feedbackPage.validationError"));
      return;
    }
    setValidationError(null);
    submitMutation.mutate({ title: title.trim(), description: description.trim() });
  };

  return (
    <Box sx={{ maxWidth: 800, mx: "auto" }}>
      <Typography variant="h4" sx={{ fontWeight: "bold", mb: 1 }}>
        {t("feedbackPage.title")}
      </Typography>
      <Typography sx={{ color: "text.secondary", mb: 4 }}>
        {t("feedbackPage.subtitle")}
      </Typography>

      <Paper sx={{ p: 3, mb: 4 }} component="form" onSubmit={handleSubmit}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          {t("feedbackPage.formTitle")}
        </Typography>

        {validationError && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            {validationError}
          </Alert>
        )}
        {submitMutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {t("feedbackPage.submitError")}
          </Alert>
        )}
        {submitMutation.isSuccess && (
          <Alert
            severity="success"
            sx={{ mb: 2 }}
            action={
              <MuiLink
                href={submitMutation.data.url}
                target="_blank"
                rel="noopener noreferrer"
                sx={{ display: "inline-flex", alignItems: "center", gap: 0.5 }}
              >
                #{submitMutation.data.number} <OpenInNewIcon fontSize="inherit" />
              </MuiLink>
            }
          >
            {t("feedbackPage.submitSuccess")}
          </Alert>
        )}

        <TextField
          fullWidth
          label={t("feedbackPage.titleLabel")}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          inputProps={{ maxLength: 200 }}
          sx={{ mb: 2 }}
          disabled={submitMutation.isPending}
        />
        <TextField
          fullWidth
          multiline
          minRows={4}
          label={t("feedbackPage.descriptionLabel")}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          inputProps={{ maxLength: 5000 }}
          sx={{ mb: 2 }}
          disabled={submitMutation.isPending}
        />
        <Button
          type="submit"
          variant="contained"
          disabled={submitMutation.isPending}
          startIcon={submitMutation.isPending ? <CircularProgress size={16} /> : undefined}
        >
          {t("feedbackPage.submitButton")}
        </Button>
      </Paper>

      <Typography variant="h6" sx={{ mb: 2 }}>
        {t("feedbackPage.listTitle")}
      </Typography>

      {isListLoading && <CircularProgress size={28} />}
      {isListError && (
        <Alert severity="error">{t("feedbackPage.listError")}</Alert>
      )}
      {!isListLoading && !isListError && issues?.length === 0 && (
        <Typography sx={{ color: "text.secondary" }}>
          {t("feedbackPage.emptyList")}
        </Typography>
      )}

      <Stack spacing={2}>
        {issues?.map((issue) => (
          <Paper key={issue.number} sx={{ p: 2 }}>
            <Stack
              direction="row"
              justifyContent="space-between"
              alignItems="flex-start"
              spacing={1}
            >
              <Box sx={{ minWidth: 0 }}>
                <MuiLink
                  href={issue.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  sx={{ fontWeight: "bold", wordBreak: "break-word" }}
                >
                  {issue.title}
                </MuiLink>
                {issue.bodyPreview && (
                  <Typography
                    variant="body2"
                    sx={{ color: "text.secondary", mt: 0.5 }}
                  >
                    {issue.bodyPreview}
                  </Typography>
                )}
                <Typography variant="caption" sx={{ color: "text.disabled" }}>
                  #{issue.number}
                  {issue.authorUsername ? ` · ${issue.authorUsername}` : ""}
                </Typography>
              </Box>
              <Chip
                size="small"
                label={
                  issue.state === "open"
                    ? t("feedbackPage.stateOpen")
                    : t("feedbackPage.stateClosed")
                }
                color={issue.state === "open" ? "success" : "default"}
              />
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Box>
  );
};

export default FeedbackPage;
