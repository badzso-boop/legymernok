import React from "react";
import { Box, CircularProgress } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { forgeApi } from "../../api/client";
import type { ContentPageResponse } from "../../types/mission";
import RetroButton from "../RetroButton";
import {
  MissionPlayerActions,
  MissionPlayerHeaderPortal,
} from "../shared/MissionPlayerShell";

interface ContentMissionViewProps {
  missionId: string;
  onComplete?: () => void;
  starSystemId?: string;
}

const PAGE_SIZE = 100;

const ContentMissionView: React.FC<ContentMissionViewProps> = ({
  missionId,
  onComplete,
  starSystemId,
}) => {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError } =
    useInfiniteQuery({
      queryKey: ["missionContent", missionId],
      queryFn: ({ pageParam }): Promise<ContentPageResponse> =>
        forgeApi.getContentPage(missionId, pageParam, PAGE_SIZE),
      initialPageParam: 0,
      getNextPageParam: (lastPage) =>
        lastPage.hasNextPage ? lastPage.page + 1 : undefined,
    });

  const missionName = data?.pages[0]?.missionName ?? "";
  const loadedContent = isError
    ? t("play.loadError")
    : (data?.pages.map((p) => p.content).join("\n") ?? "");

  const handleNext = () => {
    if (onComplete) {
      onComplete();
    } else if (starSystemId) {
      navigate(`/star-systems/${starSystemId}`);
    } else {
      navigate(-1);
    }
  };

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 200 }}>
        <CircularProgress sx={{ color: "var(--color-accent-primary)" }} />
      </Box>
    );
  }

  return (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
      <MissionPlayerHeaderPortal title={missionName} />

      <Box
        sx={{
          color: "var(--color-text-primary)",
          "& h1, & h2, & h3": {
            color: "var(--color-accent-primary)",
            fontWeight: 700,
          },
          "& p": { lineHeight: 1.7 },
          "& code": {
            bgcolor: "var(--color-bg-elevated)",
            px: 0.5,
            borderRadius: 0.5,
            fontFamily: "monospace",
            fontSize: "0.9em",
          },
          "& pre": {
            bgcolor: "var(--color-bg-elevated)",
            border: "1px solid var(--color-border)",
            p: 1.5,
            borderRadius: 1,
            overflow: "auto",
          },
          "& blockquote": {
            borderLeft: "3px solid var(--color-accent-primary)",
            pl: 2,
            ml: 0,
            color: "var(--color-text-secondary)",
          },
          "& a": { color: "var(--color-accent-primary)" },
          "& ul, & ol": { pl: 3 },
          "& table": { borderCollapse: "collapse", width: "100%" },
          "& th, & td": { border: "1px solid var(--color-border)", p: "6px 12px" },
          "& th": { bgcolor: "var(--color-bg-elevated)" },
          "& hr": { borderColor: "var(--color-border)", my: 2 },
        }}
      >
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{loadedContent}</ReactMarkdown>
      </Box>

      <MissionPlayerActions>
        {hasNextPage && (
          <RetroButton
            color="blue"
            labelKey="play.loadMore"
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
          />
        )}
        <RetroButton color="green" labelKey="play.next" onClick={handleNext} />
      </MissionPlayerActions>
    </Box>
  );
};

export default ContentMissionView;
