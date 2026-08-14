import React, { useState } from "react";
import { Outlet, useNavigate } from "react-router-dom";
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  Box,
  Container,
  IconButton,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  Divider,
  Menu,
  MenuItem,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import "../App.css";

const MainLayout: React.FC = () => {
  const { isAuthenticated, logout, hasRole } = useAuth();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const theme = useTheme();
  // Alatta a nav-sor (Nyelv + Változásnapló + a bejelentkezett/vendég
  // linkek) korábban egyetlen, nem tördelődő sorban élt — ez admin
  // usernél (Csillagtérkép + Forge-jaim + Admin Vezérlőpult + Kijelentkezés
  // + Változásnapló + Nyelv) már 1440px-en is túllógott a Toolbaron és a
  // jobb szélső gombok levágódtak/elérhetetlenné váltak. `md` alatt most
  // hamburger + Drawer jön; `md` fölött az account-jellegű elemek (Admin,
  // Kijelentkezés) egy kompakt fiók-menübe kerülnek, hogy a sor rövid
  // maradjon minden állapotban.
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [accountMenuAnchor, setAccountMenuAnchor] = useState<null | HTMLElement>(null);

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === "hu" ? "en" : "hu");
  };

  const go = (path: string) => {
    setDrawerOpen(false);
    setAccountMenuAnchor(null);
    navigate(path);
  };

  const handleLogout = () => {
    setDrawerOpen(false);
    setAccountMenuAnchor(null);
    logout();
  };

  const isAdmin = hasRole("ROLE_ADMIN");

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        position: "relative",
      }}
    >
      <div className="star-background" />
      <AppBar
        position="sticky"
        sx={{
          bgcolor: "rgba(2, 6, 23, 0.6)",
          backdropFilter: "blur(8px)",
          borderBottom: "1px solid rgba(255,255,255,0.1)",
        }}
      >
        <Toolbar>
          <Box
            onClick={() => navigate("/")}
            sx={{
              flexGrow: 1,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: 1,
              minWidth: 0,
            }}
          >
            <img
              src="/astronaut-logo.svg"
              alt=""
              width={28}
              height={28}
              style={{ flexShrink: 0 }}
            />
            <Typography
              variant="h6"
              component="div"
              sx={{
                fontWeight: "bold",
                letterSpacing: 1,
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              LÉGYMÉRNÖK.HU
            </Typography>
          </Box>

          {isMobile ? (
            <IconButton
              color="inherit"
              aria-label={t("nav.menu")}
              onClick={() => setDrawerOpen(true)}
              data-cy="nav-hamburger"
            >
              <MenuIcon />
            </IconButton>
          ) : (
            <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
              <Button color="inherit" onClick={toggleLanguage}>
                {i18n.language.toUpperCase()}
              </Button>

              <Button color="inherit" onClick={() => navigate("/changelog")}>
                {t("nav.changelog")}
              </Button>

              {!isAuthenticated ? (
                <>
                  <Button color="inherit" onClick={() => navigate("/login")}>
                    {t("login")}
                  </Button>
                  <Button
                    variant="contained"
                    color="primary"
                    onClick={() => navigate("/register")}
                  >
                    {t("register")}
                  </Button>
                </>
              ) : (
                <>
                  <Button color="inherit" onClick={() => navigate("/star-map")}>
                    {t("nav.starMap")}
                  </Button>
                  <Button color="inherit" onClick={() => navigate("/my-forge")}>
                    {t("nav.myForge")}
                  </Button>
                  <IconButton
                    color="inherit"
                    aria-label={t("nav.account")}
                    onClick={(e) => setAccountMenuAnchor(e.currentTarget)}
                    data-cy="nav-account-menu"
                  >
                    <AccountCircleIcon />
                  </IconButton>
                  <Menu
                    anchorEl={accountMenuAnchor}
                    open={Boolean(accountMenuAnchor)}
                    onClose={() => setAccountMenuAnchor(null)}
                  >
                    <MenuItem onClick={() => go("/profile")}>
                      {t("nav.profile")}
                    </MenuItem>
                    <MenuItem onClick={() => go("/settings")}>
                      {t("nav.settings")}
                    </MenuItem>
                    <MenuItem onClick={() => go("/feedback")}>
                      {t("nav.feedback")}
                    </MenuItem>
                    {isAdmin && (
                      <MenuItem onClick={() => go("/admin")}>
                        {t("adminDashboard")}
                      </MenuItem>
                    )}
                    <MenuItem onClick={handleLogout}>{t("logout")}</MenuItem>
                  </Menu>
                </>
              )}
            </Box>
          )}
        </Toolbar>
      </AppBar>

      <Drawer anchor="right" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box sx={{ width: 260 }} role="presentation">
          <List>
            <ListItemButton onClick={toggleLanguage}>
              <ListItemText primary={`${t("nav.language")}: ${i18n.language.toUpperCase()}`} />
            </ListItemButton>
            <ListItemButton onClick={() => go("/changelog")}>
              <ListItemText primary={t("nav.changelog")} />
            </ListItemButton>

            <Divider />

            {!isAuthenticated ? (
              <>
                <ListItemButton onClick={() => go("/login")}>
                  <ListItemText primary={t("login")} />
                </ListItemButton>
                <ListItemButton onClick={() => go("/register")}>
                  <ListItemText primary={t("register")} />
                </ListItemButton>
              </>
            ) : (
              <>
                <ListItemButton onClick={() => go("/star-map")}>
                  <ListItemText primary={t("nav.starMap")} />
                </ListItemButton>
                <ListItemButton onClick={() => go("/my-forge")}>
                  <ListItemText primary={t("nav.myForge")} />
                </ListItemButton>
                <ListItemButton onClick={() => go("/profile")}>
                  <ListItemText primary={t("nav.profile")} />
                </ListItemButton>
                <ListItemButton onClick={() => go("/settings")}>
                  <ListItemText primary={t("nav.settings")} />
                </ListItemButton>
                <ListItemButton onClick={() => go("/feedback")}>
                  <ListItemText primary={t("nav.feedback")} />
                </ListItemButton>
                {isAdmin && (
                  <ListItemButton onClick={() => go("/admin")}>
                    <ListItemText primary={t("adminDashboard")} />
                  </ListItemButton>
                )}
                <ListItemButton onClick={handleLogout}>
                  <ListItemText primary={t("logout")} />
                </ListItemButton>
              </>
            )}
          </List>
        </Box>
      </Drawer>

      <Container
        component="main"
        maxWidth="xl"
        sx={{ flexGrow: 1, py: 4, position: "relative", zIndex: 1 }}
      >
        <Outlet />
      </Container>
    </Box>
  );
};

export default MainLayout;
