# ============================================================================
# LuminaGraph (Ragent) — Bootstrap 主应用镜像
# 构建前提: mvn clean package -pl bootstrap -DskipTests 已完成
# ============================================================================

FROM eclipse-temurin:17-jre

WORKDIR /app

# 复制已构建的 JAR（GitHub Actions 中 mvn package 后执行 docker build）
COPY bootstrap/target/bootstrap-*.jar /app/app.jar

# 配置文件挂载点（运行时通过 docker-compose volume 挂入）
RUN mkdir -p /app/config

EXPOSE 8080

ENTRYPOINT exec java $JAVA_OPTS \
  -jar /app/app.jar \
  --spring.config.location=/app/config/application.yml
