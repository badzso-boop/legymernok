import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
    Box, Card, CardContent, Typography, TextField, Button,
    Alert, Link as MuiLink
} from '@mui/material';
import { useAuth } from '../../context/AuthContext';
import apiClient from '../../api/client';
import type { LoginResponse } from '../../types/auth';

// Validációs séma (Zod)
const loginSchema = z.object({
    username: z.string().min(1, { message: 'usernameMandatory' }),
    password: z.string().min(1, { message: 'passwordMandatory' }),
});

type LoginFormInputs = z.infer<typeof loginSchema>;

const LoginPage: React.FC = () => {
    const { t } = useTranslation();
    const { login } = useAuth();
    const navigate = useNavigate();
    const [error, setError] = useState<string | null>(null);

    const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginFormInputs>({
        resolver: zodResolver(loginSchema),
    });

    const onSubmit = async (data: LoginFormInputs) => {
        setError(null);
        try {
            // Backend hívás
            const response = await apiClient.post<LoginResponse>('/auth/login', data);

            // Sikeres belépés -> Token mentése (Context)
            login(response.data);

            // TODO: iranyitsd at majd az admin feluletre
            // Átirányítás (ha admin, akkor adminra, amúgy főoldalra - egyelőre főoldal)
            navigate('/');
        } catch (err: any) {
            if (err.response?.status === 401) {
                setError(t('errorUsernameOrPwd'));
            } else {
                setError(t('errorLogin'));
            }
        }
    };

    return (
        <Box
            sx={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '60vh' // Hogy függőlegesen is középen legyen (a MainLayout paddingjával együtt)
            }}
        >
            <Card
                sx={{
                    minWidth: 350,
                    maxWidth: 450,
                    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.37)', // Üveges/Lebegő hatás
                    backdropFilter: 'blur( 4px )',
                    border: '1px solid rgba(255, 255, 255, 0.18)',
                    borderRadius: 2
                }}
            >
                <CardContent sx={{ p: 4 }}>
                    <Typography variant="h4" component="h1" gutterBottom textAlign="center" fontWeight="bold" color="primary">
                        {t('login')}
                    </Typography>

                    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

                    <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ mt: 1 }}>
                        <TextField
                            margin="normal"
                            required
                            fullWidth
                            id="username"
                            label={t('username') || "Felhasználónév"}
                            autoComplete="username"
                            autoFocus
                            error={!!errors.username}
                            helperText={errors.username?.message ? t(errors.username.message) : ""}
                            {...register('username')}
                        />

                        <TextField
                            margin="normal"
                            required
                            fullWidth
                            label={t('password') || "Jelszó"}
                            type="password"
                            id="password"
                            autoComplete="current-password"
                            error={!!errors.password}
                            helperText={errors.password?.message ? t(errors.password.message) : ""}
                            {...register('password')}
                        />

                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 3 }}>
                            <Button
                            type="submit"
                            variant="contained"
                            disabled={isSubmitting}
                            sx={{ px: 4, py: 1 }}
                            >
                            {t('login')}
                            </Button>
                        </Box>

                        <Box sx={{ mt: 2, textAlign: 'center' }}>
                            <Typography variant="body2" color="text.secondary">
                            {t('noProfile')}{' '}
                                <MuiLink component="button" type="button" onClick={() => navigate('/register')}>
                                    {t('register')}
                                </MuiLink>
                            </Typography>
                        </Box>
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
};

export default LoginPage;