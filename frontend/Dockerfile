# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached across builds that only
# change application source, not the dependency set.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime stage ---
FROM nginx:alpine

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
