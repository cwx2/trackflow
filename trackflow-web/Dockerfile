# ===== Stage 1: 构建阶段 =====
FROM node:20-alpine AS builder

WORKDIR /build

# 先复制 package.json 利用层缓存
COPY package.json package-lock.json* ./

RUN npm ci --prefer-offline

# 复制源码并构建
COPY . .

RUN npm run build

# ===== Stage 2: 运行阶段（nginx 静态托管）=====
FROM nginx:alpine AS runtime

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
