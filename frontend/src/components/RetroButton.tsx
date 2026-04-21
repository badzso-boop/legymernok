import React from "react";
import { useTranslation } from "react-i18next";
import "../styles/RetroUI.css";

interface RetroButtonProps {
  color: "red" | "green" | "yellow" | "blue";
  labelKey: string;
  onClick?: () => void;
  type?: "button" | "submit";
  disabled?: boolean;
  active?: boolean;
  size?: "small" | "medium";
  "data-cy"?: string;
}

const RetroButton: React.FC<RetroButtonProps> = ({
  color,
  labelKey,
  onClick,
  type = "button",
  disabled = false,
  active = false,
  size = "medium",
  "data-cy": dataCy,
}) => {
  const { t } = useTranslation();

  return (
    <div className="button-group">
      <button
        type={type}
        className={`retro-btn ${color} ${size === "small" ? "small" : ""} ${active ? "active" : ""}`}
        onClick={onClick}
        disabled={disabled}
        data-cy={dataCy}
      />
      <div
        className="label-plate"
        style={{ color: "#fff", textTransform: "uppercase" }}
      >
        {t(labelKey)}
      </div>
    </div>
  );
};

export default RetroButton;
