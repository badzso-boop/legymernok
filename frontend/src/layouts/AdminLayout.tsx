import React from "react";
import { Outlet, useNavigate, useLocation } from "react-router-dom";
import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
  AppBar,
  Toolbar,
  IconButton,
  Avatar,
  Divider,
} from "@mui/material";
import {
  People as PeopleIcon,
  School as SchoolIcon,
  Assignment as AssignmentIcon,
  Security as SecurityIcon,
  VpnKey as PermissionIcon,
  Menu as MenuIcon,
  ArrowBack as ArrowBackIcon,
  Language as LanguageIcon,
} from "@mui/icons-material";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";

const drawerWidth = 240;

const menuItems = [
  { text: "users", icon: <PeopleIcon />, path: "/admin/users" },
  { text: "starSystems", icon: <SchoolIcon />, path: "/admin/star-systems" },
  { text: "missions", icon: <AssignmentIcon />, path: "/admin/missions" },
  { text: "roles", icon: <SecurityIcon />, path: "/admin/roles" },
  { text: "permissions", icon: <PermissionIcon />, path: "/admin/permissions" },
];

const AdminLayout: React.FC = () => {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const [mobileOpen, setMobileOpen] = React.useState(false);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  // Nyelvváltó függvény
  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === "hu" ? "en" : "hu");
  };

  const drawerContent = (
    <div>
      <Toolbar
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          px: 2,
        }}
      >
        <Typography
          variant="h6"
          noWrap
          component="div"
          sx={{ fontWeight: "bold", color: "primary.main" }}
        >
          {t("adminPanel")}
        </Typography>
      </Toolbar>
      <Divider />

      {/* User Info in Sidebar */}
      <Box sx={{ p: 2, display: "flex", alignItems: "center", gap: 2 }}>
        <Avatar sx={{ bgcolor: "secondary.main" }}>
          {user?.username.charAt(0).toUpperCase()}
        </Avatar>
        <Box>
          <Typography variant="subtitle2" sx={{ fontWeight: "bold" }}>
            {user?.username}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {t("administrator")}
          </Typography>
        </Box>
      </Box>
      <Divider />

      <List>
        {menuItems.map((item) => (
          <ListItem key={item.text} disablePadding>
            <ListItemButton
              selected={location.pathname.startsWith(item.path)}
              onClick={() => navigate(item.path)}
            >
              <ListItemIcon
                sx={{
                  color: location.pathname.startsWith(item.path)
                    ? "primary.main"
                    : "inherit",
                }}
              >
                {item.icon}
              </ListItemIcon>
              <ListItemText primary={t(item.text)} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
      <Divider />
      <Box sx={{ p: 2 }}>
        <List disablePadding>
          <ListItem disablePadding>
            <ListItemButton onClick={() => navigate("/")}>
              <ListItemIcon>
                <ArrowBackIcon />
              </ListItemIcon>
              <ListItemText primary={t("backToHome")} />
            </ListItemButton>
          </ListItem>

          <ListItem disablePadding>
            <ListItemButton onClick={toggleLanguage}>
              <ListItemIcon>
                <LanguageIcon />
              </ListItemIcon>
              <ListItemText primary={i18n.language.toUpperCase()} />
            </ListItemButton>
          </ListItem>
        </List>
      </Box>
    </div>
  );

  return (
    <Box sx={{ display: "flex" }}>
      {/* Top Bar for Mobile */}
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          ml: { sm: `${drawerWidth}px` },
          display: { sm: "none" },
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 2, display: { sm: "none" } }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" noWrap component="div">
            {t("adminPanel")}
          </Typography>
        </Toolbar>
      </AppBar>

      {/* Sidebar (Drawer) */}
      <Box
        component="nav"
        sx={{ width: { sm: drawerWidth }, flexShrink: { sm: 0 } }}
      >
        {/* Mobile Drawer */}
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: "block", sm: "none" },
            "& .MuiDrawer-paper": {
              boxSizing: "border-box",
              width: drawerWidth,
            },
          }}
        >
          {drawerContent}
        </Drawer>

        {/* Desktop Drawer */}
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: "none", sm: "block" },
            "& .MuiDrawer-paper": {
              boxSizing: "border-box",
              width: drawerWidth,
            },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      {/* Main Content Area */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { sm: `calc(100% - ${drawerWidth}px)` },
          minHeight: "100vh",
          bgcolor: "background.default",
        }}
      >
        <Toolbar sx={{ display: { sm: "none" } }} />{" "}
        {/* Spacer for mobile appbar */}
        <Outlet />
      </Box>
    </Box>
  );
};

export default AdminLayout;
