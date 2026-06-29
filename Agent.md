# Agent 工作指南

本文档用于指导参与本项目的 AI Agent / 子 Agent 快速理解代码边界、开发约定和验证流程。开始任何改动前，先阅读 `README.md` 与 `git规范.md`，再结合当前任务选择最小修改范围。

## 项目概览

- 项目名称：`ad-video-gen-java`，前端展示为 **AIVision Control**。
- 目标：可控式 AI 营销视频工作台，支持商品图生成广告与视频素材拆解重制两类工作流。
- 后端：Spring Boot 3、Java 17、Spring Data JPA、H2、S3 兼容对象存储、FFmpeg、火山方舟模型客户端。
- 前端：React 18、Vite、TypeScript、Lucide React。主要代码集中在 `frontend/src/main.tsx` 与 `frontend/src/styles.css`。
- 对象存储：使用通用 S3 协议，目标中间件可为 RustFS、MinIO 或其他 S3 兼容服务；成片默认上传到 `final-videos/` 且不过期。

## 主要目录

- `src/main/java/com/volcengine/demo/advideo/agent/`：Market、Director、VideoStoryboard、Release 等 Agent 封装。
- `src/main/java/com/volcengine/demo/advideo/client/`：方舟 LLM、生图、视频客户端。
- `src/main/java/com/volcengine/demo/advideo/controller/`：HTTP API 入口与异常处理。
- `src/main/java/com/volcengine/demo/advideo/dto/`：前后端接口对象。
- `src/main/java/com/volcengine/demo/advideo/entity/`：JPA 实体。
- `src/main/java/com/volcengine/demo/advideo/orchestrator/`：任务状态机与主编排逻辑。
- `src/main/java/com/volcengine/demo/advideo/service/`：提示词、S3、FFmpeg 等基础服务。
- `src/main/resources/prompts/`：各 Agent 提示词。
- `frontend/src/main.tsx`：前端页面、状态流转、组件与 API 调用。
- `frontend/src/styles.css`：前端全局样式。
- `src/test/java/`：后端集成/功能测试。

## 工作流认知

商品图流程：

```text
创建 → 营销策划 → 分镜脚本 → 图片生成与评估 → 视频生成与评估 → 最终合成 → 完成
```

视频素材流程：

```text
创建 → 视频理解与分镜 → 图片生成与评估 → 视频生成与评估 → 最终合成 → 完成
```

后端阶段会在前端归并展示：

- `IMAGE_EVALUATING` / `IMAGE_SELECTING` → 图片生成与评估
- `VIDEO_EVALUATING` / `VIDEO_SELECTING` → 视频生成与评估

任务执行原则：

- `start` / `advance` 异步推进单步，前端轮询详情。
- `RUNNING` 时只给当前正在执行阶段加遮罩，回看已完成阶段不应遮罩。
- 回看已完成节点是只读态，当前阶段支持编辑、保存和推进。
- 重燃只展示所选阶段真实可改输入；图片/视频节点不展示历史候选素材，视频节点可选择候选图。
- 分镜、图片组、视频组必须按 `shotId` 自然顺序兜底排序，避免并发完成顺序影响展示与提交。

## 开发约定

- 修改前先检查 `git status --short`，不要覆盖未提交的用户改动。
- 优先沿用现有单文件前端结构，不轻易引入新状态库、UI 库或大规模拆分。
- 后端主流程改动优先放在 `WorkflowOrchestratorServiceImpl` 附近，保持状态机推进和上下文清理逻辑集中。
- S3 存储相关改动优先使用 `S3StorageService` 与配置类，不在业务层散落中间件细节。
- 提示词改动只修改对应 `src/main/resources/prompts/**/prompt.md` 文件，并确认 Agent 输出 DTO 仍兼容。
- 前端 UI 调整要保持现有工作台风格：低饱和青蓝、紧凑卡片、清晰层级、少装饰、优先效率。
- 图片/视频审核页面应保持一致交互：左侧分镜导航固定可见，右侧分镜内容滚动，点击卡片选择素材。
- 最终合成页只展示成片预览、发布文案、话题和下载/复制操作，不混入上游审核内容。

## 注释规范

新增方法必须添加中文规范注释，修改方法时同步更新注释。格式建议：

```java
/**
 * 功能描述：说明方法完成的业务动作。
 * 参数解释：逐一说明关键参数含义。
 * 返回对象描述：说明返回对象、空值或集合含义。
 * 可能抛出的异常：说明业务异常或无。
 */
```

TypeScript 中新增重要函数也按同样四段式中文注释书写。简单 JSX 片段或样式类无需为了形式添加噪音注释。

## 验证命令

前端改动：

```bash
cd frontend
npm run build
```

后端单测/集成测试：

```bash
mvn test
```

主工作流相关改动建议至少运行：

```bash
mvn -Dtest=VideoTaskWorkflowFunctionalTests test
```

完整打包：

```bash
mvn clean package
```

提交前通用检查：

```bash
git diff --check
git status --short
```

如果只改前端布局且没有测试框架覆盖，至少执行 `npm run build` 并在回复中说明未新增单元测试的原因。

## Git 提交规范

遵循 `git规范.md`：

```text
<type>(<scope>): <subject>
```

常用类型：

- `feat`：新增功能
- `fix`：修复问题
- `docs`：文档修改
- `style`：格式调整，无业务逻辑
- `refactor`：重构
- `test`：测试
- `chore`：工程维护
- `revert`：回滚

约束：

- subject 控制在 50 字以内，中文可用。
- 冒号后必须有空格。
- 单次提交只包含同一类改动。
- 不使用 `update`、`改代码`、`临时提交` 等模糊描述。
- 本项目后续代码变更完成后应自动提交；提交前只暂存本次任务相关文件，保留无关未跟踪或用户改动。

## 常见任务处理建议

- 前端布局问题：先定位组件与 CSS 选择器，确认是否影响全局，再使用更窄的 class 约束。
- 遮罩/回看问题：优先检查 `WorkflowView`、`StageContent`、`readOnly`、`viewStage` 与 `task.stage` 的关系。
- 顺序问题：后端优先保证集合顺序；前端保留 `sortByShotId` 兜底。
- 重燃问题：检查 `RegenerateControls`、`regenerateDraftFromTask`、`regeneratePayload` 与后端 `regenerate` 处理是否一致。
- 选片问题：检查 `selectedImages` / `selectedVideos` 是否按分镜映射提交，并确认后端按原分镜列表回填。
- 成片问题：检查 `FfmpegComposeService`、`S3StorageService`、`ReleaseAgent` 和 `finalVideo` 响应字段。

## 禁止事项

- 不要执行 `git reset --hard`、`git checkout --` 等会覆盖用户改动的命令，除非用户明确要求。
- 不要把对象存储实现写死为某个中间件；应保持 S3 兼容协议抽象。
- 不要在主流程里引入未验证的并发顺序依赖；并发任务完成后应按预先确定的分镜顺序回写列表。
- 不要提交构建产物、运行数据或本地缓存，如 `target/`、`frontend/dist/`、`docker/docker-data/`、`.DS_Store`。
- 不要为了 UI 修改重写业务逻辑；用户明确要求“只改布局”时，只调整 JSX 结构和 CSS。

## 注意

- 每次回答之前 增加"你好,老哥。"前缀