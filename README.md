# ad-video-gen-java

Java/Spring Boot 版本的广告视频生成 Demo，按 `volcengine/ai-app-lab/demohouse/ad_video_gen` README 中的多 Agent 和三模型分工实现。

## 功能

- LLM：`Doubao-Seed-1.6`，负责理解用户需求、页面素材和 Agent 推理
- 图像模型：`Doubao-Seedream 4.5 pro`，负责文生图 / 参考图生图
- 视频模型：`Doubao-Seedance 1.0 pro`，负责图文生视频
- 商品图广告流程：从商品图片 + 文本需求出发，依次完成营销策划、分镜脚本、图片候选、视频候选和最终合成
- 视频拆解广告流程：从本地视频或视频 URL 出发，先做视频理解与分镜总结，再进入图片候选、分镜视频和最终广告合成
- 市场分析 Agent：生成目标人群、卖点、关键词和创意策略
- 导演 Agent：生成短视频标题、脚本和分镜提示词
- 视频理解 Agent：根据上传视频总结固定结构的分镜脚本
- 评估 Agent：输出评分、优势、风险和优化建议
- Multimedia Agent：先调用 Seedream 生成分镜图，再调用 Seedance 生成视频
- Release Agent：生成发布文案、话题标签和短链
- 文档版任务状态机：`video_task` + `video_task_context` 持久化保存任务状态和上下文
- 候选素材机制：每个分镜可生成多张候选图、多段候选视频，并支持人工选择
- 评分开关：创建任务时可分别控制“图片评分”“视频评分”，关闭后默认不自动选材，等待人工确认
- 自动确认开关：开启后，当前节点满足自动推进条件时会直接进入下一步；关闭后始终停在待审核态
- 本地 FFmpeg 合成：最终视频先落盘到 `./data/final-videos`，随后上传到 S3 兼容对象存储
- S3 兼容对象存储：本地图片/视频上传至 RustFS 等 S3 兼容服务，返回可访问 URL 供 LLM 与后续流程使用；`uploads/` 前缀对象默认 7 天过期，`final-videos/` 前缀默认不过期
- 前端工作台：创建任务、切换两类工作流、查看历史任务、编辑阶段产物、人工选择素材、从指定阶段重新生成

## Prompt 对齐

项目中的提示词已统一迁移为 Markdown 资源，位于 `src/main/resources/prompts`：

- `market-agent/prompt.md`
- `director-agent/prompt.md`
- `video-storyboard-agent/prompt.md`
- `evaluate-agent/prompt.md`
- `release-agent/prompt.md`

`PromptService` 会按 `## PROMPT_XXX` 段落读取对应提示词正文。这样既保留了提示词的结构化组织，也避免把提示词硬编码散落在 Java 代码里。

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

# S3 兼容对象存储（RustFS / AWS S3 / 其他 S3 兼容服务）
export S3_ENDPOINT=http://127.0.0.1:9000
export S3_PUBLIC_BASE_URL=http://127.0.0.1:9000
export S3_ACCESS_KEY=你的AccessKey
export S3_SECRET_KEY=你的SecretKey
export S3_BUCKET=ad-video-gen-java
export S3_REGION=us-east-1
export S3_PATH_STYLE_ACCESS=true
export S3_AUTO_CREATE_BUCKET=true
export S3_OBJECT_EXPIRATION_DAYS=7
```

S3 兼容对象存储相关环境变量说明：

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `S3_ENDPOINT` | 否 | `http://127.0.0.1:9000` | S3 API 服务地址，RustFS 可填写 RustFS API endpoint |
| `S3_PUBLIC_BASE_URL` | 否 | 空 | 对外可访问的文件 URL 前缀；为空时使用 `S3_ENDPOINT` |
| `S3_ACCESS_KEY` | 是 | 空 | 访问密钥 |
| `S3_SECRET_KEY` | 是 | 空 | 秘密密钥 |
| `S3_BUCKET` | 否 | `ad-video-gen-java` | 存储桶名称 |
| `S3_REGION` | 否 | `us-east-1` | S3 region；RustFS 本地部署通常保持默认即可 |
| `S3_PATH_STYLE_ACCESS` | 否 | `true` | 是否使用 path-style URL，RustFS 等自托管服务建议开启 |
| `S3_AUTO_CREATE_BUCKET` | 否 | `true` | 启动时自动创建 bucket 并配置生命周期 |
| `S3_OBJECT_EXPIRATION_DAYS` | 否 | `7` | `uploads/` 前缀对象过期天数；最终视频使用 `final-videos/` 前缀，默认不过期 |

