# ad-video-gen-java

Java/Spring Boot 版本的广告视频生成 Demo，按 `volcengine/ai-app-lab/demohouse/ad_video_gen` README 中的多 Agent 和三模型分工实现。

## 功能

- LLM：`Doubao-Seed-1.6`，负责理解用户需求、页面素材和 Agent 推理
- 图像模型：`Doubao-Seedream 4.5 pro`，负责文生图 / 参考图生图
- 视频模型：`Doubao-Seedance 1.0 pro`，负责图文生视频
- 市场分析 Agent：生成目标人群、卖点、关键词和创意策略
- 导演 Agent：生成短视频标题、脚本和分镜提示词
- 评估 Agent：输出评分、优势、风险和优化建议
- Multimedia Agent：先调用 Seedream 生成分镜图，再调用 Seedance 生成视频
- Release Agent：生成发布文案、话题标签和短链
- 文档版任务状态机：`video_task` + `video_task_context` 持久化保存任务状态和上下文
- 候选素材机制：每个分镜可生成多张候选图、多段候选视频，并支持人工选择
- 本地 FFmpeg 合成：最终视频落盘到 `./data/final-videos`，通过 `/final-videos/**` 访问，不使用 TOS 存储
- 前端工作台：创建任务、启动任务、查看阶段产物、人工选择素材、从指定阶段重新生成

## Prompt 对齐

项目已将原 Python 实现中的 5 个 `prompt.py` 原样复制到 `src/main/resources/prompts`：

- `market-agent/prompt.py`
- `director-agent/prompt.py`
- `evaluate-agent/prompt.py`
- `release-agent/prompt.py`
- `multimedia-agent/prompt.py`

Java 代码通过 `PromptService` 解析这些文件里的 `PROMPT_XXX = """..."""` 常量，避免手工改写导致提示词与原项目不一致。

## 启动

执行 Maven 打包时会自动安装项目所需的 Node.js/npm、构建前端，并将
`frontend/dist` 复制到 `target/classes/static` 后打入 Spring Boot JAR。
前端构建产物只存在于 `target`，不会写入源码资源目录或加入 Git 管理。

```bash
mvn clean package
java -jar target/ad-video-gen-java-0.0.1-SNAPSHOT.jar
```

可选环境变量：

```bash
export ARK_API_KEY=你的方舟APIKey
export LLM_MODEL=doubao-seed-1-6-250615
export LLM_ENDPOINT_ID=你的LLM接入点ID

export IMAGE_MODEL=doubao-seedream-4-5-pro
export CGT_ENDPOINT_ID=你的Seedream接入点ID
export IMAGE_GENERATION_ENABLED=true

export VIDEO_MODEL=doubao-seedance-1-0-pro
export T2V_ENDPOINT_ID=你的Seedance接入点ID
export VIDEO_GENERATION_ENABLED=true

export PUBLIC_BASE_URL=http://localhost:8080
export FFMPEG_BINARY=ffmpeg
export FFMPEG_OUTPUT_DIR=./data/final-videos
```

未配置 `ARK_API_KEY` 或未开启 `IMAGE_GENERATION_ENABLED` / `VIDEO_GENERATION_ENABLED` 时，服务会使用本地兜底内容，方便先跑通整体流程。

最终合成阶段会调用本机 FFmpeg。请确保 `ffmpeg` 在 `PATH` 中，或通过 `FFMPEG_BINARY` 指定可执行文件路径。

## 前端工作台

前端位于 `frontend` 目录，默认代理后端 `http://localhost:8080`。

```bash
cd frontend
npm install
npm run dev
```

打开：

```text
http://localhost:8002/
```

本地开发仍可单独运行 Vite；正式打包和 Docker 部署时，前端由 Spring Boot
直接托管，无需单独部署。

## Docker 部署

镜像使用 Java 17 运行，并内置最终视频合成所需的 FFmpeg。容器默认监听
`48080`，H2 数据库与最终视频分别持久化到两个宿主机目录：

```bash
docker build -t ad-video-gen-java .

docker run -d \
  --name ad-video-gen-java \
  -p 48080:48080 \
  -e ARK_API_KEY=你的方舟APIKey \
  -e IMAGE_GENERATION_ENABLED=true \
  -e VIDEO_GENERATION_ENABLED=true \
  -v "$(pwd)/docker-data/db:/app/data/db" \
  -v "$(pwd)/docker-data/videos:/app/data/videos" \
  ad-video-gen-java
```

访问地址：

```text
前端工作台：http://localhost:48080/
H2 Console：http://localhost:48080/h2-console
最终视频：http://localhost:48080/final-videos/{文件名}.mp4
```

