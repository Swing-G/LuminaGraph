# LuminaGraph (Ragent) — 服务器部署手册

> 服务器: 117.72.216.56 / Ubuntu 22.04 / 8GB / 2核  
> 仓库: https://github.com/Swing-G/LuminaGraph  
> 架构: Docker Compose 部署（基础设施 + 应用全部容器化）  
> 已有项目: zhiguang（systemd 直跑 JAR，互不影响）

---

## 一、部署架构

```
117.72.216.56
│
├─ :80  Nginx → zhiguang 前端 + API    (现有，不动)
├─ :81  Nginx → ragent 前端 + API      (新增)
│       /api/* → localhost:8081 (Spring Boot 容器)
│
├─ Docker Compose (ragent 独立网络)
│   ├─ postgres:5433    pgvector/pgvector:pg16
│   ├─ redis:6380       redis:7-alpine
│   ├─ rmqnamesrv:19876 apache/rocketmq:5.2.0
│   ├─ rmqbroker:10911  apache/rocketmq:5.2.0
│   ├─ dashboard:8082   rocketmq-dashboard:2.1.0
│   ├─ rustfs:9002/9003 rustfs/rustfs:1.0.0-alpha.72
│   ├─ mcp-server:9100  eclipse-temurin:17-jre
│   └─ app:8081         eclipse-temurin:17-jre (Spring Boot)
│
└─ 宿主机直装
    ├─ zhiguang (systemd, :8080)
    ├─ Redis :6379, MySQL, ES, Kafka  (zhiguang 用)
    └─ Java 21, Maven, Node.js, Nginx
```

## 二、首次部署（服务器上执行）

### 2.1 安装 Docker

```bash
# 如果还没有 Docker
curl -fsSL https://get.docker.com | bash
sudo usermod -aG docker $USER
newgrp docker
docker --version

# 安装 Docker Compose 插件（如果没有）
docker compose version
```

### 2.2 创建目录结构

```bash
mkdir -p /opt/ragent-config   /var/www/ragent
```

### 2.3 Clone 代码

```bash
cd /opt
git clone git@github.com:Swing-G/LuminaGraph.git ragent
cd /opt/ragent
git checkout main
```

> 如果 clone 慢，参照现有项目的 SSH 密钥配置。

### 2.4 上传配置文件

把 `docs/deploy/application-docker.yml` 上传到服务器并修改：

```bash
# 本地执行:
scp docs/deploy/application-docker.yml root@117.72.216.56:/opt/ragent-config/application.yml

# 服务器上修改 API Key 和管理员密码:
vim /opt/ragent-config/application.yml
#   - 改 ai.providers.xxx.apiKey 为你的真实 Key
#   - 改 ragent.admin.password 为你的管理员密码
```

### 2.5 初始化数据库

```bash
# 先启动 PostgreSQL（不启动整个 stack）
cd /opt/ragent
docker compose up -d postgres
sleep 5

# 运行建表脚本
for f in resources/database/*.sql; do
  echo "执行: $f"
  docker exec -i ragent-postgres psql -U postgres -d ragent < "$f"
done
```

### 2.6 配置 Nginx

```bash
# 复制配置
sudo cp docs/deploy/nginx-ragent.conf /etc/nginx/sites-available/ragent

# 启用站点
sudo ln -sf /etc/nginx/sites-available/ragent /etc/nginx/sites-enabled/ragent

# 测试并重载
sudo nginx -t && sudo systemctl reload nginx
```

### 2.7 构建并启动全部服务

```bash
cd /opt/ragent

# 构建后端
mvn clean package -pl bootstrap,mcp-server -am -DskipTests

# 构建前端
cd frontend && npm install --legacy-peer-deps && VITE_API_BASE_URL=/api/ragent npm run build && cd ..

# 部署前端
sudo rm -rf /var/www/ragent/*
sudo cp -r frontend/dist/* /var/www/ragent/

# 启动所有 Docker 服务
docker compose up -d --build

# 查看状态
docker compose ps
docker compose logs -f app
```

### 2.8 验证

```bash
# 容器状态
docker compose ps                                 # 全部应为 Up/healthy

# 后端
curl http://localhost:8081/                       # Spring Boot 响应

# 前端
curl -s http://localhost:81/ | head -20           # 应为 HTML

# RocketMQ Dashboard
curl -s http://localhost:8082/ | head -5          # 应返回页面

# RustFS Console
curl -s http://localhost:9003/ | head -5          # 应返回页面
```