未配置 `S3_ACCESS_KEY` / `S3_SECRET_KEY` 时，上传接口会返回 mock URL，便于本地先跑通流程；正式使用图片/视频上传能力前需配置 RustFS 或其他 S3 兼容对象存储。

未配置 `ARK_API_KEY` 或未开启 `IMAGE_GENERATION_ENABLED` / `VIDEO_GENERATION_ENABLED` 时，服务会使用本地兜底内容，方便先跑通整体流程。

最终合成阶段会调用本机 FFmpeg。请确保 `ffmpeg` 在 `PATH` 中，或通过 `FFMPEG_BINARY` 指定可执行文件路径。配置 S3 兼容对象存储后，最终视频会上传到 `final-videos/` 前缀，发布信息中的短链字段直接使用对象存储 URL。

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

### 创建任务页

- 左侧展示当前项目的核心流程设计图和理念说明
- 中间输入区顶部可切换两类工作流：`商品图生成` / `视频解析生成`
- 右侧为生成配置，包括目标平台、时长、比例、风格、评分开关和自动确认开关
- 商品图流程支持本地图片上传或图片链接输入；本地上传会先写入 S3 兼容对象存储并返回 URL
- 视频拆解流程支持本地视频上传或视频 URL 输入；本地上传会先写入 S3 兼容对象存储并返回 URL

### 审核与重燃

- 每个阶段的产物都可以在工作台查看
- 营销策划、分镜脚本支持在线编辑后直接生效
- 图片候选、视频候选支持人工选择，也支持在启用评分时由模型先给出评分建议
- “重燃”支持从指定阶段重新执行，并修改该阶段需要的输入参数
- 历史任务可以再次打开查看，切换到已完成的旧节点查看之前的中间结果

## Docker 部署

运行层使用精简的 Eclipse Temurin 17 UBI minimal 镜像。FFmpeg 不在镜像构建
期间联网安装，而是从项目本地的 Linux x64 安装包复制并解压。先准备安装包：

```bash
mkdir -p docker/ffmpeg
cp /本地路径/ffmpeg-master-latest-linux64-gpl.tar.xz docker/ffmpeg/
```

项目当前使用的安装包架构为 Linux x86_64；压缩包已排除 Git 管理。容器默认
监听 `48080`，H2 数据库与最终视频分别持久化到两个宿主机目录：

```bash
mvn clean package
docker build -t ad-video-gen-java .

docker run -d \
  --name ad-video-gen-java \
  -p 48080:48080 \
  -e ARK_API_KEY=你的方舟APIKey \
  -e CGT_ENDPOINT_ID=你的Seedream接入点ID \
  -e T2V_ENDPOINT_ID=你的Seedance接入点ID \
  -e LLM_ENDPOINT_ID=你的LLM接入点ID \
  -e PUBLIC_BASE_URL=http://你的服务暴露的ip或域名:48080 \
  -e S3_ENDPOINT=http://host.docker.internal:9000 \
  -e S3_PUBLIC_BASE_URL=http://你的对象存储暴露地址:9000 \
  -e S3_ACCESS_KEY=你的AccessKey \
  -e S3_SECRET_KEY=你的SecretKey \
  -e S3_BUCKET=ad-video-gen-java \
  -e S3_REGION=us-east-1 \
  -e S3_PATH_STYLE_ACCESS=true \
  -e S3_AUTO_CREATE_BUCKET=true \
  -e S3_OBJECT_EXPIRATION_DAYS=7 \
  -v "$(pwd)/docker-data/db:/app/data/db" \
  -v "$(pwd)/docker-data/videos:/app/data/videos" \
  ad-video-gen-java
```

容器访问宿主机 RustFS 时，`S3_ENDPOINT` 常用 `http://host.docker.internal:9000`（Linux 需 Docker 20.10+ 并加 `--add-host=host.docker.internal:host-gateway`）。RustFS 与业务容器同网部署时，可改为 `http://rustfs:9000` 等服务名。

