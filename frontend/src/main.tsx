import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Archive,
  BarChart3,
  BookOpen,
  Check,
  CheckCircle2,
  Clapperboard,
  Copy,
  Download,
  FileText,
  Image as ImageIcon,
  Loader2,
  Music2,
  Plus,
  Rocket,
  RotateCcw,
  Search,
  Sparkles,
  UploadCloud,
  Video,
  ChevronDown,
  X,
} from "lucide-react";
import "./styles.css";

/** 开发态直连后端，避免 Vite 未启动或代理异常时出现 ERR_CONNECTION_REFUSED */
const API_BASE = import.meta.env.VITE_API_BASE ?? (import.meta.env.DEV ? "http://127.0.0.1:8080" : "");

type TaskStage =
  | "CREATED"
  | "MARKET_PLANNING"
  | "SHOT_SCRIPT_GENERATING"
  | "IMAGE_GENERATING"
  | "IMAGE_EVALUATING"
  | "IMAGE_SELECTING"
  | "VIDEO_GENERATING"
  | "VIDEO_EVALUATING"
  | "VIDEO_SELECTING"
  | "FINAL_COMPOSING"
  | "COMPLETED"
  | "FAILED";

type ProductInfo = {
  name: string;
  sellingPoint: string;
  resources: string[];
};

type VideoConfig = {
  videoType: string;
  productInfo: ProductInfo;
  targetAudience: string;
  platform: string;
  duration: number;
  aspectRatio: string;
  videoAdvice: string;
};

type Shot = {
  shotId: string;
  orderNo: number;
  duration: number;
  prompt: string;
  action: string;
  words: string;
  reference: string;
  camera: string;
  sceneType: string;
};

type ImageCandidate = {
  assetId: string;
  shotId: string;
  id: number;
  url: string;
  score?: number;
  reason?: string;
  selected?: boolean;
};

type VideoCandidate = {
  assetId: string;
  shotId: string;
  id: number;
  url: string;
  score?: number;
  reason?: string;
  selected?: boolean;
};

type ShotImageGroup = {
  shotId: string;
  duration?: number;
  prompt: string;
  action: string;
  words: string;
  reference: string;
  images: ImageCandidate[];
};

type ShotVideoGroup = {
  shotId: string;
  duration?: number;
  prompt: string;
  action: string;
  words: string;
  reference: string;
  videos: VideoCandidate[];
};

type SelectedImage = {
  shotId: string;
  duration?: number;
  image: ImageCandidate;
  prompt: string;
  action: string;
  words: string;
};

type SelectedVideo = {
  shotId: string;
  duration?: number;
  video: VideoCandidate;
  words: string;
  action: string;
};

type FinalVideo = {
  videoUrl: string;
  videoTitle: string;
  videoRelease: string;
  hashtags: string[];
  selectedVideos: SelectedVideo[];
};

type WorkflowType = "product_image_ad" | "video_storyboard_ad";

type TaskRequest = {
  workflowType: WorkflowType;
  inputType: "product_image" | "source_video";
  text?: string;
  imageUrls?: string[];
  imageFileIds?: string[];
  sourceVideoUrl?: string;
  sourceVideoFileId?: string;
  sourceVideoFileName?: string;
  videoType?: string;
  platform?: string;
  duration?: number;
  aspectRatio?: string;
  style?: string;
  imageScoringEnabled?: boolean;
  videoScoringEnabled?: boolean;
  autoConfirmEnabled?: boolean;
  generateImageCount?: number;
  generateVideoCount?: number;
};

type RegenerateDraft = {
  taskInput: TaskRequest;
  videoConfig: VideoConfig;
  shots: Shot[];
  imageGroups: ShotImageGroup[];
  videoGroups: ShotVideoGroup[];
  selectedImages: Record<string, string>;
  selectedVideos: Record<string, string>;
};

type TaskDetail = {
  taskId: string;
  status: string;
  stage: TaskStage;
  progress: number;
  workflowType: WorkflowType;
  request?: TaskRequest;
  videoConfig?: VideoConfig;
  shots: Shot[];
  imageGroups: ShotImageGroup[];
  scoredImageGroups: ShotImageGroup[];
  selectedImages: SelectedImage[];
  videoGroups: ShotVideoGroup[];
  scoredVideoGroups: ShotVideoGroup[];
  selectedVideos: SelectedVideo[];
  finalVideo?: FinalVideo;
  errorCode?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
};

type TaskSummary = {
  taskId: string;
  status: string;
  currentStep: TaskStage;
  workflowType: WorkflowType;
  productName: string;
  updatedAt: string;
};

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

type FormState = {
  workflowType: WorkflowType;
  inputType: "product_image" | "source_video";
  text: string;
  imageUrls: string;
  imageFileId: string;
  imageFileName: string;
  sourceVideoUrl: string;
  sourceVideoFileId: string;
  sourceVideoFileName: string;
  videoType: string;
  platform: string;
  duration: string;
  aspectRatio: string;
  style: string;
  imageScoringEnabled: boolean;
  videoScoringEnabled: boolean;
  autoConfirmEnabled: boolean;
  generateImageCount: string;
  generateVideoCount: string;
};

type WorkflowViewProps = {
  task: TaskDetail;
  busy: boolean;
  message: string;
  selectedImages: Record<string, string>;
  selectedVideos: Record<string, string>;
  editableShots: Shot[];
  editableImageGroups: ShotImageGroup[];
  regenerateStage: TaskStage;
  regenerateReason: string;
  regenerateDraft: RegenerateDraft;
  setSelectedImages: (value: Record<string, string>) => void;
  setSelectedVideos: (value: Record<string, string>) => void;
  setEditableShots: (value: Shot[]) => void;
  setEditableImageGroups: (value: ShotImageGroup[]) => void;
  setRegenerateStage: (value: TaskStage) => void;
  setRegenerateReason: (value: string) => void;
  setRegenerateDraft: (value: RegenerateDraft) => void;
  onAdvance: () => void;
  onRequestAdvance: () => void;
  onSaveEdits: () => void;
  onSaveStage: () => void;
  onSaveSelections: () => void;
  onRegenerate: () => Promise<boolean>;
  isDirty: boolean;
  stageTodoSummary: string;
  stageSaveLabel: string;
};

type PersistedSnapshot = {
  taskUpdatedAt: string;
  shotsJson: string;
  selectedImagesJson: string;
  selectedVideosJson: string;
  imageGroupsJson: string;
};

type StageViewProps = WorkflowViewProps & {
  viewStage: TaskStage;
  readOnly: boolean;
  onOpenRegenerate?: (stage: TaskStage) => void;
};

const initialForm: FormState = {
  workflowType: "product_image_ad",
  inputType: "product_image",
  text: "参考上传的商品图片，生成一条 15 秒带货广告视频。商品：卖点：",
  imageUrls: "",
  imageFileId: "",
  imageFileName: "",
  sourceVideoUrl: "",
  sourceVideoFileId: "",
  sourceVideoFileName: "",
  videoType: "商品展示视频",
  platform: "douyin",
  duration: "15",
  aspectRatio: "9:16",
  style: "电影感",
  imageScoringEnabled: false,
  videoScoringEnabled: false,
  autoConfirmEnabled: false,
  generateImageCount: "4",
  generateVideoCount: "2"
};

const aspectRatioOptions = [
  { value: "adaptive", label: "自适应" },
  { value: "21:9", label: "21:9" },
  { value: "16:9", label: "16:9" },
  { value: "4:3", label: "4:3" },
  { value: "1:1", label: "1:1" },
  { value: "3:4", label: "3:4" },
  { value: "9:16", label: "9:16" },
  { value: "9:21", label: "9:21" }
];

const durationOptions = ["5", "10", "15"];

const platformOptions = [
  { value: "douyin", label: "抖音", icon: <Music2 size={18} /> },
  { value: "xiaohongshu", label: "小红书", icon: <BookOpen size={18} /> },
  { value: "bilibili", label: "哔哩哔哩", icon: <Clapperboard size={18} /> }
] as const;

const emptyVideoConfig: VideoConfig = {
  videoType: "商品展示视频",
  productInfo: { name: "", sellingPoint: "", resources: [] },
  targetAudience: "",
  platform: "douyin",
  duration: 15,
  aspectRatio: "9:16",
  videoAdvice: ""
};

const emptyRegenerateDraft: RegenerateDraft = {
  taskInput: {
    workflowType: "product_image_ad",
    inputType: "product_image",
    text: "",
    imageUrls: [],
    imageFileIds: [],
    sourceVideoUrl: "",
    sourceVideoFileId: "",
    sourceVideoFileName: "",
    videoType: "商品展示视频",
    platform: "douyin",
    duration: 15,
    aspectRatio: "9:16",
    style: "",
    imageScoringEnabled: false,
    videoScoringEnabled: false,
    autoConfirmEnabled: false,
    generateImageCount: 4,
    generateVideoCount: 2
  },
  videoConfig: emptyVideoConfig,
  shots: [],
  imageGroups: [],
  videoGroups: [],
  selectedImages: {},
  selectedVideos: {}
};

const stages: Array<{ stage: TaskStage; label: string; icon: React.ReactNode }> = [
  { stage: "CREATED", label: "创建", icon: <Check size={16} /> },
  { stage: "MARKET_PLANNING", label: "营销策划", icon: <Sparkles size={16} /> },
  { stage: "SHOT_SCRIPT_GENERATING", label: "分镜脚本", icon: <FileText size={16} /> },
  { stage: "IMAGE_GENERATING", label: "图片生成与评估", icon: <ImageIcon size={16} /> },
  { stage: "VIDEO_GENERATING", label: "视频生成与评估", icon: <Video size={16} /> },
  { stage: "FINAL_COMPOSING", label: "最终合成", icon: <Archive size={16} /> },
  { stage: "COMPLETED", label: "完成", icon: <CheckCircle2 size={16} /> }
];

const workflowOptions: Array<{ value: WorkflowType; label: string; shortLabel: string; description: string }> = [
  { value: "product_image_ad", label: "商品图生成广告", shortLabel: "商品图", description: "从商品图片和文字需求出发，先做营销策划再生成广告。" },
  { value: "video_storyboard_ad", label: "视频素材拆解生成广告", shortLabel: "视频素材", description: "从视频素材总结分镜，再生成图片候选、分镜视频和最终广告。" }
];

