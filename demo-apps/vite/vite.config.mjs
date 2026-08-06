import { defineConfig } from 'vite'

export default defineConfig({
    root: "kotlin",
    server: {
        host: true,
        port: 8091,
        allowedHosts: ["localhost:8091"],
        proxy: {
            '/api': {
                target: 'http://localhost:8090',
                // changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api/, ''),
                ws: true,
            }
        }
    },
})