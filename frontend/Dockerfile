FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# nginx's entrypoint runs envsubst on /etc/nginx/templates/*.template (only our own variable).
ENV NGINX_ENVSUBST_FILTER=APP_API_KEY
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
