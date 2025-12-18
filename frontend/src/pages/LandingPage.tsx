import React from 'react';
import { Box, Typography } from '@mui/material';
import LoadingScreen from '../components/LoadingScreen';
import { useTranslation } from 'react-i18next';

 const LandingPage: React.FC = () => {
    const { t } = useTranslation();

    return (
        <Box sx={{ textAlign: 'center', mt: 8 }}>
            <Typography variant="h2" gutterBottom fontWeight="bold">
                {t('welcome')}
            </Typography>
            <Typography variant="h5" color="text.secondary" gutterBottom>
                {t('landing')}
            </Typography>
            <LoadingScreen />
        </Box>
    );
};

export default LandingPage;