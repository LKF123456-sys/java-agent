# ============================================================
# 赛博AI平台 - 多阶段构建Dockerfile
# 构建阶段：Maven构建(JDK 21)
# 运行阶段：JRE 21运行时
# ============================================================

# ==================== 第一阶段：Maven构建 ====================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build
# 复制pom.xml（利用Docker缓存层，依赖下载只在pom.xml变更时重新执行）
COPY pom.xml .
# 预先下载Maven依赖
RUN mvn dependency:go-offline -B
# 复制源代码
COPY src ./src
# 执行Maven构建，跳过测试
RUN mvn clean package -DskipTests -B

# ==================== 第二阶段：JRE运行时 ====================
FROM eclipse-temurin:21-jre
WORKDIR /app
# 创建日志和上传目录
RUN mkdir -p /app/logs /app/uploads
# 从构建阶段复制可执行JAR文件
COPY --from=builder /build/target/cyber-ai-platform.jar app.jar
# 暴露应用端口
EXPOSE 8080
# 设置JVM参数环境变量
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
