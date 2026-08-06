#!/bin/bash
# 自动部署脚本 - 在后台运行，避免 systemctl restart 杀死当前进程
# 用法: nohup bash deploy.sh > /tmp/deploy.log 2>&1 &

cd /opt/springaitest || exit 1

echo "$(date): 开始部署..."
sleep 2

echo "$(date): git pull..."
git pull origin main 2>&1

echo "$(date): mvn package..."
mvn clean package -DskipTests -q 2>&1

echo "$(date): mkdir..."
mkdir -p /opt/springaitest/data/generated-files

echo "$(date): restart service..."
systemctl restart springaitest

sleep 5
echo "$(date): service status: $(systemctl is-active springaitest)"
echo "$(date): 部署完成"