确保 bucket 对 `S3_PUBLIC_BASE_URL` 或 `S3_ENDPOINT` 对应地址可读，否则 LLM 与前端无法访问上传后的图片/视频 URL。

访问地址：

```text
前端工作台：http://localhost:48080/
H2 Console：http://localhost:48080/h2-console
最终视频：使用接口返回的对象存储 URL，例如 http://对象存储地址:9000/ad-video-gen-java/final-videos/{日期}/{文件名}.mp4
```

容器内 H2 JDBC URL 为：

```text
jdbc:h2:file:/app/data/db/ad-video-gen;MODE=MySQL
```

工作台按设计文档中的状态机推进。商品图广告流程与视频拆解广告流程共用同一套主状态机，但前置理解节点的语义不同：

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

用户可以在前端查看历史任务，并继续编辑历史任务。默认情况下，流程不会自动执行到下一步：每次点击“开始生成 / 进入下一步”只推进一个阶段，阶段产物生成后进入 `WAITING_REVIEW`，用户确认或编辑后再手动进入下一步；若创建任务时开启“自动确认”，满足条件的节点会自动推进。分镜脚本、图片评分、视频评分都支持编辑保存；保存后会清空受影响的后续产物，避免后续素材和用户编辑不一致。

## API

### 文档版任务接口

创建任务：

```bash
curl -X POST http://localhost:8080/api/video-tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowType": "product_image_ad",
    "inputType": "product_image",
    "text": "参考商品图片，生成一条 15 秒带货广告视频。商品：玻璃水。卖点：去虫胶、无甲醇、去油膜。",
    "imageUrls": [
      "https://example.com/product.jpg"
    ],
    "videoType": "商品展示视频",
    "platform": "抖音",
    "duration": 15,
    "aspectRatio": "9:16",
    "style": "真实生活方式、明亮、轻快",
    "imageScoringEnabled": true,
    "videoScoringEnabled": false,
    "autoConfirmEnabled": false,
    "generateImageCount": 2,
    "generateVideoCount": 1
  }'
```

视频拆解广告流程可先上传视频，再创建任务：

```bash
curl -X POST http://localhost:8080/api/video-tasks/upload-video \
  -F 'file=@/absolute/path/to/source.mp4'

# 响应示例：
# { "code": 0, "data": { "fileName": "source.mp4", "fileUrl": "http://127.0.0.1:9000/ad-video-gen-java/uploads/videos/..." } }
```

商品图流程上传图片：

```bash
curl -X POST http://localhost:8080/api/video-tasks/upload-image \
  -F 'file=@/absolute/path/to/product.jpg'
```

创建视频拆解任务（使用上传返回的 `fileUrl`）：

```bash
curl -X POST http://localhost:8080/api/video-tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "workflowType": "video_storyboard_ad",
    "inputType": "source_video",
    "text": "请基于上传的视频素材总结分镜，并重制为一条适合小红书的广告视频。",
    "sourceVideoUrl": "http://127.0.0.1:9000/ad-video-gen-java/uploads/videos/2026/06/22/xxx-source.mp4",
    "sourceVideoFileName": "source.mp4",
    "videoType": "视频素材重制广告",
    "platform": "小红书",
    "duration": 15,
    "aspectRatio": "9:16",
    "style": "电影感",
    "imageScoringEnabled": true,
    "videoScoringEnabled": true,
    "autoConfirmEnabled": false,
    "generateImageCount": 4,
    "generateVideoCount": 2
  }'
```

`CreateVideoTaskRequest` 关键字段说明：

- `workflowType`
  - `product_image_ad`：商品图生成广告
  - `video_storyboard_ad`：视频解析生成广告
- `inputType`
  - `product_image`：商品图流程
  - `source_video`：视频拆解流程
- `imageScoringEnabled`
  - `true`：自动对候选图片评分
  - `false`：不评分，等待人工选择
- `videoScoringEnabled`
  - `true`：自动对候选视频评分
  - `false`：不评分，等待人工选择
- `autoConfirmEnabled`
  - `true`：节点满足条件时自动推进
  - `false`：每一步都停在待审核态

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
└── service        # 任务、多媒体、S3 对象存储、短链服务
```
