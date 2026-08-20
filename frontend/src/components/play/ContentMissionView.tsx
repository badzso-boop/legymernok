import React from "react";
import { Box, CircularProgress } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { forgeApi, missionApi } from "../../api/client";
import type { ContentPageResponse } from "../../types/mission";
import { findNextPlayableMission, getMissionPlayPath } from "../../utils/missionNavigation";
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

  // A "Következő" gombhoz előre lekérjük a csillagrendszer misszióit, hogy
  // kattintáskor tudjuk, melyik az önálló (nem Mission Group-ba tartozó)
  // következő misszió a sorban.
  const { data: starSystemMissions, isLoading: isNextTargetLoading } = useQuery({
    queryKey: ["starSystemMissions", starSystemId],
    queryFn: () => missionApi.listByStarSystem(starSystemId!),
    enabled: !onComplete && !!starSystemId,
    staleTime: 60_000,
  });

  const handleNext = () => {
    if (onComplete) {
      onComplete();
      return;
    }

    const next = starSystemMissions
      ? findNextPlayableMission(starSystemMissions, missionId)
      : null;

    if (next) {
      // Hard reload, nem react-router navigate() — ismert, dokumentált
      // router-hiba miatt (ld. gyökér CLAUDE.md "Nyitott ismert hibák"):
      // navigate() két különböző "play/*" route között frissíti az URL-t,
      // de az Outlet nem cseréli le a komponenst. window.location.hash
      // önmagában (reload nélkül) ugyanígy nem elég — az csak "same-document
      // navigation", nem tényleges oldalbetöltés. A `?ss=` a starSystemId-t
      // viszi át a `state` helyett, amit egy reload elveszítene (ld.
      // handleBack a QuizPlayerPage/CodingMissionPage/ContentMissionPage-ben).
      const path = getMissionPlayPath(next)!;
      const query = starSystemId ? `?ss=${encodeURIComponent(starSystemId)}` : "";
      window.location.hash = `#${path}${query}`;
      window.location.reload();
      return;
    }

    if (starSystemId) {
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
        <RetroButton
          color="green"
          labelKey="play.next"
          onClick={handleNext}
          disabled={!onComplete && !!starSystemId && isNextTargetLoading}
        />
      </MissionPlayerActions>
    </Box>
  );
};

export default ContentMissionView;