function App() {
  const [form, setForm] = useState<FormState>(initialForm);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [videoFile, setVideoFile] = useState<File | null>(null);
  const [taskId, setTaskId] = useState("");
  const [task, setTask] = useState<TaskDetail | null>(null);
  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [regenerateStage, setRegenerateStage] = useState<TaskStage>("SHOT_SCRIPT_GENERATING");
  const [regenerateReason, setRegenerateReason] = useState("");
  const [selectedImages, setSelectedImages] = useState<Record<string, string>>({});
  const [selectedVideos, setSelectedVideos] = useState<Record<string, string>>({});
  const [editableShots, setEditableShots] = useState<Shot[]>([]);
  const [editableImageGroups, setEditableImageGroups] = useState<ShotImageGroup[]>([]);
  const [regenerateDraft, setRegenerateDraft] = useState<RegenerateDraft>(emptyRegenerateDraft);
  const [confirmAdvanceOpen, setConfirmAdvanceOpen] = useState<{ issues: string[] } | null>(null);
  const savedSnapshotRef = useRef<PersistedSnapshot | null>(null);

  useEffect(() => {
    void loadTasks();
  }, []);

  useEffect(() => {
    if (!taskId) return;
    void loadTask(taskId);
  }, [taskId]);

  useEffect(() => {
    if (!taskId || task?.status !== "RUNNING") return;
    const timer = window.setInterval(() => void loadTask(taskId), 2500);
    return () => window.clearInterval(timer);
  }, [taskId, task?.status]);

  useEffect(() => {
    if (!task) return;
    setRegenerateDraft(regenerateDraftFromTask(task));
  }, [task?.taskId, task?.updatedAt, regenerateStage]);

  async function loadTasks() {
    const response = await fetch(`${API_BASE}/api/video-tasks`);
    if (!response.ok) return;
    const body = (await response.json()) as ApiResponse<TaskSummary[]>;
    if (body.code === 0) setTasks(body.data ?? []);
  }

  async function loadTask(id: string) {
    const detail = await fetchTaskDetail(id);
    if (detail) applyTaskDetail(detail);
  }

  async function fetchTaskDetail(id: string) {
    const response = await fetch(`${API_BASE}/api/video-tasks/${id}`);
    if (!response.ok) return null;
    const body = (await response.json()) as ApiResponse<TaskDetail>;
    return body.code === 0 ? body.data : null;
  }

  function applyTaskDetail(detail: TaskDetail) {
    const selectedImageMap = Object.fromEntries((detail.selectedImages ?? []).map((item) => [item.shotId, item.image.assetId]));
    const selectedVideoMap = Object.fromEntries((detail.selectedVideos ?? []).map((item) => [item.shotId, item.video.assetId]));
    const nextSelectedImages = Object.keys(selectedImageMap).length > 0
      ? selectedImageMap
      : fallbackSelectedImages(detail.imageGroups ?? [], detail.scoredImageGroups ?? []);
    const nextSelectedVideos = Object.keys(selectedVideoMap).length > 0
      ? selectedVideoMap
      : fallbackSelectedVideos(detail.videoGroups ?? [], detail.scoredVideoGroups ?? []);
    setTask(detail);
    setSelectedImages(nextSelectedImages);
    setSelectedVideos(nextSelectedVideos);
    const nextShots = detail.shots ?? [];
    const nextImageGroups = (detail.scoredImageGroups?.length ? detail.scoredImageGroups : detail.imageGroups) ?? [];
    setEditableShots(nextShots);
    setEditableImageGroups(nextImageGroups);
    savedSnapshotRef.current = buildPersistedSnapshot(detail.updatedAt, nextShots, nextSelectedImages, nextSelectedVideos, nextImageGroups);
  }

  function discardLocalChanges() {
    if (!task) return;
    applyTaskDetail(task);
  }

  function dirtyState() {
    if (!task || !savedSnapshotRef.current) return { dirty: false, issues: [] as string[] };
    return compareDirtyState(
      savedSnapshotRef.current,
      task.updatedAt,
      editableShots,
      selectedImages,
      selectedVideos,
      editableImageGroups
    );
  }

  async function saveStageChanges() {
    if (!task) return;
    const stage = canonicalStage(task.stage);
    const { dirty } = dirtyState();
    if (stage === "SHOT_SCRIPT_GENERATING") {
      if (dirty) await saveCurrentEdits();
      return;
    }
    if (stage === "IMAGE_GENERATING") {
      if (dirty && JSON.stringify(editableShots) !== savedSnapshotRef.current?.shotsJson) {
        await saveCurrentEdits();
      }
      await saveSelections();
      return;
    }
    if (stage === "VIDEO_GENERATING") {
      if (task.stage === "VIDEO_EVALUATING" || task.stage === "VIDEO_SELECTING") {
        await saveSelections();
        return;
      }
      await saveCurrentEdits();
      await saveSelections();
      return;
    }
    if (hasScoredVideos(task) && isVideoReviewStage(task.stage)) {
      await saveSelections();
    }
  }

  async function requestAdvance() {
    if (!task) return;
    const missing = getMissingSelectionCountForTask(task, selectedImages, selectedVideos);
    if (missing > 0) {
      setMessage(`还有 ${missing} 组分镜未选择素材`);
      return;
    }
    const { dirty, issues } = dirtyState();
    if (dirty) {
      setConfirmAdvanceOpen({ issues });
      return;
    }
    await advance();
  }

  async function saveAndAdvance() {
    setConfirmAdvanceOpen(null);
    await saveStageChanges();
    await advance();
  }

  async function discardAndAdvance() {
    setConfirmAdvanceOpen(null);
    discardLocalChanges();
    await advance();
  }

  async function createTask(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      const imageSources = form.workflowType === "video_storyboard_ad" ? { imageUrls: [], imageFileIds: [] } : await prepareImageSources();
      const videoSource = await prepareVideoSource();
      if (form.workflowType === "product_image_ad" && imageSources.imageUrls.length === 0) {
        throw new Error("请上传本地产品图片，或输入至少一个图片链接");
      }
      if (form.workflowType === "video_storyboard_ad" && !videoSource.sourceVideoUrl) {
        throw new Error("请上传本地视频文件，或输入视频链接");
      }
      const response = await fetch(`${API_BASE}/api/video-tasks`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          workflowType: form.workflowType,
          inputType: form.workflowType === "video_storyboard_ad" ? "source_video" : "product_image",
          text: form.text,
          imageUrls: imageSources.imageUrls,
          sourceVideoUrl: videoSource.sourceVideoUrl,
          sourceVideoFileName: videoSource.sourceVideoFileName,
          videoType: form.videoType,
          platform: form.platform,
          duration: Number(form.duration || 15),
          aspectRatio: form.aspectRatio,
          style: form.style,
          imageScoringEnabled: form.imageScoringEnabled,
          videoScoringEnabled: form.videoScoringEnabled,
          autoConfirmEnabled: form.autoConfirmEnabled,
          generateImageCount: Number(form.generateImageCount || 4),
          generateVideoCount: Number(form.generateVideoCount || 2)
        })
      });
      const body = (await response.json()) as ApiResponse<{ taskId: string }>;
      if (!response.ok || body.code !== 0) throw new Error(body.message || response.statusText);
      setTaskId(body.data.taskId);
      setMessage("任务已创建");
      await loadTasks();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "创建失败");
    } finally {
      setBusy(false);
    }
  }

  async function prepareImageSources() {
    const manual = await Promise.all(
      form.imageUrls.split("\n").map((item) => item.trim()).filter(Boolean).map(normalizeImageUrl)
    );
    if (!imageFile) {
      return {
        imageUrls: manual,
        imageFileIds: [] as string[]
      };
    }
    const formData = new FormData();
    formData.append("file", imageFile);
    const response = await fetch(`${API_BASE}/api/video-tasks/upload-image`, {
      method: "POST",
      body: formData
    });
    const body = (await response.json()) as ApiResponse<{ fileName: string; fileUrl: string }>;
    if (!response.ok || body.code !== 0) {
      throw new Error(body.message || response.statusText);
    }
    setForm((previous) => ({
      ...previous,
      imageFileName: body.data.fileName
    }));
    return {
      imageUrls: [...manual, body.data.fileUrl],
      imageFileIds: [] as string[]
    };
  }

  async function prepareVideoSource() {
    const manualUrl = form.sourceVideoUrl.trim();
    if (!videoFile) {
      return {
        sourceVideoUrl: manualUrl,
        sourceVideoFileName: form.sourceVideoFileName
      };
    }
    const formData = new FormData();
    formData.append("file", videoFile);
    const response = await fetch(`${API_BASE}/api/video-tasks/upload-video`, {
      method: "POST",
      body: formData
    });
    const body = (await response.json()) as ApiResponse<{ fileName: string; fileUrl: string }>;
    if (!response.ok || body.code !== 0) {
      throw new Error(body.message || response.statusText);
    }
    setForm((previous) => ({
      ...previous,
      sourceVideoUrl: body.data.fileUrl,
      sourceVideoFileName: body.data.fileName
    }));
    return {
      sourceVideoUrl: body.data.fileUrl,
      sourceVideoFileName: body.data.fileName
    };
  }

  async function postAction(path: string, successMessage: string, body?: unknown, options?: { waitForChange?: boolean }): Promise<boolean> {
    if (!taskId) return false;
    const previousTask = task;
    setBusy(true);
    setMessage("");
    try {
      const response = await fetch(`${API_BASE}${path}`, {
        method: "POST",
        headers: body ? { "Content-Type": "application/json" } : undefined,
        body: body ? JSON.stringify(body) : undefined
      });
      const rawText = await response.text();
      let payload: ApiResponse<unknown> = { code: -1, message: response.statusText, data: null };
      if (rawText) {
        try {
          payload = JSON.parse(rawText) as ApiResponse<unknown>;
        } catch {
          throw new Error(rawText.slice(0, 200) || response.statusText || "服务器返回异常");
        }
      }
      if (!response.ok || payload.code !== 0) throw new Error(payload.message || response.statusText);
      setMessage(successMessage);
      if (options?.waitForChange && previousTask) {
        await waitForTaskChange(previousTask);
      } else {
        await loadTask(taskId);
      }
      await loadTasks();
      return true;
    } catch (error) {
      const messageText = error instanceof Error ? error.message : "操作失败";
      if (messageText === "Failed to fetch" || messageText.includes("NetworkError")) {
        setMessage("无法连接后端服务，请确认 Spring Boot (8080) 已启动；页面需通过 npm run dev (8002) 或 http://localhost:8080 访问");
      } else {
        setMessage(messageText);
      }
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function advance() {
    if (!task) return;
    const path = task.stage === "CREATED" ? "start" : "advance";
    await postAction(`/api/video-tasks/${task.taskId}/${path}`, "已进入下一步", undefined, { waitForChange: true });
  }

  async function saveCurrentEdits() {
    if (!task) return;
    const payload: Partial<Pick<TaskDetail, "shots">> & { selectedImages?: SelectedImage[] } = {};
    if (task.stage === "SHOT_SCRIPT_GENERATING" || canonicalStage(task.stage) === "IMAGE_GENERATING") {
      payload.shots = editableShots;
    }
    if (canonicalStage(task.stage) === "VIDEO_GENERATING") {
      payload.selectedImages = buildSelectedImagesPayload(task, editableShots, editableImageGroups, selectedImages);
    }
    if (Object.keys(payload).length === 0) return;
    await postAction(`/api/video-tasks/${task.taskId}/context`, "更改已应用", payload);
  }

  async function saveSelections() {
    if (!task) return;
    await postAction(`/api/video-tasks/${task.taskId}/select-assets`, "选择已保存", {
      selectedImages: Object.entries(selectedImages).map(([shotId, assetId]) => ({ shotId, assetId })),
      selectedVideos: Object.entries(selectedVideos).map(([shotId, assetId]) => ({ shotId, assetId }))
    });
  }

  function activeRegenerateDraft(): RegenerateDraft {
    return {
      ...regenerateDraft,
      shots: editableShots,
      imageGroups: editableImageGroups.length > 0 ? editableImageGroups : regenerateDraft.imageGroups,
      selectedImages
    };
  }

  async function regenerate(): Promise<boolean> {
    if (!task) return false;
    const draft = activeRegenerateDraft();
    const payload = regeneratePayload(regenerateStage, draft, task.workflowType ?? "product_image_ad");
    if ("shots" in payload && Array.isArray(payload.shots)) {
      payload.shots = shotsForRegenerateApi(payload.shots, regenerateDraft.shots);
    }
    return postAction(`/api/video-tasks/${task.taskId}/regenerate`, "已重新生成", {
      fromStage: regenerateStage,
      reason: regenerateReason,
      shotIds: [],
      ...payload
    }, { waitForChange: true });
  }

  async function waitForTaskChange(previousTask: TaskDetail) {
    for (let attempt = 0; attempt < 18; attempt += 1) {
      await delay(700);
      const next = await fetchTaskDetail(previousTask.taskId);
      if (!next) continue;
      applyTaskDetail(next);
      if (
        next.stage !== previousTask.stage
        || next.status !== previousTask.status
        || next.updatedAt !== previousTask.updatedAt
      ) {
        return;
      }
    }
    await loadTask(previousTask.taskId);
  }

  function resetToCreate() {
    setTask(null);
    setTaskId("");
    setMessage("");
    setImageFile(null);
    setVideoFile(null);
    setForm(initialForm);
  }

  return (
    <div className="app-shell">
      <Topbar onNewTask={resetToCreate} />
      <Sidebar tasks={tasks} activeTaskId={taskId} onSelectTask={setTaskId} />
      <main className="workspace">
        {!task && (
        <CreateTaskView
          form={form}
          setForm={setForm}
          imageFile={imageFile}
          setImageFile={setImageFile}
          videoFile={videoFile}
          setVideoFile={setVideoFile}
          busy={busy}
          message={message}
          onSubmit={createTask}
        />
        )}
        {task && (
          <WorkflowView
            task={task}
            busy={busy}
            message={message}
            selectedImages={selectedImages}
            selectedVideos={selectedVideos}
            editableShots={editableShots}
            editableImageGroups={editableImageGroups}
            regenerateStage={regenerateStage}
            regenerateReason={regenerateReason}
            regenerateDraft={regenerateDraft}
            setSelectedImages={setSelectedImages}
            setSelectedVideos={setSelectedVideos}
            setEditableShots={setEditableShots}
            setEditableImageGroups={setEditableImageGroups}
            setRegenerateStage={setRegenerateStage}
            setRegenerateReason={setRegenerateReason}
            setRegenerateDraft={setRegenerateDraft}
            onAdvance={advance}
            onRequestAdvance={requestAdvance}
            onSaveEdits={saveCurrentEdits}
            onSaveStage={saveStageChanges}
            onSaveSelections={saveSelections}
            onRegenerate={regenerate}
            isDirty={dirtyState().dirty}
            stageTodoSummary={task ? getStageTodoSummary(task, selectedImages, selectedVideos) : ""}
            stageSaveLabel={task ? getStageSaveLabel(task) : ""}
          />
        )}
      </main>
      {confirmAdvanceOpen && (
        <ConfirmAdvanceDialog
          issues={confirmAdvanceOpen.issues}
          busy={busy}
          onSaveAndContinue={() => void saveAndAdvance()}
          onDiscardAndContinue={() => void discardAndAdvance()}
          onCancel={() => setConfirmAdvanceOpen(null)}
        />
      )}
    </div>
  );
}

function fallbackSelectedImages(imageGroups: ShotImageGroup[], scoredImageGroups: ShotImageGroup[]) {
  const groups = scoredImageGroups.length > 0 ? scoredImageGroups : imageGroups;
  return Object.fromEntries(groups.flatMap((group) => {
    const [first] = group.images ?? [];
    return first ? [[group.shotId, first.assetId]] : [];
  }));
}

function fallbackSelectedVideos(videoGroups: ShotVideoGroup[], scoredVideoGroups: ShotVideoGroup[]) {
  const groups = scoredVideoGroups.length > 0 ? scoredVideoGroups : videoGroups;
  return Object.fromEntries(groups.flatMap((group) => {
    const [first] = group.videos ?? [];
    return first ? [[group.shotId, first.assetId]] : [];
  }));
}

function Topbar({
  onNewTask
}: {
  onNewTask: () => void;
}) {
  return (
    <header className="topbar">
      <div className="brand-lockup" aria-label="AIVision Control">
        <LogoMark />
        <div>
          <h1>AIVision Control</h1>
          <p>可控式 AI 营销视频工作台</p>
        </div>
      </div>
      <div className="top-actions">
        <button className="primary" onClick={onNewTask}>
          <Plus size={18} />
          创建新任务
        </button>
      </div>
    </header>
  );
}

function LogoMark() {
  return (
    <svg className="logo-mark" viewBox="0 0 48 48" role="img" aria-hidden="true">
      <defs>
        <linearGradient id="logoGradient" x1="8" y1="7" x2="40" y2="42" gradientUnits="userSpaceOnUse">
          <stop stopColor="#31D0D8" />
          <stop offset="1" stopColor="#1494D2" />
        </linearGradient>
      </defs>
      <rect x="5" y="6" width="38" height="36" rx="12" fill="url(#logoGradient)" />
      <path
        d="M16 30V18.8c0-1.6 1.8-2.5 3.1-1.6l14.2 9.1c1.2.8 1.2 2.6 0 3.4l-14.2 9.1c-1.3.8-3.1-.1-3.1-1.7V30Z"
        fill="rgba(255,255,255,0.95)"
      />
      <circle cx="18.5" cy="18.5" r="4.5" fill="#0F2B3F" fillOpacity="0.18" />
      <path
        d="M34 12.8l1.1 2.2 2.4 1.1-2.4 1.1-1.1 2.2-1.1-2.2-2.4-1.1 2.4-1.1 1.1-2.2Z"
        fill="white"
      />
    </svg>
  );
}

function Sidebar({
  tasks,
  activeTaskId,
  onSelectTask
}: {
  tasks: TaskSummary[];
  activeTaskId: string;
  onSelectTask: (taskId: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [workflowFilter, setWorkflowFilter] = useState<"ALL" | WorkflowType>("ALL");
  const filteredTasks = tasks.filter((item) => {
    const keyword = query.trim().toLowerCase();
    const matchesKeyword = !keyword
      || item.taskId.toLowerCase().includes(keyword)
      || (item.productName ?? "").toLowerCase().includes(keyword);
    const matchesStatus = statusFilter === "ALL" || item.status === statusFilter;
    const matchesWorkflow = workflowFilter === "ALL" || item.workflowType === workflowFilter;
    return matchesKeyword && matchesStatus && matchesWorkflow;
  });
  return (
    <aside className="sidebar">
      <section>
        <h2>最近任务（最多 20 条）</h2>
        <p>{tasks.length} 个任务</p>
      </section>
      <div className="history-tools">
        <label className="search-box" aria-label="搜索任务">
          <Search size={16} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索标题或 ID" />
        </label>
        <div className="filter-row">
          <label>
            状态
            <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="ALL">全部</option>
              <option value="RUNNING">进行中</option>
              <option value="WAITING_REVIEW">待审核</option>
              <option value="SUCCESS">已完成</option>
              <option value="FAILED">失败</option>
            </select>
          </label>
          <label>
            流程
            <select value={workflowFilter} onChange={(event) => setWorkflowFilter(event.target.value as "ALL" | WorkflowType)}>
              <option value="ALL">全部</option>
              {workflowOptions.map((item) => <option key={item.value} value={item.value}>{item.shortLabel}</option>)}
            </select>
          </label>
        </div>
      </div>
      <div className="activity-title">活动历史</div>
      <div className="history-list">
        {filteredTasks.map((item) => (
          <button
            key={item.taskId}
            className={`history-card ${item.taskId === activeTaskId ? "active" : ""}`}
            onClick={() => onSelectTask(item.taskId)}
          >
            <span>{shortId(item.taskId)} · {workflowLabel(item.workflowType)}</span>
            <b>{summaryTitle(item.productName)}</b>
            <p>{statusText(item.status)} · {stageText(item.workflowType, item.currentStep)}</p>
            <small>{formatDateTime(item.updatedAt)}</small>
            {item.status === "FAILED" && <em>失败</em>}
          </button>
        ))}
        {tasks.length === 0 && <div className="empty">暂无任务</div>}
        {tasks.length > 0 && filteredTasks.length === 0 && <div className="empty">没有匹配任务</div>}
        {tasks.length > 0 && <p className="sidebar-footnote">任务较多时在管理端查看完整列表</p>}
      </div>
    </aside>
  );
}

function CreateTaskView({
  form,
  setForm,
  imageFile,
  setImageFile,
  videoFile,
  setVideoFile,
  busy,
  message,
  onSubmit
}: {
  form: FormState;
  setForm: React.Dispatch<React.SetStateAction<FormState>>;
  imageFile: File | null;
  setImageFile: (file: File | null) => void;
  videoFile: File | null;
  setVideoFile: (file: File | null) => void;
  busy: boolean;
  message: string;
  onSubmit: (event: React.FormEvent) => void;
}) {
  const selectedWorkflow = workflowOptions.find((item) => item.value === form.workflowType) ?? workflowOptions[0];
  const sourceReady = form.workflowType === "video_storyboard_ad"
    ? Boolean(videoFile || form.sourceVideoUrl.trim())
    : Boolean(imageFile || form.imageUrls.trim());
  const uploadWarning = !sourceReady;
  return (
    <form className="create-layout" onSubmit={onSubmit}>
      <section className="hero-copy">
        <div className="create-title">
          <span>新建任务</span>
          <h2>素材、需求、配置</h2>
          <p>{selectedWorkflow.description}</p>
        </div>
        <section className="process-board" aria-label="核心流程设计图和理念">
          <div className="process-kicker">流程说明</div>
          <div className="process-line">
            <ProcessStep icon={<UploadCloud size={18} />} title="素材输入" text={form.workflowType === "video_storyboard_ad" ? "本地视频或视频链接作为分镜理解源，图片素材作为可选参考。" : "本地图片或图片链接作为商品视觉锚点。"} />
            <ProcessStep icon={<BarChart3 size={18} />} title={form.workflowType === "video_storyboard_ad" ? "视频理解" : "营销策划"} text={form.workflowType === "video_storyboard_ad" ? "LLM 总结原视频的关键镜头并输出可编辑分镜。" : "LLM 提炼商品名称、目标人群、核心卖点和投放建议。"} />
            <ProcessStep icon={<FileText size={18} />} title="分镜脚本" text={form.workflowType === "video_storyboard_ad" ? "基于视频素材总结得到固定结构的分镜片段。" : "把营销方案拆成可审核、可编辑的分镜片段。"} />
            <ProcessStep icon={<ImageIcon size={18} />} title="图片生成与评估" text="一次组图生成候选图，再由模型按分镜匹配度评分。" />
            <ProcessStep icon={<Video size={18} />} title="视频生成与评估" text="按分镜时长和比例生成片段，评分后选择最佳素材。" />
            <ProcessStep icon={<Archive size={18} />} title="最终合成" text="FFmpeg 串联合格片段，生成可预览的最终视频。" />
            <ProcessStep icon={<Rocket size={18} />} title="人工重燃" text="任一步都能修改该步输入参数后重跑，并保留已完成历史。" />
          </div>
          <div className="process-principle">
            <b>设计理念</b>
            <span>AI 负责生成候选方案，人负责确认方向；每个节点先沉淀结构化结果，再进入下一步，让广告生成过程可追踪、可编辑、可重试。</span>
          </div>
        </section>
      </section>
      <section className="input-card">
        <div className="card-title">
          <span>1</span>
          <div>
            <h3>素材与需求</h3>
            <p>先确定输入来源，再补充广告生成要求。</p>
          </div>
        </div>
        <div className="workflow-segments" aria-label="工作流类型切换">
          {workflowOptions.map((item) => (
              <button
                type="button"
                className={form.workflowType === item.value ? "active" : ""}
                onClick={() => setForm((previous) => ({
                  ...previous,
                  workflowType: item.value,
                  inputType: item.value === "video_storyboard_ad" ? "source_video" : "product_image",
                  videoType: item.value === "video_storyboard_ad" ? "视频素材重制广告" : "商品展示视频",
                  text: item.value === "video_storyboard_ad"
                    ? "请基于上传的视频素材总结分镜，并重制为一条广告视频。"
                    : previous.text
                }))}
              >
                <b>{item.label}</b>
                <span>{item.description}</span>
              </button>
          ))}
        </div>
        {form.workflowType === "video_storyboard_ad" && (
          <>
            <label className={`upload-zone ${uploadWarning ? "warning" : ""}`}>
              <UploadCloud size={42} />
              <strong>{videoFile ? videoFile.name : (form.sourceVideoFileName || "拖拽源视频至此")}</strong>
              <span>上传本地视频后将保存到 S3 兼容对象存储（默认 7 天过期），后续理解阶段使用视频 URL</span>
              <input type="file" accept="video/*" onChange={(event) => setVideoFile(event.target.files?.[0] ?? null)} />
            </label>
            {uploadWarning && <p className="field-helper warning">请上传视频或填写至少一个视频链接</p>}
            <label>
              视频链接
              <textarea value={form.sourceVideoUrl} onChange={(event) => setFormValue("sourceVideoUrl", event.target.value, setForm)} placeholder="输入可直接访问的视频 URL" />
            </label>
          </>
        )}
        {form.workflowType !== "video_storyboard_ad" && (
          <>
            <label className={`upload-zone ${uploadWarning ? "warning" : ""}`}>
              <UploadCloud size={42} />
              <strong>{imageFile ? imageFile.name : (form.imageFileName || "拖拽产品图片至此")}</strong>
              <span>支持 PNG, JPG, WEBP 或 AVIF，上传后保存到 S3 兼容对象存储（默认 7 天过期）</span>
              <input type="file" accept="image/*" onChange={(event) => setImageFile(event.target.files?.[0] ?? null)} />
            </label>
            {uploadWarning && <p className="field-helper warning">请上传图片或填写至少一个链接</p>}
            <label>
              图片链接
              <textarea value={form.imageUrls} onChange={(event) => setFormValue("imageUrls", event.target.value, setForm)} placeholder="每行一个图片链接，例如 https://example.com/product.png" />
            </label>
          </>
        )}
        <label>
          产品或需求描述
          <textarea value={form.text} onChange={(event) => setFormValue("text", event.target.value, setForm)} placeholder={form.workflowType === "video_storyboard_ad" ? "描述你希望如何基于原视频重制广告，例如风格、时长、平台、保留哪些镜头..." : "描述视觉美学、灯光、运动行为和核心信息..."} />
        </label>
        <div className="metric-row">
          <label>
            候选图片数量
            <input type="number" min={1} max={10} value={form.generateImageCount} onChange={(event) => setFormValue("generateImageCount", event.target.value, setForm)} />
          </label>
          <label>
            候选视频数量
            <input type="number" min={1} max={5} value={form.generateVideoCount} onChange={(event) => setFormValue("generateVideoCount", event.target.value, setForm)} />
          </label>
        </div>
      </section>
      <aside className="config-card">
        <div className="card-title">
          <span>2</span>
          <div>
            <h3>生成配置</h3>
            <p>确认平台、规格、风格和自动化策略。</p>
          </div>
        </div>
        <div className="config-section">
          <span className="field-label">目标平台</span>
          <div className="platforms">
            {platformOptions.map((platform) => (
              <button
                key={platform.value}
                type="button"
                className={form.platform === platform.value ? "active" : ""}
                onClick={() => setFormValue("platform", platform.value, setForm)}
              >
                {platform.icon}
                <span>{platform.label}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="metric-row">
          <label>
            时长
            <div className="preset-row">
              {durationOptions.map((duration) => (
                <button type="button" key={duration} className={form.duration === duration ? "active" : ""} onClick={() => setFormValue("duration", duration, setForm)}>
                  {duration}秒
                </button>
              ))}
            </div>
            <input type="number" min={1} max={120} value={form.duration} onChange={(event) => setFormValue("duration", event.target.value, setForm)} />
          </label>
          <label>
            比例
            <select value={form.aspectRatio} onChange={(event) => setFormValue("aspectRatio", event.target.value, setForm)}>
              {aspectRatioOptions.map((ratio) => (
                <option key={ratio.value} value={ratio.value}>{ratio.label}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="config-section">
          <span className="field-label">选择主风格（单选）</span>
          <div className="style-tags">
            {["动漫", "电影感", "赛博朋克", "复古胶片", "极简高级", "国潮插画", "3D渲染", "手绘涂鸦", "水彩艺术", "像素游戏", "未来科技", "暗黑悬疑", "梦幻童话", "日系清新", "欧美大片", "蒸汽朋克", "霓虹都市", "黑白默片", "黏土动画"].map((style) => (
              <button type="button" key={style} className={form.style === style ? "active" : ""} onClick={() => setFormValue("style", style, setForm)}>
                {style}
              </button>
            ))}
          </div>
          <input value={form.style} onChange={(event) => setFormValue("style", event.target.value, setForm)} placeholder="自定义风格描述" />
        </div>
        <div className="summary-flags">
          <label className="summary-flag-toggle">
            <span>图片评分</span>
            <input
              type="checkbox"
              checked={form.imageScoringEnabled}
              onChange={(event) => setForm((previous) => ({ ...previous, imageScoringEnabled: event.target.checked }))}
            />
            <small className="field-helper">生成后为每组图片打分，结果展示在候选下方</small>
          </label>
          <label className="summary-flag-toggle">
            <span>视频评分</span>
            <input
              type="checkbox"
              checked={form.videoScoringEnabled}
              onChange={(event) => setForm((previous) => ({ ...previous, videoScoringEnabled: event.target.checked }))}
            />
            <small className="field-helper">生成后为每组视频打分，结果展示在候选下方</small>
          </label>
          <label className="summary-flag-toggle">
            <span>自动确认</span>
            <input
              type="checkbox"
              checked={form.autoConfirmEnabled}
              onChange={(event) => setForm((previous) => ({ ...previous, autoConfirmEnabled: event.target.checked }))}
            />
            <small className="field-helper">跳过人工选图/选视频，直接选最高分（与手动保存选择互斥）</small>
          </label>
        </div>
        <div className="submit-summary">
          <span>素材状态<b className={sourceReady ? "summary-ready" : "summary-warning"}>{sourceReady ? "已提供" : "待补充"}</b></span>
          <span>工作流<b>{selectedWorkflow.shortLabel}</b></span>
          <span>输出规格<b>{platformLabel(form.platform)} · {form.duration || "-"}s · {form.aspectRatio}</b></span>
        </div>
        {message && <div className="message">{message}</div>}
        <button className="primary big" disabled={busy || !sourceReady}>
          {busy ? <Loader2 className="spin" size={20} /> : <Sparkles size={22} />}
          开始生成
        </button>
      </aside>
    </form>
  );
}

function ProcessStep({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="process-step">
      <div className="process-icon">{icon}</div>
      <div>
        <b>{title}</b>
        <span>{text}</span>
      </div>
    </div>
  );
}

function WorkflowView(props: WorkflowViewProps) {
  const { task } = props;
  const [viewStage, setViewStage] = useState<TaskStage>(canonicalStage(task.stage));
  const [regenerateOpen, setRegenerateOpen] = useState(false);
  const readOnly = canonicalStage(viewStage) !== canonicalStage(task.stage);
  const workflowStages = stagesForWorkflow(task.workflowType);
  const currentStage = canonicalStage(task.stage);

  useEffect(() => {
    setViewStage(canonicalStage(task.stage));
  }, [task.taskId, task.stage]);

  function returnToCurrentStage() {
    setViewStage(currentStage);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function openRegenerateAt(stage: TaskStage) {
    props.setRegenerateStage(stage);
    setRegenerateOpen(true);
  }

  const stageTitle = viewStage === "COMPLETED"
    ? finalTitle(task)
    : `${readOnly ? "回顾 · " : ""}${stageText(task.workflowType, viewStage)}`;

  return (
    <div className="workflow-page">
      <section className="workflow-head">
        <div>
          <div className="headline-row">
            <h2>{stageTitle}</h2>
            <span className="workflow-type-tag">{workflowLabel(task.workflowType)}</span>
            <span className={`status ${task.status.toLowerCase()}`}>{statusText(task.status)}</span>
          </div>
          <p className="workflow-meta">
            任务 ID: <span>{task.taskId}</span>
            {!readOnly && props.stageTodoSummary && <> · <span className="stage-todo-summary">{props.stageTodoSummary}</span></>}
          </p>
        </div>
        <div className="workflow-actions">
          <button type="button" onClick={() => setRegenerateOpen(true)} disabled={props.busy || task.status === "RUNNING"}>
            <RotateCcw size={18} />
            重新生成
          </button>
          {!readOnly && (
            <button
              className="primary"
              onClick={props.onRequestAdvance}
              disabled={props.busy || task.status === "RUNNING" || task.status === "SUCCESS"}
              title={readOnly ? "请返回当前阶段后再继续" : undefined}
            >
              {props.busy ? <Loader2 className="spin" size={18} /> : <Rocket size={18} />}
              {nextLabel(task.workflowType, task.stage)}
            </button>
          )}
          {readOnly && (
            <button type="button" className="secondary" onClick={returnToCurrentStage}>
              返回当前阶段
            </button>
          )}
        </div>
      </section>
      <TaskSummaryBar task={task} viewingStage={viewStage} readOnly={readOnly} />
      <StageStepper current={task.stage} viewing={viewStage} status={task.status} task={task} onSelect={setViewStage} items={workflowStages} />
      {props.message && <div className="message">{props.message}</div>}
      {hasTaskError(task) && (
        <ErrorPanel
          task={task}
          onRegenerateFromFailure={() => openRegenerateAt(currentStage)}
          onReturnCurrent={returnToCurrentStage}
        />
      )}
      {readOnly && (
        <div className="review-mode-banner" role="status">
          <span>当前阶段仍为「{stageText(task.workflowType, task.stage)}」— 此处仅查看，不可编辑</span>
          <button type="button" className="link-button" onClick={returnToCurrentStage}>返回当前阶段</button>
        </div>
      )}
      <section className={`stage-canvas ${task.status === "RUNNING" ? "is-running" : ""}`}>
        {task.status === "RUNNING" && (
          <div className="stage-running-overlay" aria-live="polite">
            <Loader2 className="spin" size={28} />
            <strong>正在生成：{stageText(task.workflowType, task.stage)}</strong>
            <span>预计需数分钟，完成后自动刷新</span>
          </div>
        )}
        <StageContent {...props} viewStage={viewStage} readOnly={readOnly} onOpenRegenerate={openRegenerateAt} />
      </section>
      {regenerateOpen && (
        <RegenerateDrawer onClose={() => setRegenerateOpen(false)}>
          <RegenerateControls
            {...props}
            onRegenerate={async () => {
              const ok = await props.onRegenerate();
              if (ok) setRegenerateOpen(false);
              return ok;
            }}
          />
        </RegenerateDrawer>
      )}
    </div>
  );
}

/**
 * 功能描述：展示任务详情页的关键上下文，帮助用户快速判断当前待办和生成配置。
 * 参数解释：task 表示当前任务详情；viewingStage 表示用户正在查看的流程节点。
 * 返回对象描述：返回任务摘要条的 React 节点。
 * 可能抛出的异常：无。
 */
function TaskSummaryBar({ task, viewingStage, readOnly }: { task: TaskDetail; viewingStage: TaskStage; readOnly: boolean }) {
  const flags = [
    task.request?.imageScoringEnabled ? "图片评分" : "",
    task.request?.videoScoringEnabled ? "视频评分" : "",
    task.request?.autoConfirmEnabled ? "自动确认" : ""
  ].filter(Boolean);
  const viewingLabel = readOnly
    ? `回顾 · ${stageText(task.workflowType, viewingStage)}`
    : stageText(task.workflowType, viewingStage);
  return (
    <section className="task-summary-bar">
      <span>流程位置<b>{stageText(task.workflowType, task.stage)} · 正在查看 {viewingLabel}</b></span>
      <span>规格<b>{platformLabel(task.request?.platform ?? task.videoConfig?.platform)} · {task.request?.duration ?? task.videoConfig?.duration ?? "-"}s · {task.request?.aspectRatio ?? task.videoConfig?.aspectRatio ?? "-"}</b></span>
      <span>策略<b>{flags.length > 0 ? flags.join(" / ") : "人工确认"}</b></span>
      <span>更新<b>{formatDateTime(task.updatedAt)}</b></span>
    </section>
  );
}

/**
 * 功能描述：为重新生成表单提供右侧抽屉容器，避免重燃表单常驻占用主流程空间。
 * 参数解释：children 表示抽屉主体内容；onClose 表示关闭抽屉的回调函数。
 * 返回对象描述：返回包含遮罩、标题栏和主体内容的 React 节点。
 * 可能抛出的异常：无。
 */
function RegenerateDrawer({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="drawer-layer" role="dialog" aria-modal="true" aria-label="重新生成">
      <button className="drawer-mask" type="button" onClick={onClose} aria-label="关闭重新生成抽屉" />
      <aside className="drawer-panel">
        <header>
          <div>
            <span>重新生成</span>
            <h3>从指定阶段重跑后续内容</h3>
          </div>
          <button type="button" onClick={onClose} aria-label="关闭重新生成抽屉">
            <X size={18} />
          </button>
        </header>
        {children}
      </aside>
    </div>
  );
}

function ErrorPanel({
  task,
  onRegenerateFromFailure,
  onReturnCurrent
}: {
  task: TaskDetail;
  onRegenerateFromFailure: () => void;
  onReturnCurrent: () => void;
}) {
  const errorText = [
    `任务 ID: ${task.taskId}`,
    `失败节点: ${stageText(task.workflowType, task.stage)}`,
    task.errorCode ? `错误码: ${task.errorCode}` : "",
    `错误信息: ${task.errorMessage ?? "未知错误"}`
  ].filter(Boolean).join("\n");

  return (
    <section className="error-panel">
      <div>
        <strong>后端异常</strong>
        <span>{task.errorCode || "WORKFLOW_FAILED"}</span>
      </div>
      <p>{task.errorMessage || "任务执行失败，请查看服务端日志获取更多信息。"}</p>
      <div className="error-panel-actions">
        <button type="button" className="secondary" onClick={() => navigator.clipboard.writeText(errorText)}>
          <Copy size={16} />
          复制错误
        </button>
        <button type="button" className="primary-outline" onClick={onRegenerateFromFailure}>
          从失败阶段重新生成
        </button>
        <button type="button" className="link-button" onClick={onReturnCurrent}>
          返回当前阶段
        </button>
      </div>
    </section>
  );
}

function ConfirmAdvanceDialog({
  issues,
  busy,
  onSaveAndContinue,
  onDiscardAndContinue,
  onCancel
}: {
  issues: string[];
  busy: boolean;
  onSaveAndContinue: () => void;
  onDiscardAndContinue: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="confirm-advance-layer" role="dialog" aria-modal="true" aria-labelledby="confirm-advance-title">
      <button type="button" className="drawer-mask" onClick={onCancel} aria-label="关闭" />
      <div className="confirm-advance-dialog">
        <h3 id="confirm-advance-title">还有未保存的更改</h3>
        <ul>
          {issues.map((issue) => <li key={issue}>{issue}</li>)}
        </ul>
        <div className="confirm-advance-actions">
          <button type="button" className="primary" onClick={onSaveAndContinue} disabled={busy}>保存并继续</button>
          <button type="button" className="secondary" onClick={onDiscardAndContinue} disabled={busy}>放弃更改并继续</button>
          <button type="button" className="link-button" onClick={onCancel} disabled={busy}>取消</button>
        </div>
      </div>
    </div>
  );
}

function StageSaveBar({
  label,
  onSave,
  disabled,
  dirty
}: {
  label: string;
  onSave: () => void;
  disabled?: boolean;
  dirty?: boolean;
}) {
  return (
    <div className="stage-save-bar">
      {dirty && <span className="stage-save-hint">有未保存更改</span>}
      <button type="button" className={dirty ? "primary-outline" : "secondary"} onClick={onSave} disabled={disabled}>
        {label}
      </button>
    </div>
  );
}

function StageStepper({
  current,
  viewing,
  status,
  task,
  onSelect,
  items
}: {
  current: TaskStage;
  viewing: TaskStage;
  status: string;
  task: TaskDetail;
  onSelect: (stage: TaskStage) => void;
  items: Array<{ stage: TaskStage; label: string; icon: React.ReactNode }>;
}) {
  return (
    <div className="stage-stepper">
      {items.map((item) => {
        const active = item.stage === canonicalStage(current);
        const viewingStage = item.stage === canonicalStage(viewing);
        const done = stageIndex(task.workflowType, item.stage) < stageIndex(task.workflowType, current) || current === "COMPLETED";
        const available = isStageAvailable(task, item.stage);
        return (
          <button
            type="button"
            className={`stage-dot ${active ? "active" : ""} ${viewingStage ? "viewing" : ""} ${done ? "done" : ""}`}
            key={item.stage}
            disabled={!available}
            title={!available ? "该阶段尚未生成内容" : done ? "点击查看" : undefined}
            onClick={() => onSelect(item.stage)}
          >
            <span>{done ? <Check size={18} /> : item.icon}</span>
            {active && status === "WAITING_REVIEW" && <b>待审核</b>}
            {active && status === "RUNNING" && <b>执行中</b>}
            <small>{item.label}</small>
          </button>
        );
      })}
    </div>
  );
}

function StageContent(props: StageViewProps) {
  const { task, viewStage } = props;
  if (!isStageAvailable(task, viewStage)) return <div className="empty-state">该节点还没有生成内容。</div>;
  if (viewStage === "MARKET_PLANNING") return <MarketingStage task={task} onOpenRegenerate={props.onOpenRegenerate} />;
  if (viewStage === "SHOT_SCRIPT_GENERATING") {
    return task.workflowType === "video_storyboard_ad"
      ? <VideoUnderstandingStage {...props} />
      : <ShotStage {...props} />;
  }
  if (isImageReviewStage(viewStage)) return hasScoredImages(task) ? <ImageEvaluateStage {...props} /> : <ImageGenerateStage {...props} />;
  if (isVideoReviewStage(viewStage)) return hasScoredVideos(task) ? <VideoEvaluateStage {...props} /> : <VideoGenerateStage {...props} />;
  if (viewStage === "FINAL_COMPOSING" || viewStage === "COMPLETED") return <FinalStage task={task} />;
  return (
    <div className="empty-state">
      {task.workflowType === "video_storyboard_ad"
        ? "任务已创建，点击进入下一步开始视频理解与分镜。"
        : "任务已创建，点击进入下一步开始营销策划。"}
    </div>
  );
}

function MarketingStage({ task, onOpenRegenerate }: { task: TaskDetail; onOpenRegenerate?: (stage: TaskStage) => void }) {
  const config = task.videoConfig;
  if (!config) return <div className="empty-state">等待生成营销策划。</div>;
  return (
    <div className="marketing-stage">
      <div className="two-pane">
        <section className="panel-card marketing-plan-card">
          <h3>AI 生成营销策划方案</h3>
          <label>任务标题<input readOnly value={`${config.productInfo?.name ?? "产品"} 营销策划`} /></label>
          <label>目标人群<textarea readOnly value={config.targetAudience ?? ""} /></label>
          <label>核心卖点<textarea readOnly value={config.productInfo?.sellingPoint ?? ""} /></label>
          <label>创意策略<textarea className="marketing-advice" readOnly value={config.videoAdvice ?? ""} /></label>
        </section>
        <aside className="panel-card">
          <h3>配置摘要</h3>
          <div className="summary-list">
            <span>视频类型<b>{config.videoType}</b></span>
            <span>目标平台<b>{platformLabel(config.platform)}</b></span>
            <span>视频比例<b>{config.aspectRatio}</b></span>
            <span>视频时长<b>{config.duration}s</b></span>
            <span>参考素材<b>{resourceSummary(config.productInfo?.resources)}</b></span>
          </div>
        </aside>
      </div>
      <div className="marketing-regen-hint">
        <span>如需修改营销方案，请使用 <strong>重新生成 → 营销策划</strong></span>
        {onOpenRegenerate && (
          <button type="button" className="secondary" onClick={() => onOpenRegenerate("MARKET_PLANNING")}>
            打开重燃并定位到营销策划
          </button>
        )}
      </div>
    </div>
  );
}

function ShotReferenceField({
  shotId,
  reference,
  readOnly,
  onUpload,
  uploadError
}: {
  shotId: string;
  reference: string;
  readOnly: boolean;
  onUpload: (file: File) => void | Promise<void>;
  uploadError?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const hasPreview = isRenderableImage(reference);

  async function handleFileChange(file: File | null) {
    if (!file) return;
    await onUpload(file);
    if (inputRef.current) inputRef.current.value = "";
  }

  return (
    <div className="shot-reference-field">
      <span className="shot-reference-label">分镜参考图</span>
      <div className={`shot-reference-card ${hasPreview ? "has-image" : "empty"}`}>
        {hasPreview ? (
          <img className="shot-reference-preview" src={reference} alt={`${shotId}-reference`} />
        ) : (
          <div className="shot-reference-placeholder">
            <UploadCloud size={22} />
            <p>{reference ? "当前参考图不可预览" : "未上传参考图，将只按当前分镜内容生成候选图片"}</p>
          </div>
        )}
        {!readOnly && (
          <>
            <input
              ref={inputRef}
              type="file"
              accept="image/*"
              hidden
              onChange={(event) => void handleFileChange(event.target.files?.[0] ?? null)}
            />
            <button
              type="button"
              className="shot-reference-action"
              onClick={() => inputRef.current?.click()}
            >
              {hasPreview ? "替换参考图" : "上传参考图"}
            </button>
          </>
        )}
      </div>
      {uploadError && <p className="field-helper warning">{uploadError}</p>}
    </div>
  );
}

function ShotEditorList({
  shots,
  readOnly,
  workflowType,
  onChange
}: {
  shots: Shot[];
  readOnly: boolean;
  workflowType: WorkflowType;
  onChange: (shots: Shot[]) => void;
}) {
  const promptLabel = workflowType === "video_storyboard_ad" ? "画面总结" : "视觉提示词";
  const actionLabel = workflowType === "video_storyboard_ad" ? "镜头动作" : "运镜 / 动作";
  const wordsLabel = workflowType === "video_storyboard_ad" ? "口播 / 字幕" : "对白 / 旁白";

  function updateShot(shotId: string, patch: Partial<Shot>) {
    if (readOnly) return;
    onChange(shots.map((shot) => shot.shotId === shotId ? { ...shot, ...patch } : shot));
  }

  return (
    <div className="shot-list">
      {shots.map((shot) => (
        <article className="shot-card" key={shot.shotId}>
          <header>
            <b>{shot.shotId}</b>
            <label className="shot-duration-inline">
              时长（秒）
              <input
                type="number"
                min={1}
                max={120}
                readOnly={readOnly}
                value={shot.duration ?? 5}
                onChange={(event) => updateShot(shot.shotId, { duration: Number(event.target.value || 5) })}
              />
            </label>
          </header>
          <label>{promptLabel}<textarea readOnly={readOnly} value={shot.prompt ?? ""} onChange={(event) => updateShot(shot.shotId, { prompt: event.target.value })} /></label>
          <label>{actionLabel}<input readOnly={readOnly} value={shot.action ?? ""} onChange={(event) => updateShot(shot.shotId, { action: event.target.value })} /></label>
          <label>{wordsLabel}<textarea readOnly={readOnly} value={shot.words ?? ""} onChange={(event) => updateShot(shot.shotId, { words: event.target.value })} /></label>
          {workflowType === "video_storyboard_ad" && (
            <ShotReferenceField
              shotId={shot.shotId}
              reference={shot.reference ?? ""}
              readOnly={readOnly}
              onUpload={async (file) => updateShot(shot.shotId, { reference: await fileToJpegDataUrl(file) })}
            />
          )}
        </article>
      ))}
    </div>
  );
}

function VideoGenerationShotEditor({
  groups,
  readOnly,
  onChangeGroup
}: {
  groups: ShotImageGroup[];
  readOnly: boolean;
  onChangeGroup: (shotId: string, patch: Partial<ShotImageGroup>) => void;
}) {
  return (
    <div className="regen-video-shot-list">
      {groups.map((group) => (
          <div className="regen-video-shot" key={group.shotId}>
            <div className="regen-video-shot-head">
              <b>{group.shotId}</b>
              <span>{group.duration ?? "-"} 秒</span>
            </div>
            <div className="regen-video-shot-body">
              <div className="regen-video-shot-editor">
                <label>分镜视频时长（秒）<input type="number" min={1} max={120} readOnly={readOnly} value={group.duration ?? 5} onChange={(event) => onChangeGroup(group.shotId, { duration: Number(event.target.value || 5) })} /></label>
                <label>视频画面提示<textarea readOnly={readOnly} value={group.prompt} onChange={(event) => onChangeGroup(group.shotId, { prompt: event.target.value })} /></label>
                <label>镜头动作<input readOnly={readOnly} value={group.action} onChange={(event) => onChangeGroup(group.shotId, { action: event.target.value })} /></label>
                <label>口播 / 字幕<textarea readOnly={readOnly} value={group.words} onChange={(event) => onChangeGroup(group.shotId, { words: event.target.value })} /></label>
              </div>
            </div>
          </div>
      ))}
    </div>
  );
}

function ShotStage({ task, editableShots, setEditableShots, readOnly, onSaveStage, stageSaveLabel, isDirty }: StageViewProps) {
  return (
    <div className="stage-stack">
      <div className="two-pane">
        <section className="panel-card">
          <div className="section-head"><h3>分镜序列</h3><span>{editableShots.length} 个分镜</span></div>
          <ShotEditorList shots={editableShots} readOnly={readOnly} workflowType={task.workflowType ?? "product_image_ad"} onChange={setEditableShots} />
        </section>
        <aside className="panel-card">
          <h3>编辑范围</h3>
          <div className="summary-list">
            <span>当前流程<b>{stageText(task.workflowType, task.stage)}</b></span>
            <span>任务状态<b>{statusText(task.status)}</b></span>
            <span>{readOnly ? "查看模式" : "可编辑字段"}<b>{readOnly ? "历史节点只读" : "时长 / 提示词 / 运镜 / 口播"}</b></span>
          </div>
          <p className="hint">保存后会保留分镜 ID 和顺序，用修改后的分镜参数重新生成后续图片和视频。</p>
        </aside>
      </div>
      {!readOnly && (
        <StageSaveBar label={stageSaveLabel || "保存分镜"} onSave={() => void onSaveStage()} dirty={isDirty} disabled={readOnly} />
      )}
    </div>
  );
}

function VideoUnderstandingStage({ task, editableShots, setEditableShots, readOnly, onSaveStage, stageSaveLabel, isDirty }: StageViewProps) {
  return (
    <div className="stage-stack">
      <div className="two-pane">
        <section className="panel-card marketing-plan-card">
          <h3>视频理解结果</h3>
          <label>素材标题<input readOnly value={task.videoConfig?.productInfo?.name ?? task.request?.sourceVideoFileName ?? "视频素材"} /></label>
          <label>源视频链接<textarea readOnly value={task.request?.sourceVideoUrl ?? resolveLegacyVideoUrl(task.request)} /></label>
          <label>理解说明<textarea className="marketing-advice" readOnly value={task.videoConfig?.videoAdvice ?? ""} /></label>
        </section>
        <section className="panel-card">
          <div className="section-head"><h3>视频总结分镜</h3><span>{editableShots.length} 个分镜</span></div>
          <ShotEditorList shots={editableShots} readOnly={readOnly} workflowType="video_storyboard_ad" onChange={setEditableShots} />
        </section>
      </div>
      {!readOnly && (
        <StageSaveBar label={stageSaveLabel || "保存分镜"} onSave={() => void onSaveStage()} dirty={isDirty} disabled={readOnly} />
      )}
    </div>
  );
}

function upsertEditableShot(shots: Shot[], shotId: string, patch: Partial<Shot>, fallback: Shot): Shot[] {
  if (shots.some((item) => item.shotId === shotId)) {
    return shots.map((item) => item.shotId === shotId ? { ...item, ...patch } : item);
  }
  return [...shots, { ...fallback, ...patch }];
}

function shotFromImageGroup(group: ShotImageGroup, orderNo: number): Shot {
  return {
    shotId: group.shotId,
    orderNo,
    duration: group.duration ?? 5,
    prompt: group.prompt,
    action: group.action,
    words: group.words,
    reference: group.reference ?? "",
    camera: "",
    sceneType: ""
  };
}

function SelectionProgressBar({
  groups,
  selected,
  assetKey,
  allExpanded,
  onToggleExpandAll,
  showExpandAll = false
}: {
  groups: Array<ShotImageGroup | ShotVideoGroup>;
  selected: Record<string, string>;
  assetKey: "images" | "videos";
  allExpanded?: boolean;
  onToggleExpandAll?: () => void;
  showExpandAll?: boolean;
}) {
  const missingGroups = groups.filter((group) => {
    const assets = assetKey === "images" && "images" in group ? group.images : "videos" in group ? group.videos : [];
    return assets.length > 0 && !selected[group.shotId];
  });
  const selectedCount = groups.length - missingGroups.length;
  const nextMissing = missingGroups[0]?.shotId;

  function jumpTo(shotId: string) {
    document.getElementById(`shot-review-${shotId}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  if (groups.length === 0) return null;

  return (
    <div className="selection-progress-bar">
      <span>
        已选 <b>{selectedCount}/{groups.length}</b> 组
        {missingGroups.length > 0 && <> · 还有 <b>{missingGroups.length}</b> 组待选择</>}
      </span>
      <div className="selection-progress-actions">
        {showExpandAll && onToggleExpandAll && (
          <button type="button" className="link-button" onClick={onToggleExpandAll}>
            {allExpanded ? "收起全部" : "展开全部"}
          </button>
        )}
        {nextMissing && (
          <button type="button" className="link-button" onClick={() => jumpTo(nextMissing)}>
            下一组未选：{nextMissing} ↓
          </button>
        )}
      </div>
    </div>
  );
}

function resolveContextImageUrl(
  shotId: string,
  selectedImages: Record<string, string>,
  imageGroups: ShotImageGroup[]
) {
  const assetId = selectedImages[shotId];
  if (!assetId) return undefined;
  const group = imageGroups.find((item) => item.shotId === shotId);
  return group?.images.find((item) => item.assetId === assetId)?.url;
}

function ShotReviewCard({
  cardId,
  mode,
  imageGroup,
  videoGroup,
  shot,
  workflowType = "product_image_ad",
  selectedImages,
  selectedVideos,
  onSelectImages,
  onSelectVideos,
  onShotChange,
  contextImageUrl,
  readOnly,
  showScore,
  allowPendingScore,
  taskStatus,
  allExpanded = false,
  detailsExpanded = false,
  onToggleDetails
}: {
  cardId: string;
  mode: "image-generate" | "image-evaluate" | "video-evaluate";
  imageGroup?: ShotImageGroup;
  videoGroup?: ShotVideoGroup;
  shot?: Shot;
  workflowType?: WorkflowType;
  selectedImages?: Record<string, string>;
  selectedVideos?: Record<string, string>;
  onSelectImages?: (value: Record<string, string>) => void;
  onSelectVideos?: (value: Record<string, string>) => void;
  onShotChange?: (shotId: string, patch: Partial<Shot>) => void;
  contextImageUrl?: string;
  readOnly: boolean;
  showScore: boolean;
  allowPendingScore?: boolean;
  taskStatus?: string;
  allExpanded?: boolean;
  detailsExpanded?: boolean;
  onToggleDetails?: () => void;
}) {
  const [uploadError, setUploadError] = useState("");
  const group = imageGroup ?? videoGroup;
  if (!group) return null;
  const shotId = group.shotId;

  const assets = imageGroup?.images ?? videoGroup?.videos ?? [];
  const selectedAssetId = mode === "video-evaluate"
    ? selectedVideos?.[shotId]
    : selectedImages?.[shotId];
  const isSelected = Boolean(selectedAssetId);
  const promptLabel = workflowType === "video_storyboard_ad" ? "画面总结" : "视觉提示词";
  const actionLabel = workflowType === "video_storyboard_ad" ? "镜头动作" : "运镜 / 动作";
  const wordsLabel = workflowType === "video_storyboard_ad" ? "口播 / 字幕" : "对白 / 旁白";
  const paramSource = shot ?? shotFromImageGroup(imageGroup ?? {
    shotId,
    duration: group.duration,
    prompt: group.prompt,
    action: group.action,
    words: group.words,
    reference: group.reference ?? "",
    images: []
  }, 0);
  const currentReference = shot?.reference ?? imageGroup?.reference ?? group.reference ?? "";
  const detailsOpen = allExpanded || detailsExpanded;
  const detailsToggleLabel = mode === "video-evaluate" ? "分镜详情" : "编辑参数";

  async function uploadReference(file: File) {
    if (readOnly || !onShotChange) return;
    setUploadError("");
    try {
      onShotChange(shotId, { reference: await fileToJpegDataUrl(file) });
    } catch (error) {
      setUploadError(error instanceof Error ? error.message : "参考图上传失败");
    }
  }

  return (
    <article className="shot-review-card panel-card" id={cardId}>
      <header className="shot-review-header">
        <div>
          <b>{shotId}</b>
          <span>{group.duration ?? "-"} 秒 · {assets.length} 个候选</span>
        </div>
        <div className="shot-review-header-actions">
          <em className={isSelected ? "ready" : "pending"}>{isSelected ? "已选择" : "待选择"}</em>
          <button
            type="button"
            className={`shot-review-toggle ${detailsOpen ? "open" : ""}`}
            onClick={onToggleDetails}
          >
            {detailsToggleLabel}
            <ChevronDown size={16} />
          </button>
        </div>
      </header>

      {mode === "video-evaluate" && videoGroup ? (
        <VideoCandidateGrid
          group={videoGroup}
          selected={selectedVideos ?? {}}
          onSelect={onSelectVideos ?? (() => undefined)}
          readOnly={readOnly}
          showScore={showScore}
          allowPendingScore={allowPendingScore}
          taskStatus={taskStatus}
          allExpanded={allExpanded}
        />
      ) : imageGroup ? (
        <ImageCandidateGrid
          group={imageGroup}
          selected={selectedImages ?? {}}
          onSelect={onSelectImages ?? (() => undefined)}
          readOnly={readOnly}
          showScore={showScore}
          allowPendingScore={allowPendingScore}
          taskStatus={taskStatus}
          allExpanded={allExpanded}
        />
      ) : null}

      {mode === "video-evaluate" && detailsOpen && (
        <div className="shot-review-context">
          <span className="shot-review-context-label">分镜上下文</span>
          <div className="shot-review-context-body">
            {contextImageUrl && isRenderableImage(contextImageUrl) ? (
              <img className="shot-context-thumb" src={contextImageUrl} alt={`${shotId}-context`} />
            ) : (
              <div className="shot-context-thumb empty">无分镜图</div>
            )}
            <div className="shot-review-context-text">
              <p><b>{wordsLabel}</b>{group.words || "—"}</p>
              <p><b>{actionLabel}</b>{group.action || "—"}</p>
            </div>
          </div>
        </div>
      )}

      {mode !== "video-evaluate" && detailsOpen && (
        <div className="shot-review-params">
          <label className="shot-duration-inline">
            时长（秒）
            <input
              type="number"
              min={1}
              max={120}
              readOnly={readOnly}
              value={paramSource.duration ?? 5}
              onChange={(event) => onShotChange?.(shotId, { duration: Number(event.target.value || 5) })}
            />
          </label>
          <label>{promptLabel}<textarea readOnly={readOnly} value={paramSource.prompt ?? ""} onChange={(event) => onShotChange?.(shotId, { prompt: event.target.value })} /></label>
          <label>{actionLabel}<input readOnly={readOnly} value={paramSource.action ?? ""} onChange={(event) => onShotChange?.(shotId, { action: event.target.value })} /></label>
          <label>{wordsLabel}<textarea readOnly={readOnly} value={paramSource.words ?? ""} onChange={(event) => onShotChange?.(shotId, { words: event.target.value })} /></label>
          {workflowType === "video_storyboard_ad" && (
            <ShotReferenceField
              shotId={shotId}
              reference={currentReference}
              readOnly={readOnly}
              onUpload={uploadReference}
              uploadError={uploadError}
            />
          )}
        </div>
      )}
    </article>
  );
}

function ImageCandidateGrid({
  group,
  selected,
  onSelect,
  readOnly,
  showScore,
  allowPendingScore,
  taskStatus,
  allExpanded = false
}: {
  group: ShotImageGroup;
  selected: Record<string, string>;
  onSelect: (value: Record<string, string>) => void;
  readOnly: boolean;
  showScore: boolean;
  allowPendingScore?: boolean;
  taskStatus?: string;
  allExpanded?: boolean;
}) {
  function toggleSelect(assetId: string) {
    if (readOnly) return;
    if (selected[group.shotId] === assetId) {
      const next = { ...selected };
      delete next[group.shotId];
      onSelect(next);
      return;
    }
    onSelect({ ...selected, [group.shotId]: assetId });
  }

  function shouldShowScore(asset: ImageCandidate) {
    if (!showScore) return false;
    if (typeof asset.score === "number") return true;
    if (allowPendingScore && taskStatus === "RUNNING") return true;
    return false;
  }

  function scorePendingLabel(asset: ImageCandidate) {
    if (typeof asset.score === "number") return undefined;
    if (taskStatus === "RUNNING") return "评分中…";
    return allowPendingScore ? "待评分" : undefined;
  }

  return (
    <div className="shot-review-image-grid">
      {group.images.map((asset) => {
        const isSelected = selected[group.shotId] === asset.assetId;
        return (
          <div className={`media-card compact ${isSelected ? "selected" : ""}`} key={asset.assetId}>
            <div className="media-preview">
              {isRenderableImage(asset.url) ? <img src={asset.url} alt={asset.assetId} /> : <div className="mock-media">{asset.url}</div>}
            </div>
            {!readOnly && (
              <button
                type="button"
                className={`media-select-btn ${isSelected ? "active" : ""}`}
                aria-pressed={isSelected}
                onClick={() => toggleSelect(asset.assetId)}
              >
                {isSelected ? "已选中" : "选中此素材"}
              </button>
            )}
            {shouldShowScore(asset) && (
              <MediaScoreMeta
                score={asset.score}
                reason={asset.reason}
                pendingLabel={scorePendingLabel(asset)}
                allExpanded={allExpanded}
              />
            )}
            {isSelected && (
              <>
                <b className="checkmark"><Check size={18} /></b>
                <em className="selected-label">已选择</em>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

function VideoCandidateGrid({
  group,
  selected,
  onSelect,
  readOnly,
  showScore,
  allowPendingScore,
  taskStatus,
  allExpanded = false
}: {
  group: ShotVideoGroup;
  selected: Record<string, string>;
  onSelect: (value: Record<string, string>) => void;
  readOnly: boolean;
  showScore: boolean;
  allowPendingScore?: boolean;
  taskStatus?: string;
  allExpanded?: boolean;
}) {
  function toggleSelect(assetId: string) {
    if (readOnly) return;
    if (selected[group.shotId] === assetId) {
      const next = { ...selected };
      delete next[group.shotId];
      onSelect(next);
      return;
    }
    onSelect({ ...selected, [group.shotId]: assetId });
  }

  function shouldShowScore(asset: VideoCandidate) {
    if (!showScore) return false;
    if (typeof asset.score === "number") return true;
    if (allowPendingScore && taskStatus === "RUNNING") return true;
    return false;
  }

  function scorePendingLabel(asset: VideoCandidate) {
    if (typeof asset.score === "number") return undefined;
    if (taskStatus === "RUNNING") return "评分中…";
    return allowPendingScore ? "待评分" : undefined;
  }

  if (group.videos.length === 0) {
    return <div className="empty">暂无视频候选</div>;
  }

  return (
    <div className="shot-review-video-grid">
      {group.videos.map((asset) => {
        const isSelected = selected[group.shotId] === asset.assetId;
        return (
          <div className={`media-card compact video-card ${isSelected ? "selected" : ""}`} key={asset.assetId}>
            <div className="media-preview">
              {isRenderableVideo(asset.url) ? (
                <video
                  src={asset.url}
                  controls
                  playsInline
                  preload="metadata"
                  onClick={(event) => event.stopPropagation()}
                />
              ) : (
                <div className="mock-media">{asset.url}</div>
              )}
            </div>
            {!readOnly && (
              <button
                type="button"
                className={`media-select-btn ${isSelected ? "active" : ""}`}
                aria-pressed={isSelected}
                onClick={() => toggleSelect(asset.assetId)}
              >
                {isSelected ? "已选中" : "选中此素材"}
              </button>
            )}
            {shouldShowScore(asset) && (
              <MediaScoreMeta
                score={asset.score}
                reason={asset.reason}
                pendingLabel={scorePendingLabel(asset)}
                allExpanded={allExpanded}
              />
            )}
            {isSelected && (
              <>
                <b className="checkmark"><Check size={18} /></b>
                <em className="selected-label">已选择</em>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

function useShotReviewExpansion(shotIds: string[]) {
  const [allExpanded, setAllExpanded] = useState(false);
  const [detailOverrides, setDetailOverrides] = useState<Record<string, boolean>>({});

  function isDetailsExpanded(shotId: string) {
    return allExpanded || Boolean(detailOverrides[shotId]);
  }

  function toggleDetails(shotId: string) {
    if (allExpanded) {
      setAllExpanded(false);
      setDetailOverrides(Object.fromEntries(shotIds.filter((id) => id !== shotId).map((id) => [id, true])));
      return;
    }
    const nextOpen = !isDetailsExpanded(shotId);
    const next = { ...detailOverrides, [shotId]: nextOpen };
    if (shotIds.length > 0 && shotIds.every((id) => next[id])) {
      setAllExpanded(true);
      setDetailOverrides({});
      return;
    }
    setDetailOverrides(next);
  }

  function toggleExpandAll() {
    setAllExpanded((value) => !value);
    setDetailOverrides({});
  }

  return { allExpanded, isDetailsExpanded, toggleDetails, toggleExpandAll };
}

function ImageGenerateStage({ task, editableShots, setEditableShots, selectedImages, setSelectedImages, readOnly, onSaveStage, stageSaveLabel, isDirty }: StageViewProps) {
  const groups = task.imageGroups ?? [];
  const expansion = useShotReviewExpansion(groups.map((group) => group.shotId));
  return (
    <div className="stage-stack shot-review-stage">
      <SelectionProgressBar
        groups={groups}
        selected={selectedImages}
        assetKey="images"
        allExpanded={expansion.allExpanded}
        onToggleExpandAll={expansion.toggleExpandAll}
        showExpandAll
      />
      <div className="shot-review-list">
        {groups.map((group) => (
          <ShotReviewCard
            key={group.shotId}
            cardId={`shot-review-${group.shotId}`}
            mode="image-generate"
            imageGroup={group}
            shot={editableShots.find((item) => item.shotId === group.shotId)}
            workflowType={task.workflowType ?? "product_image_ad"}
            selectedImages={selectedImages}
            onSelectImages={setSelectedImages}
            onShotChange={(shotId, patch) => {
              if (readOnly) return;
              const index = groups.findIndex((item) => item.shotId === shotId);
              if (index < 0) return;
              setEditableShots(upsertEditableShot(editableShots, shotId, patch, shotFromImageGroup(groups[index], index + 1)));
            }}
            readOnly={readOnly}
            showScore={Boolean(task.request?.imageScoringEnabled)}
            allowPendingScore={false}
            taskStatus={task.status}
            allExpanded={expansion.allExpanded}
            detailsExpanded={expansion.isDetailsExpanded(group.shotId)}
            onToggleDetails={() => expansion.toggleDetails(group.shotId)}
          />
        ))}
      </div>
      {!readOnly && (
        <StageSaveBar label={stageSaveLabel || "保存图片选择"} onSave={() => void onSaveStage()} dirty={isDirty} />
      )}
    </div>
  );
}

function ImageEvaluateStage({ task, editableShots, setEditableShots, selectedImages, setSelectedImages, readOnly, onSaveStage, stageSaveLabel, isDirty }: StageViewProps) {
  const groups = task.scoredImageGroups ?? [];
  const expansion = useShotReviewExpansion(groups.map((group) => group.shotId));
  return (
    <div className="stage-stack shot-review-stage">
      <SelectionProgressBar
        groups={groups}
        selected={selectedImages}
        assetKey="images"
        allExpanded={expansion.allExpanded}
        onToggleExpandAll={expansion.toggleExpandAll}
        showExpandAll
      />
      <div className="shot-review-list">
        {groups.map((group) => (
          <ShotReviewCard
            key={group.shotId}
            cardId={`shot-review-${group.shotId}`}
            mode="image-evaluate"
            imageGroup={group}
            shot={editableShots.find((item) => item.shotId === group.shotId)}
            workflowType={task.workflowType ?? "product_image_ad"}
            selectedImages={selectedImages}
            onSelectImages={setSelectedImages}
            onShotChange={(shotId, patch) => {
              if (readOnly) return;
              const index = groups.findIndex((item) => item.shotId === shotId);
              if (index < 0) return;
              setEditableShots(upsertEditableShot(editableShots, shotId, patch, shotFromImageGroup(groups[index], index + 1)));
            }}
            readOnly={readOnly}
            showScore={Boolean(task.request?.imageScoringEnabled)}
            allowPendingScore
            taskStatus={task.status}
            allExpanded={expansion.allExpanded}
            detailsExpanded={expansion.isDetailsExpanded(group.shotId)}
            onToggleDetails={() => expansion.toggleDetails(group.shotId)}
          />
        ))}
      </div>
      {!readOnly && (
        <StageSaveBar label={stageSaveLabel || "保存图片选择"} onSave={() => void onSaveStage()} dirty={isDirty} />
      )}
    </div>
  );
}

function VideoGenerateStage({
  task,
  editableShots,
  setEditableShots,
  editableImageGroups,
  setEditableImageGroups,
  selectedImages,
  setSelectedImages,
  selectedVideos,
  setSelectedVideos,
  readOnly,
  onSaveStage,
  stageSaveLabel,
  isDirty
}: StageViewProps) {
  const missingCount = countMissingSelections(task.videoGroups, selectedVideos, "videos");
  const imageGroups = (task.scoredImageGroups?.length ? task.scoredImageGroups : task.imageGroups) ?? [];

  function updateImageGroup(shotId: string, patch: Partial<ShotImageGroup>) {
    if (readOnly) return;
    setEditableImageGroups(editableImageGroups.map((group) => group.shotId === shotId ? { ...group, ...patch } : group));
  }

  return (
    <div className="stage-stack">
      {task.workflowType === "video_storyboard_ad" ? (
        <section className="panel-card">
          <div className="section-head"><h3>分镜视频参数</h3><span>{editableImageGroups.length} 组分镜</span></div>
          <VideoGenerationShotEditor
            groups={editableImageGroups}
            readOnly={readOnly}
            onChangeGroup={updateImageGroup}
          />
        </section>
      ) : (
        <section className="panel-card">
          <div className="section-head"><h3>分镜参数</h3><span>{editableShots.length} 个分镜</span></div>
          <ShotEditorList
            shots={editableShots}
            readOnly={readOnly}
            workflowType="product_image_ad"
            onChange={setEditableShots}
          />
        </section>
      )}
      {task.workflowType === "video_storyboard_ad" && (
        <section className="panel-card">
          <div className="section-head"><h3>选定分镜图片</h3><span>{editableImageGroups.length} 组分镜</span></div>
          <MediaGrid
            groups={editableImageGroups}
            selected={selectedImages}
            onSelect={setSelectedImages}
            type="image"
            readOnly={readOnly}
            showScore={Boolean(task.request?.imageScoringEnabled)}
            allowPendingScore={false}
            taskStatus={task.status}
          />
        </section>
      )}
      {task.workflowType === "product_image_ad" && imageGroups.length > 0 && (
        <section className="panel-card">
          <div className="section-head"><h3>用于生成分镜视频的图片</h3></div>
          <MediaGrid
            groups={imageGroups}
            selected={selectedImages}
            onSelect={setSelectedImages}
            type="image"
            readOnly={readOnly}
            showScore={Boolean(task.request?.imageScoringEnabled)}
            allowPendingScore={false}
            taskStatus={task.status}
          />
        </section>
      )}
      <section className="panel-card">
        <div className="section-head"><h3>分镜视频候选</h3><span>{missingCount === 0 ? "已完成选择" : `${missingCount} 组待选择`}</span></div>
        <MediaGrid
          groups={task.videoGroups}
          selected={selectedVideos}
          onSelect={setSelectedVideos}
          type="video"
          readOnly={readOnly}
          showScore={Boolean(task.request?.videoScoringEnabled)}
          allowPendingScore={false}
          taskStatus={task.status}
        />
      </section>
      {!readOnly && (
        <StageSaveBar label={stageSaveLabel || "保存本阶段更改"} onSave={() => void onSaveStage()} dirty={isDirty} />
      )}
    </div>
  );
}

function VideoEvaluateStage({ task, selectedImages, selectedVideos, setSelectedVideos, readOnly, onSaveStage, stageSaveLabel, isDirty }: StageViewProps) {
  const groups = task.scoredVideoGroups ?? [];
  const imageGroups = (task.scoredImageGroups?.length ? task.scoredImageGroups : task.imageGroups) ?? [];
  const expansion = useShotReviewExpansion(groups.map((group) => group.shotId));
  return (
    <div className="stage-stack shot-review-stage">
      <SelectionProgressBar
        groups={groups}
        selected={selectedVideos}
        assetKey="videos"
        allExpanded={expansion.allExpanded}
        onToggleExpandAll={expansion.toggleExpandAll}
        showExpandAll
      />
      <div className="shot-review-list">
        {groups.map((group) => (
          <ShotReviewCard
            key={group.shotId}
            cardId={`shot-review-${group.shotId}`}
            mode="video-evaluate"
            videoGroup={group}
            contextImageUrl={resolveContextImageUrl(group.shotId, selectedImages, imageGroups)}
            selectedVideos={selectedVideos}
            onSelectVideos={setSelectedVideos}
            readOnly={readOnly}
            showScore={Boolean(task.request?.videoScoringEnabled)}
            allowPendingScore
            taskStatus={task.status}
            allExpanded={expansion.allExpanded}
            detailsExpanded={expansion.isDetailsExpanded(group.shotId)}
            onToggleDetails={() => expansion.toggleDetails(group.shotId)}
          />
        ))}
      </div>
      {!readOnly && (
        <StageSaveBar label={stageSaveLabel || "保存视频选择"} onSave={() => void onSaveStage()} dirty={isDirty} />
      )}
    </div>
  );
}

function FinalStage({ task }: { task: TaskDetail }) {
  return (
    <div className="final-layout">
      <section>
        <div className="final-title">
          <span><CheckCircle2 size={16} />生成完成</span>
          <h2>{finalTitle(task)}</h2>
          <p>任务 ID: {task.taskId} · 时长: {task.videoConfig?.duration ?? "-"}s</p>
        </div>
        {task.finalVideo?.videoUrl ? (
          <video className="final-video" src={task.finalVideo.videoUrl} controls />
        ) : (
          <div className="final-video placeholder"><PlayIcon /></div>
        )}
      </section>
      <aside className="result-stack">
        <section className="panel-card">
          <h3>发布文案 <Copy size={18} /></h3>
          <blockquote>{task.finalVideo?.videoRelease ?? "等待生成发布文案"}</blockquote>
          <div className="style-tags">{(task.finalVideo?.hashtags ?? []).map((tag) => <span key={tag}>{tag}</span>)}</div>
        </section>
        <section className="panel-card">
          <h3>分享链接</h3>
          <div className="copy-line">
            <input readOnly value={task.finalVideo?.videoUrl ?? ""} />
            <button disabled={!task.finalVideo?.videoUrl} onClick={() => navigator.clipboard.writeText(task.finalVideo?.videoUrl ?? "")}><Copy size={20} /></button>
          </div>
          <a className={`download-button ${task.finalVideo?.videoUrl ? "" : "disabled"}`} href={task.finalVideo?.videoUrl || undefined} download target="_blank" rel="noreferrer">
            <Download size={18} />下载高清视频
          </a>
        </section>
      </aside>
    </div>
  );
}

function MediaScoreMeta({
  score,
  reason,
  pendingLabel,
  allExpanded = false
}: {
  score?: number;
  reason?: string;
  pendingLabel?: string;
  allExpanded?: boolean;
}) {
  const [localExpanded, setLocalExpanded] = useState(false);
  const expanded = allExpanded || localExpanded;
  const label = typeof score === "number" ? `${score} 分` : (pendingLabel ?? "待评分");
  return (
    <div className="media-score-meta">
      <span>{label}</span>
      {reason ? (
        <>
          <small className={expanded ? "expanded" : "clamped"}>{reason}</small>
          {!allExpanded && (
            <button type="button" className="link-button" onClick={() => setLocalExpanded((value) => !value)}>
              {expanded ? "收起" : "展开"}
            </button>
          )}
        </>
      ) : null}
    </div>
  );
}

function MediaGrid({
  groups,
  selected,
  onSelect,
  type,
  readOnly = false,
  showScore = false,
  allowPendingScore = false,
  taskStatus
}: {
  groups: Array<ShotImageGroup | ShotVideoGroup>;
  selected: Record<string, string>;
  onSelect: (value: Record<string, string>) => void;
  type: "image" | "video";
  readOnly?: boolean;
  showScore?: boolean;
  allowPendingScore?: boolean;
  taskStatus?: string;
}) {
  function toggleSelect(shotId: string, assetId: string) {
    if (readOnly) return;
    if (selected[shotId] === assetId) {
      const next = { ...selected };
      delete next[shotId];
      onSelect(next);
      return;
    }
    onSelect({ ...selected, [shotId]: assetId });
  }

  function shouldShowScoreMeta(asset: { score?: number; reason?: string }) {
    if (!showScore) return false;
    if (typeof asset.score === "number") return true;
    if (allowPendingScore && taskStatus === "RUNNING") return true;
    return false;
  }

  function scorePendingLabel(asset: { score?: number }) {
    if (typeof asset.score === "number") return undefined;
    if (taskStatus === "RUNNING") return "评分中…";
    return allowPendingScore ? "待评分" : undefined;
  }

  return (
    <div className="media-groups">
      {groups.map((group) => {
        const assets = "images" in group ? group.images : group.videos;
        const selectedAsset = selected[group.shotId];
        return (
          <section className="media-group" key={group.shotId}>
            <header>
              <div>
                <b>{group.shotId}</b>
                <span>{group.duration ?? "-"} 秒 · {assets.length} 个候选</span>
              </div>
              <em className={selectedAsset ? "ready" : "pending"}>{selectedAsset ? "已选择" : "待选择"}</em>
            </header>
            <p>{group.prompt || group.action || "暂无分镜说明"}</p>
            <div className="media-grid">
              {assets.map((asset) => {
                const isSelected = selected[group.shotId] === asset.assetId;
                return (
                  <div
                    className={`media-card ${isSelected ? "selected" : ""}`}
                    key={asset.assetId}
                    tabIndex={readOnly ? -1 : 0}
                    onKeyDown={(event) => {
                      if (readOnly || event.target !== event.currentTarget) return;
                      if (event.key === " " || event.key === "Enter") {
                        event.preventDefault();
                        toggleSelect(group.shotId, asset.assetId);
                      }
                    }}
                  >
                    <div className="media-preview">
                      {type === "image" && isRenderableImage(asset.url) ? <img src={asset.url} alt={asset.assetId} /> : null}
                      {type === "video" && isRenderableVideo(asset.url) ? <video src={asset.url} controls muted onClick={(event) => event.stopPropagation()} /> : null}
                      {type === "image" && !isRenderableImage(asset.url) && <div className="mock-media">{asset.url}</div>}
                      {type === "video" && !isRenderableVideo(asset.url) && <div className="mock-media">{asset.url}</div>}
                    </div>
                    {!readOnly && (
                      <button
                        type="button"
                        className={`media-select-btn ${isSelected ? "active" : ""}`}
                        aria-pressed={isSelected}
                        onClick={() => toggleSelect(group.shotId, asset.assetId)}
                      >
                        {isSelected ? "已选中" : "选中此素材"}
                      </button>
                    )}
                    {shouldShowScoreMeta(asset) && (
                      <MediaScoreMeta
                        score={asset.score}
                        reason={asset.reason}
                        pendingLabel={scorePendingLabel(asset)}
                      />
                    )}
                    {isSelected && (
                      <>
                        <b className="checkmark"><Check size={18} /></b>
                        <em className="selected-label">已选择</em>
                      </>
                    )}
                  </div>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}

function RegenerateControls({
  task,
  regenerateStage,
  regenerateReason,
  regenerateDraft,
  editableShots,
  editableImageGroups,
  selectedImages,
  setRegenerateStage,
  setRegenerateReason,
  setRegenerateDraft,
  setEditableShots,
  setEditableImageGroups,
  setSelectedImages,
  onRegenerate,
  busy
}: WorkflowViewProps) {
  const draftCacheRef = useRef<Partial<Record<TaskStage, RegenerateDraft>>>({});
  const updateTaskInput = (patch: Partial<TaskRequest>) => setRegenerateDraft({ ...regenerateDraft, taskInput: { ...regenerateDraft.taskInput, ...patch } });
  const updateVideoConfig = (patch: Partial<VideoConfig>) => setRegenerateDraft({ ...regenerateDraft, videoConfig: { ...regenerateDraft.videoConfig, ...patch } });
  const updateProductInfo = (patch: Partial<ProductInfo>) => setRegenerateDraft({
    ...regenerateDraft,
    videoConfig: {
      ...regenerateDraft.videoConfig,
      productInfo: { ...regenerateDraft.videoConfig.productInfo, ...patch }
    }
  });
  const updateImageGroup = (shotId: string, patch: Partial<ShotImageGroup>) => {
    setEditableImageGroups(editableImageGroups.map((group) => group.shotId === shotId ? { ...group, ...patch } : group));
  };

  function handleStageChange(nextStage: TaskStage) {
    if (nextStage === regenerateStage) return;
    const hasLocalEdits = JSON.stringify(regenerateDraft) !== JSON.stringify(regenerateDraftFromTask(task));
    if (hasLocalEdits && !window.confirm("切换阶段将丢弃当前未应用修改，是否继续？")) {
      return;
    }
    draftCacheRef.current[regenerateStage] = regenerateDraft;
    setRegenerateStage(nextStage);
    const cached = draftCacheRef.current[nextStage];
    if (cached) {
      setRegenerateDraft(cached);
    }
  }
  const stageOptions = task.workflowType === "video_storyboard_ad"
    ? [
      { value: "SHOT_SCRIPT_GENERATING" as TaskStage, label: "视频理解与分镜" },
      { value: "IMAGE_GENERATING" as TaskStage, label: "图片生成与评估" },
      { value: "VIDEO_GENERATING" as TaskStage, label: "视频生成与评估" },
      { value: "FINAL_COMPOSING" as TaskStage, label: "最终合成" }
    ]
    : [
      { value: "MARKET_PLANNING" as TaskStage, label: "营销策划" },
      { value: "SHOT_SCRIPT_GENERATING" as TaskStage, label: "分镜脚本" },
      { value: "IMAGE_GENERATING" as TaskStage, label: "图片生成与评估" },
      { value: "VIDEO_GENERATING" as TaskStage, label: "视频生成与评估" },
      { value: "FINAL_COMPOSING" as TaskStage, label: "最终合成" }
    ];
  return (
    <div className="regen">
      <div className="regen-toolbar">
        <label className="regen-control">
          <span>重燃阶段</span>
          <select value={regenerateStage} onChange={(event) => handleStageChange(event.target.value as TaskStage)}>
            {stageOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="regen-control">
          <span>重燃原因</span>
          <input value={regenerateReason} onChange={(event) => setRegenerateReason(event.target.value)} placeholder="填写重燃原因..." />
        </label>
      </div>
      <div className="regen-stage-intro">
        <b>{stageOptions.find((option) => option.value === regenerateStage)?.label ?? "当前阶段"}</b>
        <span>修改该阶段真正使用的输入参数后，再从这里重新生成后续内容。</span>
      </div>
      <div className="regen-fields">
        {regenerateStage === "MARKET_PLANNING" && (
          <section className="regen-section">
            <label>需求描述<textarea value={regenerateDraft.taskInput.text ?? ""} onChange={(event) => updateTaskInput({ text: event.target.value })} /></label>
            <label>图片链接<textarea value={(regenerateDraft.taskInput.imageUrls ?? []).join("\n")} onChange={(event) => updateTaskInput({ imageUrls: event.target.value.split("\n").map((item) => item.trim()).filter(Boolean) })} /></label>
            <div className="metric-row">
              <label>
                目标平台
                <select
                  value={regenerateDraft.taskInput.platform ?? "douyin"}
                  onChange={(event) => updateTaskInput({ platform: event.target.value })}
                >
                  {platformOptions.map((platform) => (
                    <option key={platform.value} value={platform.value}>
                      {platform.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>风格<input value={regenerateDraft.taskInput.style ?? ""} onChange={(event) => updateTaskInput({ style: event.target.value })} /></label>
            </div>
            <div className="metric-row">
              <label>总时长<input type="number" min={1} value={regenerateDraft.taskInput.duration ?? 15} onChange={(event) => updateTaskInput({ duration: Number(event.target.value || 15) })} /></label>
              <label>比例<select value={regenerateDraft.taskInput.aspectRatio ?? "9:16"} onChange={(event) => updateTaskInput({ aspectRatio: event.target.value })}>{aspectRatioOptions.map((ratio) => <option key={ratio.value} value={ratio.value}>{ratio.label}</option>)}</select></label>
            </div>
          </section>
        )}
        {regenerateStage === "SHOT_SCRIPT_GENERATING" && (
          <section className="regen-section">
            <div className="metric-row">
              <label>{task.workflowType === "video_storyboard_ad" ? "素材标题" : "商品名称"}<input value={regenerateDraft.videoConfig.productInfo?.name ?? ""} onChange={(event) => updateProductInfo({ name: event.target.value })} /></label>
              <label>视频类型<input value={regenerateDraft.videoConfig.videoType ?? "商品展示视频"} onChange={(event) => updateVideoConfig({ videoType: event.target.value })} /></label>
            </div>
            {task.workflowType === "video_storyboard_ad" ? (
              <>
                <label>源视频链接<textarea value={regenerateDraft.taskInput.sourceVideoUrl ?? ""} onChange={(event) => updateTaskInput({ sourceVideoUrl: event.target.value })} /></label>
                <label>理解说明<textarea value={regenerateDraft.videoConfig.videoAdvice ?? ""} onChange={(event) => updateVideoConfig({ videoAdvice: event.target.value })} /></label>
              </>
            ) : (
              <>
                <label>目标人群<textarea value={regenerateDraft.videoConfig.targetAudience ?? ""} onChange={(event) => updateVideoConfig({ targetAudience: event.target.value })} /></label>
                <label>核心卖点<textarea value={regenerateDraft.videoConfig.productInfo?.sellingPoint ?? ""} onChange={(event) => updateProductInfo({ sellingPoint: event.target.value })} /></label>
                <label>创意策略<textarea value={regenerateDraft.videoConfig.videoAdvice ?? ""} onChange={(event) => updateVideoConfig({ videoAdvice: event.target.value })} /></label>
              </>
            )}
            <div className="section-head"><h4>分镜序列</h4><span>{editableShots.length} 个分镜</span></div>
            <ShotEditorList
              shots={editableShots}
              readOnly={false}
              workflowType={task.workflowType ?? "product_image_ad"}
              onChange={setEditableShots}
            />
          </section>
        )}
        {regenerateStage === "IMAGE_GENERATING" && (
          <section className="regen-section">
            <div className="metric-row">
              <label>候选图片数量<input type="number" min={1} max={10} value={regenerateDraft.taskInput.generateImageCount ?? 1} onChange={(event) => updateTaskInput({ generateImageCount: Number(event.target.value || 1) })} /></label>
              <label>画面比例<select value={regenerateDraft.taskInput.aspectRatio ?? "9:16"} onChange={(event) => updateTaskInput({ aspectRatio: event.target.value })}>{aspectRatioOptions.map((ratio) => <option key={ratio.value} value={ratio.value}>{ratio.label}</option>)}</select></label>
            </div>
            <p className="regen-hint">仅修改分镜参数与生成配置，已生成的图片候选不会在此展示。</p>
            <ShotEditorList
              shots={editableShots}
              readOnly={false}
              workflowType={task.workflowType ?? "product_image_ad"}
              onChange={setEditableShots}
            />
          </section>
        )}
        {regenerateStage === "VIDEO_GENERATING" && (
          <section className="regen-section">
            <div className="metric-row">
              <label>候选视频数量<input type="number" min={1} max={5} value={regenerateDraft.taskInput.generateVideoCount ?? 1} onChange={(event) => updateTaskInput({ generateVideoCount: Number(event.target.value || 1) })} /></label>
              <label>视频比例<select value={regenerateDraft.taskInput.aspectRatio ?? "9:16"} onChange={(event) => updateTaskInput({ aspectRatio: event.target.value })}>{aspectRatioOptions.map((ratio) => <option key={ratio.value} value={ratio.value}>{ratio.label}</option>)}</select></label>
            </div>
            <p className="regen-hint">仅修改分镜参数与生成配置，已生成的图片/视频候选不会在此展示。</p>
            {task.workflowType === "video_storyboard_ad" ? (
              <VideoGenerationShotEditor
                groups={editableImageGroups}
                readOnly={false}
                onChangeGroup={updateImageGroup}
              />
            ) : (
              <ShotEditorList
                shots={editableShots}
                readOnly={false}
                workflowType="product_image_ad"
                onChange={setEditableShots}
              />
            )}
          </section>
        )}
        {regenerateStage === "FINAL_COMPOSING" && (
          <section className="regen-section">
            <MediaGrid groups={draftVideoGroups(regenerateDraft)} selected={regenerateDraft.selectedVideos} onSelect={(value) => setRegenerateDraft({ ...regenerateDraft, selectedVideos: value })} type="video" showScore={Boolean(regenerateDraft.taskInput.videoScoringEnabled)} />
          </section>
        )}
      </div>
      <div className="regen-actions">
        <button className="primary" onClick={onRegenerate} disabled={busy}>应用重燃</button>
      </div>
    </div>
  );
}

function setFormValue(key: keyof FormState, value: string, setForm: React.Dispatch<React.SetStateAction<FormState>>) {
  setForm((previous) => ({ ...previous, [key]: value }));
}

function regenerateDraftFromTask(task: TaskDetail): RegenerateDraft {
  const imageUrls = mergeTaskImageUrls(task);
  return {
    taskInput: {
      workflowType: task.workflowType ?? "product_image_ad",
      inputType: task.request?.inputType ?? "product_image",
      text: task.request?.text ?? "",
      imageUrls,
      sourceVideoUrl: resolveTaskVideoUrl(task.request),
      sourceVideoFileName: task.request?.sourceVideoFileName ?? "",
      videoType: task.request?.videoType ?? "商品展示视频",
      platform: task.request?.platform ?? task.videoConfig?.platform ?? "douyin",
      duration: task.request?.duration ?? task.videoConfig?.duration ?? 15,
      aspectRatio: task.request?.aspectRatio ?? task.videoConfig?.aspectRatio ?? "9:16",
      style: task.request?.style ?? "",
      imageScoringEnabled: task.request?.imageScoringEnabled ?? false,
      videoScoringEnabled: task.request?.videoScoringEnabled ?? false,
      autoConfirmEnabled: task.request?.autoConfirmEnabled ?? false,
      generateImageCount: task.request?.generateImageCount ?? 4,
      generateVideoCount: task.request?.generateVideoCount ?? 2
    },
    videoConfig: task.videoConfig ?? emptyVideoConfig,
    shots: task.shots ?? [],
    imageGroups: (task.scoredImageGroups?.length ? task.scoredImageGroups : task.imageGroups) ?? [],
    videoGroups: (task.scoredVideoGroups?.length ? task.scoredVideoGroups : task.videoGroups) ?? [],
    selectedImages: Object.keys(Object.fromEntries((task.selectedImages ?? []).map((item) => [item.shotId, item.image.assetId]))).length > 0
      ? Object.fromEntries((task.selectedImages ?? []).map((item) => [item.shotId, item.image.assetId]))
      : fallbackSelectedImages(task.imageGroups ?? [], task.scoredImageGroups ?? []),
    selectedVideos: Object.keys(Object.fromEntries((task.selectedVideos ?? []).map((item) => [item.shotId, item.video.assetId]))).length > 0
      ? Object.fromEntries((task.selectedVideos ?? []).map((item) => [item.shotId, item.video.assetId]))
      : fallbackSelectedVideos(task.videoGroups ?? [], task.scoredVideoGroups ?? [])
  };
}

function buildSelectedImagesPayload(
  task: TaskDetail,
  shots: Shot[],
  imageGroups: ShotImageGroup[],
  selected: Record<string, string>
): SelectedImage[] {
  const taskGroups = (task.scoredImageGroups?.length ? task.scoredImageGroups : task.imageGroups) ?? [];
  if (task.workflowType === "video_storyboard_ad") {
    const sourceGroups = imageGroups.length > 0 ? imageGroups : taskGroups;
    return Object.entries(selected).flatMap(([shotId, assetId]) => {
      const group = sourceGroups.find((item) => item.shotId === shotId);
      const image = group?.images.find((item) => item.assetId === assetId);
      return group && image
        ? [{ shotId, duration: group.duration, image, prompt: group.prompt, action: group.action, words: group.words }]
        : [];
    });
  }
  return Object.entries(selected).flatMap(([shotId, assetId]) => {
    const group = taskGroups.find((item) => item.shotId === shotId);
    const image = group?.images.find((item) => item.assetId === assetId);
    const shot = shots.find((item) => item.shotId === shotId);
    if (!group || !image) return [];
    return [{
      shotId,
      duration: shot?.duration ?? group.duration,
      image,
      prompt: shot?.prompt ?? group.prompt,
      action: shot?.action ?? group.action,
      words: shot?.words ?? group.words
    }];
  });
}

function shotsForRegenerateApi(edited: Shot[], baseline: Shot[]): Shot[] {
  const baselineById = new Map(baseline.map((shot) => [shot.shotId, shot]));
  return edited.map((shot) => {
    const original = baselineById.get(shot.shotId);
    if (!original || shot.reference === original.reference) {
      const { reference: _reference, ...rest } = shot;
      return { ...rest, reference: "" };
    }
    return shot;
  });
}

function regeneratePayload(stage: TaskStage, draft: RegenerateDraft, workflowType: WorkflowType = "product_image_ad") {
  if (stage === "MARKET_PLANNING") {
    return { taskInput: draft.taskInput };
  }
  if (stage === "SHOT_SCRIPT_GENERATING") {
    return { taskInput: draft.taskInput, videoConfig: draft.videoConfig, shots: draft.shots };
  }
  if (stage === "IMAGE_GENERATING") {
    return { taskInput: draft.taskInput, shots: draft.shots };
  }
  if (stage === "VIDEO_GENERATING") {
    return {
      taskInput: draft.taskInput,
      selectedImages: selectedImagesFromDraft(draft, workflowType)
    };
  }
  if (stage === "FINAL_COMPOSING") {
    return {
      selectedVideos: selectedVideosFromDraft(draft)
    };
  }
  return {};
}

function selectedImagesFromDraft(draft: RegenerateDraft, workflowType: WorkflowType = "product_image_ad"): SelectedImage[] {
  return Object.entries(draft.selectedImages)
    .flatMap(([shotId, assetId]) => {
      const group = draftImageGroups(draft).find((item) => item.shotId === shotId);
      const image = group?.images.find((item) => item.assetId === assetId);
      if (!group || !image) return [];
      const shot = draft.shots.find((item) => item.shotId === shotId);
      if (workflowType === "product_image_ad" && shot) {
        return [{
          shotId,
          duration: shot.duration ?? group.duration,
          image,
          prompt: shot.prompt ?? group.prompt,
          action: shot.action ?? group.action,
          words: shot.words ?? group.words
        }];
      }
      return [{ shotId, duration: group.duration, image, prompt: group.prompt, action: group.action, words: group.words }];
    });
}

function selectedVideosFromDraft(draft: RegenerateDraft): SelectedVideo[] {
  return Object.entries(draft.selectedVideos)
    .flatMap(([shotId, assetId]) => {
      const group = draftVideoGroups(draft).find((item) => item.shotId === shotId);
      const video = group?.videos.find((item) => item.assetId === assetId);
      return group && video ? [{ shotId, duration: group.duration, video, words: group.words, action: group.action }] : [];
    });
}

function draftImageGroups(draft: RegenerateDraft): ShotImageGroup[] {
  return draft.imageGroups;
}

function draftVideoGroups(draft: RegenerateDraft): ShotVideoGroup[] {
  return draft.videoGroups;
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

async function fileToJpegDataUrl(file: File) {
  if ("createImageBitmap" in window) {
    try {
      const bitmap = await createImageBitmap(file);
      return drawImageSourceToJpeg(bitmap, () => bitmap.close());
    } catch {
      // Fall back to the data URL path below for browsers/files that createImageBitmap cannot decode.
    }
  }
  return convertDataUrlToJpeg(await readFileAsDataUrl(file));
}

async function normalizeImageUrl(url: string) {
  if (url.startsWith("data:image/")) {
    return normalizeImageDataUrl(url);
  }
  return url;
}

async function normalizeImageDataUrl(dataUrl: string) {
  const match = dataUrl.match(/^data:(image\/[^;]+);base64,(.+)$/i);
  if (!match) return dataUrl;
  return convertDataUrlToJpeg(dataUrl);
}

function convertDataUrlToJpeg(dataUrl: string, quality = 0.92) {
  return new Promise<string>((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      resolve(drawImageSourceToJpeg(image, undefined, quality));
    };
    image.onerror = () => reject(new Error("无法解析图片"));
    image.src = dataUrl;
  });
}

function drawImageSourceToJpeg(image: HTMLImageElement | ImageBitmap, cleanup?: () => void, quality = 0.92) {
  try {
    const width = image instanceof HTMLImageElement ? image.naturalWidth : image.width;
    const height = image instanceof HTMLImageElement ? image.naturalHeight : image.height;
    if (!width || !height) {
      throw new Error("图片尺寸无效");
    }
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("无法创建 canvas 上下文");
    }
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(image, 0, 0, width, height);
    return canvas.toDataURL("image/jpeg", quality);
  } finally {
    cleanup?.();
  }
}

function delay(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

function countMissingSelections(groups: Array<ShotImageGroup | ShotVideoGroup>, selected: Record<string, string>, assetKey: "images" | "videos") {
  return groups.filter((group) => {
    const assets = assetKey === "images" && "images" in group ? group.images : "videos" in group ? group.videos : [];
    return assets.length > 0 && !selected[group.shotId];
  }).length;
}

function isRenderableImage(url?: string) {
  return Boolean(url && (url.startsWith("http") || url.startsWith("/") || url.startsWith("data:image/")));
}

function isRenderableVideo(url?: string) {
  return Boolean(url && (url.startsWith("http") || url.startsWith("/") || url.startsWith("data:video/")));
}

function stagesForWorkflow(workflowType: WorkflowType) {
  if (workflowType === "video_storyboard_ad") {
    return [
      { stage: "CREATED" as TaskStage, label: "创建", icon: <Check size={16} /> },
      { stage: "SHOT_SCRIPT_GENERATING" as TaskStage, label: "视频理解与分镜", icon: <FileText size={16} /> },
      { stage: "IMAGE_GENERATING" as TaskStage, label: "图片生成与评估", icon: <ImageIcon size={16} /> },
      { stage: "VIDEO_GENERATING" as TaskStage, label: "视频生成与评估", icon: <Video size={16} /> },
      { stage: "FINAL_COMPOSING" as TaskStage, label: "最终合成", icon: <Archive size={16} /> },
      { stage: "COMPLETED" as TaskStage, label: "完成", icon: <CheckCircle2 size={16} /> }
    ];
  }
  return stages;
}

function stageIndex(workflowType: WorkflowType, stage: TaskStage) {
  return stagesForWorkflow(workflowType).findIndex((item) => item.stage === canonicalStage(stage));
}

function isStageAvailable(task: TaskDetail, stage: TaskStage) {
  const target = canonicalStage(stage);
  if (stage === "CREATED") return true;
  if (target === canonicalStage(task.stage)) return true;
  if (target === "MARKET_PLANNING") return Boolean(task.videoConfig);
  if (target === "SHOT_SCRIPT_GENERATING") return (task.shots ?? []).length > 0;
  if (target === "IMAGE_GENERATING") return (task.imageGroups ?? []).length > 0 || hasScoredImages(task);
  if (target === "VIDEO_GENERATING") return (task.videoGroups ?? []).length > 0 || hasScoredVideos(task);
  if (target === "FINAL_COMPOSING") return Boolean(task.finalVideo) || task.stage === "COMPLETED";
  if (target === "COMPLETED") return Boolean(task.finalVideo);
  return false;
}

function canEdit(stage: TaskStage) {
  return stage === "SHOT_SCRIPT_GENERATING" || isImageReviewStage(stage) || isVideoReviewStage(stage);
}

function nextLabel(workflowType: WorkflowType, stage: TaskStage) {
  if (stage === "CREATED") return "开始生成";
  if (stage === "COMPLETED") return "已完成";
  const items = stagesForWorkflow(workflowType);
  const index = stageIndex(workflowType, stage);
  const next = items[index + 1];
  return next ? `进入下一步：${next.label}` : "进入下一步";
}

function summaryTitle(value: string) {
  if (!value) return "未命名任务";
  return value.length > 18 ? `${value.slice(0, 18)}...` : value;
}

/**
 * 功能描述：将工作流枚举转换为界面展示名称。
 * 参数解释：workflowType 表示任务所属的工作流类型。
 * 返回对象描述：返回对应的中文工作流名称。
 * 可能抛出的异常：无。
 */
function workflowLabel(workflowType: WorkflowType) {
  return workflowOptions.find((item) => item.value === workflowType)?.shortLabel ?? workflowType;
}

function platformLabel(platform?: string) {
  return platformOptions.find((item) => item.value === platform)?.label ?? platform ?? "抖音";
}

/**
 * 功能描述：格式化后端时间字段，保证历史任务和任务摘要可快速扫描。
 * 参数解释：value 表示后端返回的时间字符串。
 * 返回对象描述：返回本地化后的简短日期时间；当输入为空或无效时返回占位符。
 * 可能抛出的异常：无。
 */
function formatDateTime(value?: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function mergeTaskImageUrls(task: TaskDetail) {
  const urls = [...(task.request?.imageUrls ?? task.videoConfig?.productInfo?.resources ?? [])];
  for (const value of task.request?.imageFileIds ?? []) {
    if (value.startsWith("http://") || value.startsWith("https://")) {
      if (!urls.includes(value)) urls.push(value);
    }
  }
  return urls;
}

function resolveTaskVideoUrl(request?: TaskRequest) {
  if (!request) return "";
  if (request.sourceVideoUrl) return request.sourceVideoUrl;
  if (request.sourceVideoFileId?.startsWith("http://") || request.sourceVideoFileId?.startsWith("https://")) {
    return request.sourceVideoFileId;
  }
  return "";
}

function resolveLegacyVideoUrl(request?: TaskRequest) {
  return resolveTaskVideoUrl(request);
}

function shortId(value: string) {
  return value ? `#${value.slice(-8)}` : "#TASK";
}

function statusText(value: string) {
  const map: Record<string, string> = {
    CREATED: "待开始",
    RUNNING: "进行中",
    WAITING_REVIEW: "待审核",
    SUCCESS: "已完成",
    FAILED: "失败"
  };
  return map[value] ?? value;
}

function stageText(workflowType: WorkflowType, value: TaskStage) {
  if (value === "FAILED") return "失败";
  const found = stagesForWorkflow(workflowType).find((item) => item.stage === canonicalStage(value));
  return found?.label ?? value;
}

function canonicalStage(stage: TaskStage) {
  if (stage === "IMAGE_EVALUATING" || stage === "IMAGE_SELECTING") return "IMAGE_GENERATING";
  if (stage === "VIDEO_EVALUATING" || stage === "VIDEO_SELECTING") return "VIDEO_GENERATING";
  return stage;
}

function isImageReviewStage(stage: TaskStage) {
  return canonicalStage(stage) === "IMAGE_GENERATING";
}

function isVideoReviewStage(stage: TaskStage) {
  return canonicalStage(stage) === "VIDEO_GENERATING";
}

function hasScoredImages(task: TaskDetail) {
  return (task.scoredImageGroups ?? []).length > 0;
}

function hasScoredVideos(task: TaskDetail) {
  return (task.scoredVideoGroups ?? []).length > 0;
}

function hasTaskError(task: TaskDetail) {
  return task.status === "FAILED" || Boolean(task.errorCode || task.errorMessage);
}

function finalTitle(task: TaskDetail) {
  return task.finalVideo?.videoTitle || task.videoConfig?.productInfo?.name || "生成结果";
}

function resourceSummary(resources?: string[]) {
  if (!resources || resources.length === 0) return "未提供";
  const hidden = resources.filter((item) => item.startsWith("data:image/")).length;
  const visible = resources.length - hidden;
  if (hidden > 0 && visible > 0) return `${visible} 个链接，${hidden} 张上传图片已隐藏`;
  if (hidden > 0) return `${hidden} 张上传图片已隐藏`;
  return `${visible} 个链接`;
}

function buildPersistedSnapshot(
  taskUpdatedAt: string,
  shots: Shot[],
  selectedImages: Record<string, string>,
  selectedVideos: Record<string, string>,
  imageGroups: ShotImageGroup[]
): PersistedSnapshot {
  return {
    taskUpdatedAt,
    shotsJson: JSON.stringify(shots),
    selectedImagesJson: JSON.stringify(selectedImages),
    selectedVideosJson: JSON.stringify(selectedVideos),
    imageGroupsJson: JSON.stringify(imageGroups)
  };
}

function compareDirtyState(
  snapshot: PersistedSnapshot,
  taskUpdatedAt: string,
  shots: Shot[],
  selectedImages: Record<string, string>,
  selectedVideos: Record<string, string>,
  imageGroups: ShotImageGroup[]
): { dirty: boolean; issues: string[] } {
  if (snapshot.taskUpdatedAt !== taskUpdatedAt) {
    return { dirty: false, issues: [] };
  }
  const issues: string[] = [];
  if (JSON.stringify(shots) !== snapshot.shotsJson) {
    issues.push("分镜参数已修改");
  }
  if (JSON.stringify(selectedImages) !== snapshot.selectedImagesJson) {
    issues.push("图片选择已变更");
  }
  if (JSON.stringify(selectedVideos) !== snapshot.selectedVideosJson) {
    issues.push("视频选择已变更");
  }
  if (JSON.stringify(imageGroups) !== snapshot.imageGroupsJson) {
    issues.push("分镜视频参数已修改");
  }
  return { dirty: issues.length > 0, issues };
}

function getMissingSelectionCountForTask(
  task: TaskDetail,
  selectedImages: Record<string, string>,
  selectedVideos: Record<string, string>
) {
  const stage = canonicalStage(task.stage);
  if (stage === "IMAGE_GENERATING") {
    const groups = hasScoredImages(task) ? task.scoredImageGroups : task.imageGroups;
    return countMissingSelections(groups ?? [], selectedImages, "images");
  }
  if (stage === "VIDEO_GENERATING") {
    const groups = hasScoredVideos(task) ? task.scoredVideoGroups : task.videoGroups;
    return countMissingSelections(groups ?? [], selectedVideos, "videos");
  }
  return 0;
}

function getStageTodoSummary(task: TaskDetail, selectedImages: Record<string, string>, selectedVideos: Record<string, string>) {
  const stage = canonicalStage(task.stage);
  if (stage === "CREATED") return "点击开始进入生成";
  if (stage === "MARKET_PLANNING") return "查看方案，确认后进入分镜";
  if (stage === "SHOT_SCRIPT_GENERATING") return "编辑分镜参数后保存";
  if (stage === "IMAGE_GENERATING") {
    const groups = hasScoredImages(task) ? task.scoredImageGroups : task.imageGroups;
    const missing = countMissingSelections(groups ?? [], selectedImages, "images");
    return missing > 0 ? `为每组分镜选择图片 · ${missing} 组待选` : "图片选择已完成，可进入下一步";
  }
  if (stage === "VIDEO_GENERATING") {
    const groups = hasScoredVideos(task) ? task.scoredVideoGroups : task.videoGroups;
    const missing = countMissingSelections(groups ?? [], selectedVideos, "videos");
    return missing > 0 ? `为每组分镜选择视频 · ${missing} 组待选` : "视频选择已完成，可进入下一步";
  }
  if (stage === "FINAL_COMPOSING" || stage === "COMPLETED") return "";
  return "";
}

function getStageSaveLabel(task: TaskDetail) {
  const stage = canonicalStage(task.stage);
  if (stage === "SHOT_SCRIPT_GENERATING") return "保存分镜";
  if (stage === "IMAGE_GENERATING") return "保存图片选择";
  if (stage === "VIDEO_GENERATING") {
    if (task.stage === "VIDEO_EVALUATING" || task.stage === "VIDEO_SELECTING") return "保存视频选择";
    return "保存本阶段更改";
  }
  return "";
}

function PlayIcon() {
  return <span className="play-icon">▶</span>;
}

createRoot(document.getElementById("root")!).render(<App />);
