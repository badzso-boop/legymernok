import React from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { Rocket } from 'lucide-react';
import { useTranslation } from 'react-i18next';

const LoadingScreen: React.FC = () => {
    const { t } = useTranslation();

    return (
        <Box
            sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            height: '70vh',
            gap: 3
            }}
        >
            <Box sx={{ position: 'relative', display: 'inline-flex' }}>
                <CircularProgress size={80} thickness={2} sx={{ color: 'primary.main' }} />
                <Box
                    sx={{
                    top: 0,
                    left: 0,
                    bottom: 0,
                    right: 0,
                    position: 'absolute',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    }}
                >
                    <Rocket size={32} className="animate-bounce text-blue-400" />
                </Box>
            </Box>
            <Typography variant="h5" className="animate-pulse font-mono">
                {t('missionInProgress')}
            </Typography>
        </Box>
    );
};

export default LoadingScreen;