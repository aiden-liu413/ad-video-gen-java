# 仓库协作指南

本文件是本仓库唯一的 Agent / 贡献者指南。开始任何改动前，先阅读 `README.md`、`git规范.md` 和本文件，并用 `git status --short` 确认当前工作区，避免覆盖用户未提交改动。

## 项目结构

- `src/main/java/com/volcengine/demo/advideo/`：Spring Boot 后端源码。
- `agent/`：Market、Director、VideoStoryboard、Release 等 Agent。
- `client/`：火山方舟 LLM、生图、视频客户端。
- `orchestrator/WorkflowOrchestratorServiceImpl.java`：任务状态机与主流程编排。
- `service/`：提示词、S3 对象存储、FFmpeg 合成等基础服务。
- `src/main/resources/prompts/`：各 Agent 提示词。
- `src/test/java/`：后端测试，主流程测试在 `VideoTaskWorkflowFunctionalTests`。
- `frontend/src/main.tsx`：React 页面、状态流转、API 调用与主要组件。
- `frontend/src/styles.css`：前端全局样式。

## 工作流认知

商品图流程：创建 → 营销策划 → 分镜脚本 → 图片生成与评估 → 视频生成与评估 → 最终合成 → 完成。

视频素材流程：创建 → 视频理解与分镜 → 图片生成与评估 → 视频生成与评估 → 最终合成 → 完成。

- 前端会将 `IMAGE_EVALUATING` / `IMAGE_SELECTING` 归并为图片生成与评估。
- 前端会将 `VIDEO_EVALUATING` / `VIDEO_SELECTING` 归并为视频生成与评估。
- `RUNNING` 时只给当前正在执行阶段加遮罩，回看已完成阶段不应遮罩。
- 分镜、图片组、视频组必须按 `shotId` 自然顺序兜底排序，避免并发完成顺序影响展示与提交。
- 重燃表单只展示所选阶段真实可改输入；图片/视频节点不展示历史候选素材，视频节点可选择候选图。

## 构建、测试与本地运行

- `mvn spring-boot:run`：启动后端。
- `cd frontend && npm run dev`：启动 Vite，默认 `http://localhost:8002/`。
- `cd frontend && npm run build`：前端类型检查与构建。
- `mvn test`：运行后端测试。
- `mvn -Dtest=VideoTaskWorkflowFunctionalTests test`：运行主流程功能测试。
- `mvn clean package`：完整打包，并把前端产物复制到 Spring Boot 静态资源。
- 提交前至少运行 `git diff --check`。

## 编码与注释规范

- Java 使用 Java 17 与现有 Spring Boot 分层风格。
- 前端沿用当前 React + TypeScript 单文件组织，非必要不引入新 UI 库、状态库或大规模拆分。
- 后端主流程改动优先集中在 `WorkflowOrchestratorServiceImpl` 附近，保持状态机推进和上下文清理逻辑清晰。
- S3 存储相关改动优先使用 `S3StorageService` 与配置类，不在业务层写死 RustFS、MinIO 等具体中间件。
- 新增方法必须添加中文规范注释，修改方法时同步更新注释：

```java
/**
 * 功能描述：说明方法完成的业务动作。
 * 参数解释：逐一说明关键参数含义。
 * 返回对象描述：说明返回对象、空值或集合含义。
 * 可能抛出的异常：说明业务异常或无。
 */
```

## 前端 UI 约定

- 保持现有工作台风格：低饱和青蓝、紧凑卡片、清晰层级、少装饰、优先效率。
- 图片/视频审核页面保持一致交互：左侧分镜导航固定可见，右侧分镜内容滚动，点击卡片选择素材。
- 最终合成页只展示成片预览、发布文案、话题和下载/复制操作，不混入上游审核内容。
- 用户明确要求“只改布局”时，只调整 JSX 结构和 CSS，不改业务逻辑。

## 测试要求

- 改动工作流、选片顺序、重燃、对象存储或 API 契约时，新增或更新测试。
- 主流程相关改动优先运行 `mvn -Dtest=VideoTaskWorkflowFunctionalTests test`。
- 仅前端布局改动且没有前端测试框架时，至少运行 `npm run build`，并在回复中说明未新增单元测试原因。

## 提交与 PR 规范

- 遵循 `git规范.md`：`<type>(<scope>): <subject>`。
- 示例：`fix(frontend): 修复视频审核布局`、`docs: 更新项目说明`。
- 单次提交只包含同一类改动；避免 `update`、`改代码`、`临时提交` 等模糊描述。
- 本项目代码变更完成后自动提交；提交前只暂存本次任务相关文件。
- PR 应包含变更摘要、验证命令、UI 截图、关联 issue 和配置/迁移影响说明。

## 安全与禁止事项

- 不提交本地数据、构建产物、缓存和密钥，例如 `docker-data/`、`docker/docker-data/`、`target/`、`frontend/dist/`、`.DS_Store`。
- 不执行 `git reset --hard`、`git checkout --` 等会覆盖用户改动的命令，除非用户明确要求。
- 不引入未验证的并发顺序依赖；并发任务完成后按预先确定的分镜顺序回写列表。
- 回答用户时以 `你好,老哥。` 开头。
