# ============================================================
# 赛博AI平台 - 多阶段构建Dockerfile
# 构建阶段：前端构建(Node.js) + Maven构建(JDK)
# 运行阶段：JRE运行时 + 前端静态资源
# ============================================================

# ==================== 第一阶段：前端构建 ====================
FROM node:20-alpine AS frontend-builder

# 设置工作目录
WORKDIR /app/frontend

# 复制前端package.json和package-lock.json（利用Docker缓存层）
COPY frontend/package*.json ./

# 安装前端依赖（使用npm ci确保依赖版本一致性）
RUN npm ci --registry=https://registry.npmmirror.com

# 复制前端源代码
COPY frontend/ ./

# 构建前端产物（输出到dist目录）
RUN npm run build

# ==================== 第二阶段：Maven构建 ====================
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder

# 设置工作目录
WORKDIR /app

# 复制Maven配置文件（利用Docker缓存层，依赖下载只在pom.xml变更时重新执行）
COPY pom.xml ./

# 预先下载Maven依赖（这一层会被缓存，除非pom.xml变更）
RUN mvn dependency:go-offline -B -DskipTests

# 复制后端源代码
COPY src/ ./src/

# 复制第一阶段构建好的前端产物到Spring Boot static目录
COPY --from=frontend-builder /app/frontend/dist/ ./src/main/resources/static/

# 执行Maven构建，跳过测试（测试在CI/CD流水线中单独执行）
RUN mvn clean package -DskipTests -B

# ==================== 第三阶段：JRE运行时 ====================
FROM eclipse-temurin:21-jre-alpine AS runtime

# 安装必要的系统工具（用于健康检查和调试）
# tzdata：设置时区
# curl：健康检查
# dumb-init：正确的PID 1进程管理，处理信号转发
RUN apk add --no-cache tzdata curl dumb-init \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

# 创建非root用户运行应用（安全最佳实践）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 设置工作目录
WORKDIR /app

# 从构建阶段复制可执行JAR文件
COPY --from=backend-builder /app/target/cyber-ai-platform.jar app.jar

# 创建日志目录并设置权限
RUN mkdir -p /app/logs \
    && chown -R appuser:appgroup /app

# 切换到非root用户
USER appuser

# 暴露应用端口
EXPOSE 8080

# 设置JVM参数环境变量（可通过docker-compose或运行时覆盖）
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof"

# 健康检查配置
# 使用curl访问Actuator健康端点，每30秒检查一次，超时10秒，重试3次
# 启动等待时间60秒（给Spring Boot足够的启动时间）
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 使用dumb-init作为PID 1，正确处理信号和僵尸进程
ENTRYPOINT ["dumb-init", "--"]

# 启动应用
# 1. exec格式确保Java进程接收SIGTERM信号优雅关闭
# 2. JAVA_OPTS可通过环境变量覆盖
# 3. Spring Profile可通过SPRING_PROFILES_ACTIVE环境变量设置
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ============================================================
# 构建说明：
# 1. 构建命令：docker build -t cyber-ai-platform:latest .
# 2. 运行命令：docker run -p 8080:8080 cyber-ai-platform:latest
# 3. 多阶段构建优点：
#    - 最终镜像只包含JRE和应用JAR，不包含构建工具（Node.js/Maven/JDK）
#    - 镜像体积更小（约300-400MB vs 构建阶段1GB+）
#    - 安全性更高（无编译工具链，非root用户运行）
#    - 构建缓存优化，依赖层只在pom.xml/package.json变更时重新构建
# ============================================================
