import React from "react";
import { useParams, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import ContentMissionView from "../../components/play/ContentMissionView";
import { MissionPlayerShell } from "../../components/shared/MissionPlayerShell";

const ContentMissionPage: React.FC = () => {
  const { missionId } = useParams<{ missionId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // ?ss= fallback egy hard reload-os navigációhoz — ld. ContentMissionView.tsx handleNext.
  const starSystemId =
    (location.state as { starSystemId?: string } | null)?.starSystemId ??
    searchParams.get("ss") ??
    undefined;

  const handleBack = () => {
    if (starSystemId) navigate(`/star-systems/${starSystemId}`);
    else navigate(-1);
  };

  return (
    <MissionPlayerShell onBack={handleBack}>
      <ContentMissionView missionId={missionId!} starSystemId={starSystemId} />
    </MissionPlayerShell>
  );
};

export default ContentMissionPage;
