# Front-end em duas etapas (plano 07-01 D-3, D-7): o Node gera o build de produção do
# Vite; o nginx serve os arquivos estáticos e repassa /api para a API.

FROM node:22.23.4-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build


FROM nginx:1.28.3-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
