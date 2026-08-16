import React from "react";
import { Box, CircularProgress } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { forgeApi } from "../../api/client";
import type { ContentPageResponse } from "../../types/mission";
import RetroButton from "../RetroButton";
import MarkdownContent from "../shared/MarkdownContent";
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

      <MarkdownContent>{loadedContent}</MarkdownContent>

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
