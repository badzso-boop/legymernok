import { Typography } from "@mui/material";

export const RetroPanel: React.FC<{
  children: React.ReactNode;
  title?: string;
  sx?: any;
}> = ({ children, title, sx }) => (
  <div className="control-panel-casing" style={{ position: "relative", ...sx }}>
    <div className="screw top-left" />
    <div className="screw top-right" />
    <div className="screw bottom-left" />
    <div className="screw bottom-right" />
    {title && <Typography className="retro-font-header">{title}</Typography>}
    {children}
  </div>
);
