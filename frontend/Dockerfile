# The Beacon web app: built with Node, served by nginx, which also passes /api to the API.

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# "npm run build" generates the API client from openapi.json first (prebuild).
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
# The scheme users reach Beacon with; https behind Tailscale. Sets the cookies' Secure flag.
ENV PUBLIC_SCHEME=https \
    API_UPSTREAM=api:8080
EXPOSE 80
