FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build
FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app ./
USER node
EXPOSE 3000
CMD ["npm","start"]

