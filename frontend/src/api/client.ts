import axios from 'axios';

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request interceptor: Minden kérés előtt lefut
apiClient.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Response interceptor: Minden válasz után lefut
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        // Ha 401-et kapunk (lejárt token vagy érvénytelen), kiléptetjük a usert
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('token');
            // Itt lehetne egy window.location.href = '/login' is,
            // de elegánsabb, ha az AuthContext kezeli.
        }
        return Promise.reject(error);
    }
);

export default apiClient;