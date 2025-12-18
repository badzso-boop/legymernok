import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    watch: {
      usePolling: true, // <--- EZ KÖTELEZŐ WSL2-nél, ha lassú a frissítés
    },
    host: true, // Engedélyezi az elérést a hálózatról (pl. Dockerből)
    port: 5173,
  },
})
