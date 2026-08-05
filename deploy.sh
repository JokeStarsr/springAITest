#!/bin/bash
# Spring AI Test - 部署脚本
# 用法: GIT_TOKEN=your_token ssh root@115.159.221.62 'bash -s' < deploy.sh
# 或者上传到服务器后执行: GIT_TOKEN=your_token bash deploy.sh

set -e

APP_DIR="/opt/springaitest"
SERVICE_NAME="springaitest"
GIT_URL="https://${GIT_TOKEN}@github.com/JokeStarsr/springAITest.git"

echo "===== 开始部署 ====="

if [ -z "$GIT_TOKEN" ]; then
    echo "错误: 请设置 GIT_TOKEN 环境变量"
    echo "用法: GIT_TOKEN=your_token bash deploy.sh"
    exit 1
fi

# 1. 拉取最新代码
if [ -d "$APP_DIR" ]; then
    cd "$APP_DIR"
    echo "拉取最新代码..."
    git pull "$GIT_URL" main
else
    echo "克隆仓库..."
    git clone "$GIT_URL" "$APP_DIR"
    cd "$APP_DIR"
fi

# 2. 编译打包
echo "编译打包..."
export MAVEN_OPTS="-Dfile.encoding=UTF-8"
mvn clean package -DskipTests -q

# 3. 停止旧服务
echo "停止旧服务..."
systemctl stop "$SERVICE_NAME" 2>/dev/null || true

# 4. 复制新 jar 包
JAR_FILE=$(ls target/*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "错误: 未找到编译产物"
    exit 1
fi
echo "使用 JAR: $JAR_FILE"
cp "$JAR_FILE" "${APP_DIR}/app.jar"

# 5. 重启服务
echo "重启服务..."
systemctl daemon-reload 2>/dev/null || true
systemctl restart "$SERVICE_NAME" 2>/dev/null || true

# 6. 等待启动并检查
echo "等待服务启动..."
sleep 5
if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo "✅ 服务启动成功"
else
    echo "❌ 服务启动失败，检查日志: journalctl -u $SERVICE_NAME -n 50"
    systemctl status "$SERVICE_NAME" --no-pager
    exit 1
fi

# 7. 测试健康检查
echo "测试健康检查..."
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://localhost:8080/ || echo "⚠️ 健康检查未通过"

echo "===== 部署完成 ====="
echo "访问地址: http://115.159.221.62:8080"