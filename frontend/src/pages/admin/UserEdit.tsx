import React, { useEffect, useState } from 'react';
   import { useParams, useNavigate } from 'react-router-dom';
   import {
       Box,
       Typography,
       Paper,
       TextField,
       Button,
       IconButton,
       Grid,
       Avatar,
       Divider,
       CircularProgress,
       Alert
   } from '@mui/material';
   import {
       ArrowBack as ArrowBackIcon,
       Save as SaveIcon,
       Person as PersonIcon
   } from '@mui/icons-material';
   import axios from 'axios';
import { useTranslation } from 'react-i18next';

   const API_URL = 'http://localhost:8080/api';

   interface UserResponse {
       id: string;
       username: string;
       email: string;
       roles: string[];
       avatarUrl: string | null;
   }

   const UserEdit: React.FC = () => {
       const { t } = useTranslation();
       const { id } = useParams<{ id: string }>();
       const navigate = useNavigate();
       const [user, setUser] = useState<UserResponse | null>(null);
       const [loading, setLoading] = useState(true);
       const [error, setError] = useState<string | null>(null);

       useEffect(() => {
           const fetchUser = async () => {
               try {
                   setLoading(true);
                   const token = localStorage.getItem('token');
                   const response = await axios.get(`${API_URL}/users/${id}`, {
                       headers: { Authorization: `Bearer ${token}` }
                   });
                   setUser(response.data);
                   setError(null);
               } catch (err) {
                   setError(t('errorFetchUserDetails'));
                   console.error(err);
               } finally {
                   setLoading(false);
               }
           };

           if (id) fetchUser();
       }, [id]);

       if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}><CircularProgress /></Box>;
       if (error) return <Alert severity="error">{error}</Alert>;
       if (!user) return <Alert severity="warning">{t('userNotFound')}</Alert>;

       return (
           <Box>
               {/* Visszanyíl és Cím */}
               <Box sx={{ display: 'flex', alignItems: 'center', mb: 3, gap: 2 }}>
                   <IconButton onClick={() => navigate('/admin/users')} color="primary">
                       <ArrowBackIcon />
                   </IconButton>
                   <Typography variant="h4" sx={{ fontWeight: 'bold' }}>
                        {t('editUser')}
                   </Typography>
               </Box>

               <Paper sx={{ p: 4, elevation: 3 }}>
                   <Grid container spacing={4}>
                       {/* Profil kép szekció */}
                       <Grid size={{ xs: 12, md: 4 }} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                           <Avatar
                               src={user.avatarUrl || undefined}
                               sx={{ width: 150, height: 150, mb: 2, bgcolor: 'primary.main' }}
                           >
                               <PersonIcon sx={{ fontSize: 80 }} />
                           </Avatar>
                           <Typography variant="h6">{user.username}</Typography>
                           <Typography color="text.secondary" gutterBottom>{user.email}</Typography>
                           <Button variant="outlined" size="small" sx={{ mt: 2 }}>
                                {t('changePicture')}
                           </Button>
                       </Grid>

                       {/* Adatok szekció */}
                       <Grid size={{ xs: 12, md: 8 }}>
                           <Typography variant="h6" gutterBottom>{t('basicInfo')}</Typography>
                           <Divider sx={{ mb: 3 }} />

                           <Grid container spacing={2}>
                               <Grid size={{ xs: 12, md: 6 }}>
                                   <TextField
                                       fullWidth
                                       label={t('username')}
                                       value={user.username}
                                       disabled // Egyelőre ne lehessen módosítani
                                   />
                               </Grid>
                               <Grid size={{ xs: 12, md: 6 }}>
                                   <TextField
                                       fullWidth
                                       label={t('email')}
                                       value={user.email}
                                       // Itt majd lehet onChange
                                   />
                               </Grid>
                               <Grid size={{ xs: 12 }}>
                                   <TextField
                                       fullWidth
                                       label={t('fullName')}
                                       value=""
                                       placeholder="Nincs megadva"
                                       helperText="Backend fejlesztés alatt"
                                   />
                               </Grid>
                           </Grid>

                           <Typography variant="h6" sx={{ mt: 4, mb: 1 }} gutterBottom>{t('roles')}</Typography>
                           <Divider sx={{ mb: 2 }} />

                           <Box sx={{ display: 'flex', gap: 1, mb: 4 }}>
                                {user.roles.map((role, index) => (
                                    <Button key={index} variant="contained" color="secondary" size="small">
                                        {role}
                                    </Button>
                                ))}
                               <Button variant="outlined" size="small">+</Button>
                           </Box>

                           <Box sx={{ mt: 4, display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
                               <Button variant="outlined" onClick={() => navigate('/admin/users')}>
                                   {t('cancel')}
                               </Button>
                               <Button
                                   variant="contained"
                                   color="primary"
                                   startIcon={<SaveIcon />}
                                   onClick={() => alert('Backend PUT endpoint hiányzik!')}
                               >
                                   {t('save')}
                               </Button>
                           </Box>
                       </Grid>
                   </Grid>
               </Paper>
           </Box>
       );
   };

   export default UserEdit;