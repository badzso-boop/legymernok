import React, { useEffect, useState } from 'react';
   import { useNavigate } from 'react-router-dom';
   import {
       Box,
       Typography,
       Paper,
       Table,
       TableBody,
       TableCell,
       TableContainer,
       TableHead,
       TableRow,
       Avatar,
       IconButton,
       Chip,
       Tooltip,
       CircularProgress,
       Alert
   } from '@mui/material';
   import {
       Edit as EditIcon,
       Delete as DeleteIcon,
       Person as PersonIcon
   } from '@mui/icons-material';
   import axios from 'axios';

   // API alap URL (a .env fájlból vagy fixen, ha nincs)
   const API_URL = 'http://localhost:8080/api';

   interface Role {
       id: number;
       name: string;
   }

   interface UserResponse {
       id: string;
       username: string;
       email: string;
       roles: Role[];
       avatarUrl: string | null;
       createdAt: string;
       updatedAt: string;
   }

   const UserList: React.FC = () => {
       const [users, setUsers] = useState<UserResponse[]>([]);
       const [loading, setLoading] = useState(true);
       const [error, setError] = useState<string | null>(null);
       const navigate = useNavigate();

       const fetchUsers = async () => {
           try {
               setLoading(true);
               const token = localStorage.getItem('token');
               const response = await axios.get(`${API_URL}/users`, {
                   headers: { Authorization: `Bearer ${token}` }
               });
               // A te általad küldött válaszban a data egy objektum: { data: [...] } ?
               // Vagy közvetlenül a tömb jön vissza?
               // A példád alapján:
               // { "data": [ ... ] } VAGY [ ... ]
               // Ha a backend közvetlenül List<CadetResponse>-t ad vissza, akkor response.data a tömb
               // Ha Page<CadetResponse>-t, akkor response.data.content vagy response.data.

               // A te példádban a válasz sima tömbnek tűnik a JSON alapján: [ ... ]
               // De ha { "data": [...] } formátumban jön (pl. axios néha így formázza), akkor figyelni kell.
               // Axios response.data a body. Ha a body maga a tömb, akkor jó.

               setUsers(response.data);
               setError(null);
           } catch (err: any) {
               setError('Nem sikerült betölteni a felhasználókat.');
               console.error(err);
           } finally {
               setLoading(false);
           }
       };

       useEffect(() => {
           fetchUsers();
       }, []);

       const handleDelete = async (id: string, username: string) => {
           if (window.confirm(`Biztosan törölni szeretnéd ${username} felhasználót?`)) {
               try {
                   const token = localStorage.getItem('token');
                   await axios.delete(`${API_URL}/users/${id}`, {
                       headers: { Authorization: `Bearer ${token}` }
                   });
                   setUsers(users.filter(user => user.id !== id));
               } catch (err) {
                   alert('Hiba történt a törlés során.');
               }
           }
       };

       const formatDate = (dateString: string) => {
           return new Date(dateString).toLocaleDateString('hu-HU', {
               year: 'numeric',
               month: 'long',
               day: 'numeric',
               hour: '2-digit',
               minute: '2-digit'
           });
       };

       if (loading) return (
           <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
               <CircularProgress />
           </Box>
       );

       return (
           <Box>
               <Typography variant="h4" sx={{ mb: 4, fontWeight: 'bold' }}>
                   Felhasználók kezelése
               </Typography>

               {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

               <TableContainer component={Paper} elevation={3}>
                   <Table sx={{ minWidth: 650 }}>
                       <TableHead sx={{ bgcolor: 'rgba(255,255,255,0.05)' }}>
                           <TableRow>
                               <TableCell>Felhasználó</TableCell>
                               <TableCell>Email</TableCell>
                               <TableCell>Regisztrált</TableCell>
                               <TableCell>Utolsó módosítás</TableCell>
                               <TableCell>Role-ok</TableCell>
                               <TableCell align="right">Műveletek</TableCell>
                           </TableRow>
                       </TableHead>
                       <TableBody>
                           {users.map((user) => (
                               <TableRow key={user.id} hover>
                                   <TableCell>
                                       <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                           <Avatar src={user.avatarUrl || undefined}>
                                               <PersonIcon />
                                           </Avatar>
                                           <Box>
                                               <Typography variant="subtitle2" sx={{ fontWeight: 'bold'}}>
                                                   {user.username}
                                               </Typography>
                                               <Typography variant="caption" color="text.secondary">
                                                   Nincs megadva név
                                               </Typography>
                                           </Box>
                                       </Box>
                                   </TableCell>
                                   <TableCell>{user.email}</TableCell>
                                   <TableCell>{formatDate(user.createdAt)}</TableCell>
                                   <TableCell>{formatDate(user.updatedAt)}</TableCell>
                                   <TableCell>
                                       <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                                           {user.roles.map((role, index) => (
                                               <Chip
                                                   key={index} // Mivel string, használhatjuk az index vagy magát a stringet key-nek
                                                   label={role.toString().replace('ROLE_', '')}
                                                   size="small"
                                                   color={role.toString() === 'ROLE_ADMIN' ? 'secondary' : 'default'}
                                                   variant="outlined"
                                               />
                                           ))}
                                       </Box>
                                   </TableCell>
                                   <TableCell align="right">
                                       <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1}}>
                                           <Tooltip title="Szerkesztés">
                                               <IconButton
                                                   color="primary"
                                                   onClick={() => navigate(`/admin/users/${user.id}`)}
                                               >
                                                   <EditIcon />
                                               </IconButton>
                                           </Tooltip>
                                           <Tooltip title="Törlés">
                                               <IconButton
                                                   color="error"
                                                   onClick={() => handleDelete(user.id, user.username)}
                                               >
                                                   <DeleteIcon />
                                               </IconButton>
                                           </Tooltip>
                                       </Box>
                                   </TableCell>
                               </TableRow>
                           ))}
                       </TableBody>
                   </Table>
               </TableContainer>
           </Box>
       );
   };

   export default UserList;