浏览器访问：
- **LuminaGraph 前端**: `http://117.72.216.56:81`
- **管理后台**: `http://117.72.216.56:81/admin`
- **RocketMQ Dashboard**: `http://117.72.216.56:8082`
- **RustFS Console**: `http://117.72.216.56:9003`

---

## 三、GitHub Actions 自动部署

### 3.1 安装 Self-hosted Runner

```bash
mkdir -p /opt/actions-runner-ragent && cd /opt/actions-runner-ragent

# 下载 runner
curl -o actions-runner-linux-x64.tar.gz -L \
  https://github.com/actions/runner/releases/download/v2.322.0/actions-runner-linux-x64-2.322.0.tar.gz
tar xzf actions-runner-linux-x64.tar.gz
echo "." > .auto_update

export RUNNER_ALLOW_RUNASROOT=1

# 去 https://github.com/Swing-G/LuminaGraph → Settings → Actions → Runners → New self-hosted runner 拿 token
./config.sh --url https://github.com/Swing-G/LuminaGraph --token XXXX --name ragent-server --labels ragent-server

sudo ./svc.sh install
sudo ./svc.sh start
```

### 3.2 打开阿里云安全组

| 端口 | 用途 |
|------|------|
| 81 | ragent 前端 + API |
| 8082 | RocketMQ Dashboard |
| 9003 | RustFS Console |

> 后端 8081 / 9100 / 19876 等内部端口**不需要**开公网。

### 3.3 触发部署

```bash
# 本地 push 到 main 分支即可自动触发
git push origin main
```

---

## 四、日常运维

### 常用命令

```bash
cd /opt/ragent

# 查看所有容器
docker compose ps

# 查看后端日志
docker compose logs -f app

# 查看某个服务日志
docker compose logs -f postgres
docker compose logs -f redis
docker compose logs -f rmqnamesrv

# 重启单个服务
docker compose restart app

# 重启全部
docker compose restart

# 停止全部
docker compose down

# 更新配置后重启（yml 改了的话）
docker compose restart app

# Runner 状态
sudo systemctl status actions.runner.Swing-G-LuminaGraph.ragent-server
```

### 配置变更流程

```bash
# 1. 修改服务器上的配置
vim /opt/ragent-config/application.yml

# 2. 重启应用容器
cd /opt/ragent && docker compose restart app

# 3. 检查日志
docker compose logs -f app
```

### 数据库备份

```bash
docker exec ragent-postgres pg_dump -U postgres ragent > /opt/backup/ragent_$(date +%Y%m%d).sql
```

---

## 五、端口速查

| 服务 | 容器端口 | 宿主机端口 | 公网 |
|------|---------|-----------|------|
| Nginx（前端+API） | — | **81** | ✅ |
| Spring Boot | 8080 | 8081 | ❌ |
| PostgreSQL | 5432 | 5433 | ❌ |
| Redis | 6379 | 6380 | ❌ |
| RocketMQ NS | 9876 | 19876 | ❌ |
| RocketMQ Broker | 10911 | 10911 | ❌ |
| RocketMQ Dashboard | 8082 | 8082 | ✅ |
| MCP Server | 9099 | 9100 | ❌ |
| RustFS API | 9000 | 9002 | ❌ |
| RustFS Console | 9001 | 9003 | ✅ |

---

## 六、故障排查

| 问题 | 检查 |
|------|------|
| 容器起不来 | `docker compose logs 服务名` |
| 前端 404 | 检查 `/var/www/ragent/` 是否有文件，nginx -t |
| 后端连不上 DB | `docker exec ragent-postgres pg_isready -U postgres -d ragent` |
| MCP 工具加载失败 | 检查 mcp-server 容器是否健康，`rag.mcp.servers[0].url` 是否正确 |
| RocketMQ 连不上 | 检查 rmqnamesrv 日志 `docker compose logs rmqnamesrv` |

---

## 七、将来加域名

```nginx
# /etc/nginx/sites-available/ragent（把 listen 81 改为 80 + 域名）
server {
    listen 80;
    server_name liuguangyf.top;    # 你的域名
    ...
}
```

前端构建时去掉端口号依赖：
```bash
VITE_API_BASE_URL=/api/ragent npm run build   # 保持不变，相对路径
```
