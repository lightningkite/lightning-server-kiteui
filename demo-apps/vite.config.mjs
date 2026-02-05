import { defineConfig } from 'vite'

export default defineConfig({
    root: "kotlin",
    server: {
        host: true,
        port: 8021,
        allowedHosts: ["localhost:8021"],
        proxy: {
            '/api': {
                target: 'http://localhost:8020',
                // changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api/, ''),
                ws: true,
            }
        }
    },
})