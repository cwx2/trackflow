# ===== Stage 1: 构建阶段 =====
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 先只复制 pom.xml，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# 再复制源码构建
COPY src/ src/

RUN ./mvnw clean package -DskipTests -q

# ===== Stage 2: 运行阶段 =====
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# 创建非 root 用户运行
RUN addgroup -S trackflow && adduser -S trackflow -G trackflow

# 从构建阶段复制 jar
COPY --from=builder /build/target/trackflow-server-*.jar app.jar

# 切换到非 root 用户
USER trackflow

EXPOSE 8090

# 生产环境使用 prod profile
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar", \
  "--spring.profiles.active=prod"]
