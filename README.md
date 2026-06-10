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
- 异步任务接口：提交任务后轮询查询结果

## Prompt 对齐

项目已将原 Python 实现中的 5 个 `prompt.py` 原样复制到 `src/main/resources/prompts`：

- `market-agent/prompt.py`
- `director-agent/prompt.py`
- `evaluate-agent/prompt.py`
- `release-agent/prompt.py`
- `multimedia-agent/prompt.py`

Java 代码通过 `PromptService` 解析这些文件里的 `PROMPT_XXX = """..."""` 常量，避免手工改写导致提示词与原项目不一致。

## 启动

```bash
mvn spring-boot:run
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
```

未配置 `ARK_API_KEY` 或未开启 `IMAGE_GENERATION_ENABLED` / `VIDEO_GENERATION_ENABLED` 时，服务会使用本地兜底内容，方便先跑通整体流程。

## API

提交生成任务：

方式一：商品链接生成

```bash
curl -X POST http://localhost:8080/api/ad-videos \
  -H 'Content-Type: application/json' \
  -d '{
    "prompt": "参考商品页面，生成一条 15 秒带货广告视频",
    "productName": "智能咖啡杯",
    "productDescription": "可自动控温并记录饮水习惯的随行杯",
    "productUrl": "https://example.com/products/cup",
    "targetAudience": "通勤白领、咖啡爱好者",
    "sellingPoints": "恒温、长续航、App记录",
    "style": "真实生活方式、明亮、轻快",
    "duration": "15s",
    "landingPageUrl": "https://example.com/products/cup",
    "referenceImageUrls": [
      "https://example.com/products/cup-main.png"
    ]
  }'
```

方式二：一段描述 + 一张商品图片生成

```bash
curl -X POST http://localhost:8080/api/ad-videos/upload \
  -F 'prompt=参考上传的商品图片，生成一条 15 秒带货广告视频' \
  -F 'productName=玻璃水' \
  -F 'productDescription=去虫胶无甲醇去油膜' \
  -F 'targetAudience=' \
  -F 'sellingPoints=去虫胶，无甲醇，去油膜' \
  -F 'style=' \
  -F 'duration=15s' \
  -F 'image=@/path/to/cup-main.png'
```

上传的图片会保存到 `./data/uploads` 便于本地查看，同时会转换成 `data:image/...;base64,...` 形式作为 `referenceImageUrls` 传给后续图片/视频生成步骤。这样 Seedance 图生视频不依赖公网可访问的图片 URL。

查询任务：

```bash
curl http://localhost:8080/api/ad-videos/{taskId}
```

从失败步骤重试任务：

```bash
curl -X POST http://localhost:8080/api/ad-videos/{taskId}/retry
```

任务会用 H2 持久化保存原始请求、状态、每一步 checkpoint、最终结果和错误信息。重试时已经完成的 `MarketAgent`、`DirectorAgent`、`EvaluateAgent`、`Seedream` 或 `ReleaseAgent` 结果会直接复用，只从第一个失败/缺失步骤继续，避免重复消耗模型 token。

## 持久化

- H2 数据文件：`./data/ad-video-gen.mv.db`
- H2 Console：`http://localhost:8080/h2-console`
- JDBC URL：`jdbc:h2:file:./data/ad-video-gen`
- 用户名：`sa`
- 密码：空
- 服务重启时，仍处于 `RUNNING` 的任务会自动标记为 `FAILED`，然后可以调用 retry 从已保存的 checkpoint 继续。

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
