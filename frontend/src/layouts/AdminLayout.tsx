import React from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
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
    Divider
} from '@mui/material';
import {
    People as PeopleIcon,
    School as SchoolIcon,
    Assignment as AssignmentIcon,
    Security as SecurityIcon,
    VpnKey as PermissionIcon,
    Menu as MenuIcon,
    ArrowBack as ArrowBackIcon
} from '@mui/icons-material';
import { useAuth } from '../context/AuthContext';

const drawerWidth = 240;

const menuItems = [
    { text: 'Felhasználók', icon: <PeopleIcon />, path: '/admin/users' },
    { text: 'Kurzusok', icon: <SchoolIcon />, path: '/admin/courses' },
    { text: 'Feladatok', icon: <AssignmentIcon />, path: '/admin/missions' },
    { text: 'Role-ok', icon: <SecurityIcon />, path: '/admin/roles' },
    { text: 'Permission-ök', icon: <PermissionIcon />, path: '/admin/permissions' },
];

const AdminLayout: React.FC = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user } = useAuth();
    const [mobileOpen, setMobileOpen] = React.useState(false);

    const handleDrawerToggle = () => {
        setMobileOpen(!mobileOpen);
    };

    const drawerContent = (
        <div>
            <Toolbar sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', px: 2 }}>
                <Typography variant="h6" noWrap component="div" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
                    ADMIN PANEL
                </Typography>
            </Toolbar>
            <Divider />

            {/* User Info in Sidebar */}
            <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar sx={{ bgcolor: 'secondary.main' }}>{user?.username.charAt(0).toUpperCase()}</Avatar>
                <Box>
                    <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>{user?.username}</Typography>
                    <Typography variant="caption" color="text.secondary">Adminisztrátor</Typography>
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
                            <ListItemIcon sx={{ color: location.pathname.startsWith(item.path) ? 'primary.main' : 'inherit' }}>
                                {item.icon}
                            </ListItemIcon>
                            <ListItemText primary={item.text} />
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>
            <Divider />
            <List>
                <ListItem disablePadding>
                    <ListItemButton onClick={() => navigate('/')}>
                        <ListItemIcon><ArrowBackIcon /></ListItemIcon>
                        <ListItemText primary="Vissza a főoldalra" />
                    </ListItemButton>
                </ListItem>
            </List>
        </div>
    );

    return (
        <Box sx={{ display: 'flex' }}>
            {/* Top Bar for Mobile */}
            <AppBar
                position="fixed"
                sx={{
                    width: { sm: `calc(100% - ${drawerWidth}px)` },
                    ml: { sm: `${drawerWidth}px` },
                    display: { sm: 'none' }
                }}
            >
                <Toolbar>
                    <IconButton
                        color="inherit"
                        aria-label="open drawer"
                        edge="start"
                        onClick={handleDrawerToggle}
                        sx={{ mr: 2, display: { sm: 'none' } }}
                    >
                        <MenuIcon />
                    </IconButton>
                    <Typography variant="h6" noWrap component="div">
                        Admin Dashboard
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
                        display: { xs: 'block', sm: 'none' },
                        '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
                    }}
                >
                    {drawerContent}
                </Drawer>

                {/* Desktop Drawer */}
                <Drawer
                    variant="permanent"
                    sx={{
                        display: { xs: 'none', sm: 'block' },
                        '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
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
                    minHeight: '100vh',
                    bgcolor: 'background.default'
                }}
            >
                <Toolbar sx={{ display: { sm: 'none' } }} /> {/* Spacer for mobile appbar */}
                <Outlet />
            </Box>
        </Box>
    );
};

export default AdminLayout;