容器内 H2 JDBC URL 为：

```text
jdbc:h2:file:/app/data/db/ad-video-gen;MODE=MySQL
```

工作台按设计文档中的状态机推进：

```text
CREATED
-> MARKET_PLANNING
-> SHOT_SCRIPT_GENERATING
-> IMAGE_GENERATING
-> IMAGE_EVALUATING
-> VIDEO_GENERATING
-> VIDEO_EVALUATING
-> FINAL_COMPOSING
-> COMPLETED
```

用户可以在前端查看历史任务，并继续编辑历史任务。流程不会自动执行到下一步：每次点击“开始生成 / 进入下一步”只推进一个阶段，阶段产物生成后进入 `WAITING_REVIEW`，用户确认或编辑后再手动进入下一步。分镜脚本、图片评分、视频评分都支持 JSON 编辑保存；保存后会清空受影响的后续产物，避免后续素材和用户编辑不一致。

## API

### 文档版任务接口

创建任务：

```bash
curl -X POST http://localhost:8080/api/video-tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "inputType": "product_image",
    "text": "参考商品图片，生成一条 15 秒带货广告视频。商品：玻璃水。卖点：去虫胶、无甲醇、去油膜。",
    "imageUrls": [
      "data:image/jpeg;base64,..."
    ],
    "videoType": "商品展示视频",
    "platform": "抖音",
    "duration": 15,
    "aspectRatio": "9:16",
    "style": "真实生活方式、明亮、轻快",
    "generateImageCount": 2,
    "generateVideoCount": 1
  }'
```

启动任务：

```bash
curl -X POST http://localhost:8080/api/video-tasks/{taskId}/start
```

进入下一步：

```bash
curl -X POST http://localhost:8080/api/video-tasks/{taskId}/advance
```

查询最近历史任务：

```bash
curl http://localhost:8080/api/video-tasks
```

查询详情：

```bash
curl http://localhost:8080/api/video-tasks/{taskId}
```

保存分镜脚本 / 图片评分 / 视频评分编辑：

```bash
curl -X POST http://localhost:8080/api/video-tasks/{taskId}/context \
  -H 'Content-Type: application/json' \
  -d '{
    "shots": [],
    "scoredImageGroups": null,
    "scoredVideoGroups": null
  }'
```

人工选择候选图片/视频：

```bash
curl -X POST http://localhost:8080/api/video-tasks/{taskId}/select-assets \
  -H 'Content-Type: application/json' \
  -d '{
    "selectedImages": [
      { "shotId": "shot_001", "assetId": "img_shot_001_1" }
    ],
    "selectedVideos": [
      { "shotId": "shot_001", "assetId": "vid_shot_001_1" }
    ]
  }'
```

从指定阶段重新生成：

```bash
curl -X POST http://localhost:8080/api/video-tasks/{taskId}/regenerate \
  -H 'Content-Type: application/json' \
  -d '{
    "fromStage": "IMAGE_GENERATING",
    "reason": "候选图片不够突出商品卖点"
  }'
```

可重新生成阶段：

```text
MARKET_PLANNING
SHOT_SCRIPT_GENERATING
IMAGE_GENERATING
VIDEO_GENERATING
FINAL_COMPOSING
```

任务上下文会持久化到 H2：创建请求原文保存在 `video_task.request_json`，完整上下文保存在 `video_task_context.context_json`。

旧版 `/api/ad-videos` 接口已移除，当前主流程统一使用 `/api/video-tasks`。

## 持久化

- H2 数据文件：`./data/ad-video-gen.mv.db`
- H2 Console：`http://localhost:8080/h2-console`
- JDBC URL：`jdbc:h2:file:./data/ad-video-gen;MODE=MySQL;AUTO_SERVER=TRUE`
- 用户名：`sa`
- 密码：空
- 旧版任务服务重启时，仍处于 `RUNNING` 的任务会自动标记为 `FAILED`，然后可以调用 retry 从已保存的 checkpoint 继续。
- 文档版任务的状态和上下文保存在 `video_task` / `video_task_context` 表中。

## 项目结构

```text
src/main/java/com/volcengine/demo/advideo
├── agent          # market/director/evaluate/release Agent
├── client         # Doubao-Seed-1.6 / Seedream / Seedance HTTP 客户端
├── config         # 配置属性和 HTTP 客户端配置
├── controller     # REST API
├── dto            # 请求/响应模型
├── orchestrator   # 多 Agent 编排
└── service        # 任务、多媒体、短链服务
```
