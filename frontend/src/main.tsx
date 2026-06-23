import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Archive,
  BarChart3,
  BookOpen,
  ChevronDown,
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
  X,
} from "lucide-react";
import "./styles.css";

const API_BASE = "";

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

type ScoreDraftAsset = {
  assetId: string;
  id: number;
  score?: number;
  reason?: string;
  selected?: boolean;
};

type ImageScoreDraftGroup = {
  shotId: string;
  prompt: string;
  action: string;
  words: string;
  images: ScoreDraftAsset[];
};

type VideoScoreDraftGroup = {
  shotId: string;
  prompt: string;
  action: string;
  words: string;
  videos: ScoreDraftAsset[];
};

type ScoreEditorMode = "image" | "video";

type ScoreDraftGroup = {
  shotId: string;
  prompt?: string;
  action?: string;
  words?: string;
  images?: ScoreDraftAsset[];
  videos?: ScoreDraftAsset[];
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
  imageScoresJson: string;
  videoScoresJson: string;
  regenerateStage: TaskStage;
  regenerateReason: string;
  regenerateDraft: RegenerateDraft;
  setSelectedImages: (value: Record<string, string>) => void;
  setSelectedVideos: (value: Record<string, string>) => void;
  setEditableShots: (value: Shot[]) => void;
  setImageScoresJson: (value: string) => void;
  setVideoScoresJson: (value: string) => void;
  setRegenerateStage: (value: TaskStage) => void;
  setRegenerateReason: (value: string) => void;
  setRegenerateDraft: (value: RegenerateDraft) => void;
  onAdvance: () => void;
  onSaveEdits: () => void;
  onSaveSelections: () => void;
  onRegenerate: () => void;
};

type StageViewProps = WorkflowViewProps & {
  viewStage: TaskStage;
  readOnly: boolean;
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
  style: "产品特写",
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
  const [imageScoresJson, setImageScoresJson] = useState("");
  const [videoScoresJson, setVideoScoresJson] = useState("");
  const [regenerateDraft, setRegenerateDraft] = useState<RegenerateDraft>(emptyRegenerateDraft);

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
    setEditableShots(detail.shots ?? []);
    setImageScoresJson(JSON.stringify(toImageScoreDraft(detail.scoredImageGroups ?? [], nextSelectedImages), null, 2));
    setVideoScoresJson(JSON.stringify(toVideoScoreDraft(detail.scoredVideoGroups ?? [], nextSelectedVideos), null, 2));
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

  async function postAction(path: string, successMessage: string, body?: unknown, options?: { waitForChange?: boolean }) {
    if (!taskId) return;
    const previousTask = task;
    setBusy(true);
    setMessage("");
    try {
      const response = await fetch(`${API_BASE}${path}`, {
        method: "POST",
        headers: body ? { "Content-Type": "application/json" } : undefined,
        body: body ? JSON.stringify(body) : undefined
      });
      const payload = (await response.json()) as ApiResponse<unknown>;
      if (!response.ok || payload.code !== 0) throw new Error(payload.message || response.statusText);
      setMessage(successMessage);
      if (options?.waitForChange && previousTask) {
        await waitForTaskChange(previousTask);
      } else {
        await loadTask(taskId);
      }
      await loadTasks();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "操作失败");
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
    const payload: Partial<Pick<TaskDetail, "shots" | "scoredImageGroups" | "scoredVideoGroups">> = {};
    try {
      if (task.stage === "SHOT_SCRIPT_GENERATING") payload.shots = editableShots;
      if (isImageReviewStage(task.stage)) {
        payload.scoredImageGroups = applyImageScoreDraft(task.scoredImageGroups, JSON.parse(imageScoresJson) as ImageScoreDraftGroup[]);
      }
      if (isVideoReviewStage(task.stage)) {
        payload.scoredVideoGroups = applyVideoScoreDraft(task.scoredVideoGroups, JSON.parse(videoScoresJson) as VideoScoreDraftGroup[]);
      }
    } catch (error) {
      setMessage(error instanceof Error ? `JSON 格式错误：${error.message}` : "JSON 格式错误");
      return;
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

  async function regenerate() {
    if (!task) return;
    await postAction(`/api/video-tasks/${task.taskId}/regenerate`, "已重新生成", {
      fromStage: regenerateStage,
      reason: regenerateReason,
      shotIds: [],
      ...regeneratePayload(regenerateStage, regenerateDraft)
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

  function chooseImages(value: Record<string, string>) {
    setSelectedImages(value);
    setImageScoresJson((previous) => markSelectedInScoreJson(previous, value, "images"));
  }

  function chooseVideos(value: Record<string, string>) {
    setSelectedVideos(value);
    setVideoScoresJson((previous) => markSelectedInScoreJson(previous, value, "videos"));
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
            imageScoresJson={imageScoresJson}
            videoScoresJson={videoScoresJson}
            regenerateStage={regenerateStage}
            regenerateReason={regenerateReason}
            regenerateDraft={regenerateDraft}
            setSelectedImages={chooseImages}
            setSelectedVideos={chooseVideos}
            setEditableShots={setEditableShots}
            setImageScoresJson={setImageScoresJson}
            setVideoScoresJson={setVideoScoresJson}
            setRegenerateStage={setRegenerateStage}
            setRegenerateReason={setRegenerateReason}
            setRegenerateDraft={setRegenerateDraft}
            onAdvance={advance}
            onSaveEdits={saveCurrentEdits}
            onSaveSelections={saveSelections}
            onRegenerate={regenerate}
          />
        )}
      </main>
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
        <h2>历史任务</h2>
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
  return (
    <form className="create-layout" onSubmit={onSubmit}>
      <section className="hero-copy">
        <div className="create-title">
          <span>新建任务</span>
          <h2>素材、需求、配置</h2>
          <p>{selectedWorkflow.description}</p>
        </div>
        <details className="process-board" aria-label="核心流程设计图和理念">
          <summary>
            <span>查看流程说明</span>
            <ChevronDown size={16} />
          </summary>
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
        </details>
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
            <label className="upload-zone">
              <UploadCloud size={42} />
              <strong>{videoFile ? videoFile.name : (form.sourceVideoFileName || "拖拽源视频至此")}</strong>
              <span>上传本地视频后将保存到 S3 兼容对象存储（默认 7 天过期），后续理解阶段使用视频 URL</span>
              <input type="file" accept="video/*" onChange={(event) => setVideoFile(event.target.files?.[0] ?? null)} />
            </label>
            <label>
              视频链接
              <textarea value={form.sourceVideoUrl} onChange={(event) => setFormValue("sourceVideoUrl", event.target.value, setForm)} placeholder="输入可直接访问的视频 URL" />
            </label>
          </>
        )}
        {form.workflowType !== "video_storyboard_ad" && (
          <>
            <label className="upload-zone">
              <UploadCloud size={42} />
              <strong>{imageFile ? imageFile.name : (form.imageFileName || "拖拽产品图片至此")}</strong>
              <span>支持 PNG, JPG, WEBP 或 AVIF，上传后保存到 S3 兼容对象存储（默认 7 天过期）</span>
              <input type="file" accept="image/*" onChange={(event) => setImageFile(event.target.files?.[0] ?? null)} />
            </label>
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
            <p>确认平台、时长、比例和自动化策略。</p>
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
          <span className="field-label">视觉风格</span>
          <div className="style-tags">
            {["产品特写", "真实生活方式", "测评口播", "场景种草", "促销转化"].map((style) => (
              <button type="button" key={style} className={form.style.includes(style) ? "active" : ""} onClick={() => setFormValue("style", style, setForm)}>
                {style}
              </button>
            ))}
          </div>
          <textarea value={form.style} onChange={(event) => setFormValue("style", event.target.value, setForm)} placeholder="输入自定义风格描述..." />
        </div>
        <div className="summary-flags">
          <label className="summary-flag-toggle">
            <span>图片评分</span>
            <input
              type="checkbox"
              checked={form.imageScoringEnabled}
              onChange={(event) => setForm((previous) => ({ ...previous, imageScoringEnabled: event.target.checked }))}
            />
          </label>
          <label className="summary-flag-toggle">
            <span>视频评分</span>
            <input
              type="checkbox"
              checked={form.videoScoringEnabled}
              onChange={(event) => setForm((previous) => ({ ...previous, videoScoringEnabled: event.target.checked }))}
            />
          </label>
          <label className="summary-flag-toggle">
            <span>自动确认</span>
            <input
              type="checkbox"
              checked={form.autoConfirmEnabled}
              onChange={(event) => setForm((previous) => ({ ...previous, autoConfirmEnabled: event.target.checked }))}
            />
          </label>
        </div>
        <div className="submit-summary">
          <span>素材状态<b>{sourceReady ? "已提供" : "待补充"}</b></span>
          <span>工作流<b>{selectedWorkflow.shortLabel}</b></span>
          <span>输出规格<b>{platformLabel(form.platform)} · {form.duration || "-"}s · {form.aspectRatio}</b></span>
        </div>
        {message && <div className="message">{message}</div>}
        <button className="primary big" disabled={busy}>
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

  useEffect(() => {
    setViewStage(canonicalStage(task.stage));
  }, [task.taskId, task.stage]);

  return (
    <div className="workflow-page">
      <section className="workflow-head">
        <div>
          <div className="headline-row">
            <h2>{viewStage === "COMPLETED" ? finalTitle(task) : stageText(task.workflowType, viewStage)}</h2>
            <span className={`status ${task.status.toLowerCase()}`}>{statusText(task.status)}</span>
          </div>
          <p>任务 ID: <span>{task.taskId}</span></p>
        </div>
        <div className="workflow-actions">
          <button onClick={props.onSaveEdits} disabled={props.busy || readOnly || !canEdit(task.stage)}>保存草稿</button>
          <button className="primary" onClick={props.onAdvance} disabled={props.busy || task.status === "RUNNING" || task.status === "SUCCESS"}>
            {props.busy ? <Loader2 className="spin" size={18} /> : <Rocket size={18} />}
            {nextLabel(task.workflowType, task.stage)}
          </button>
          <button type="button" onClick={() => setRegenerateOpen(true)} disabled={props.busy || task.status === "RUNNING"}>
            <RotateCcw size={18} />
            重新生成
          </button>
        </div>
      </section>
      <TaskSummaryBar task={task} viewingStage={viewStage} />
      <StageStepper current={task.stage} viewing={viewStage} status={task.status} task={task} onSelect={setViewStage} items={workflowStages} />
      {props.message && <div className="message">{props.message}</div>}
      {hasTaskError(task) && <ErrorPanel task={task} />}
      {readOnly && <div className="message">当前正在查看历史节点内容，编辑和保存操作只在当前流程节点开放。</div>}
      <section className="stage-canvas">
        <StageContent {...props} viewStage={viewStage} readOnly={readOnly} />
      </section>
      {regenerateOpen && (
        <RegenerateDrawer onClose={() => setRegenerateOpen(false)}>
          <RegenerateControls {...props} />
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
function TaskSummaryBar({ task, viewingStage }: { task: TaskDetail; viewingStage: TaskStage }) {
  const flags = [
    task.request?.imageScoringEnabled ? "图片评分" : "",
    task.request?.videoScoringEnabled ? "视频评分" : "",
    task.request?.autoConfirmEnabled ? "自动确认" : ""
  ].filter(Boolean);
  return (
    <section className="task-summary-bar">
      <span>工作流<b>{workflowLabel(task.workflowType)}</b></span>
      <span>当前阶段<b>{stageText(task.workflowType, task.stage)}</b></span>
      <span>正在查看<b>{stageText(task.workflowType, viewingStage)}</b></span>
      <span>规格<b>{platformLabel(task.request?.platform ?? task.videoConfig?.platform)} · {task.request?.duration ?? task.videoConfig?.duration ?? "-"}s · {task.request?.aspectRatio ?? task.videoConfig?.aspectRatio ?? "-"}</b></span>
      <span>更新时间<b>{formatDateTime(task.updatedAt)}</b></span>
      <span>策略<b>{flags.length > 0 ? flags.join(" / ") : "人工确认"}</b></span>
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

function ErrorPanel({ task }: { task: TaskDetail }) {
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
      <button type="button" onClick={() => navigator.clipboard.writeText(errorText)}>
        <Copy size={16} />
        复制错误
      </button>
    </section>
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
  if (viewStage === "MARKET_PLANNING") return <MarketingStage task={task} />;
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

function MarketingStage({ task }: { task: TaskDetail }) {
  const config = task.videoConfig;
  if (!config) return <div className="empty-state">等待生成营销策划。</div>;
  return (
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
  );
}

function ShotStage({ task, editableShots, setEditableShots, readOnly }: StageViewProps) {
  function updateShot(shotId: string, key: "prompt" | "action" | "words", value: string) {
    if (readOnly) return;
    setEditableShots(editableShots.map((shot) => shot.shotId === shotId ? { ...shot, [key]: value } : shot));
  }
  return (
    <div className="two-pane">
      <section className="panel-card">
        <div className="section-head"><h3>分镜序列</h3><span>{editableShots.length} 个分镜</span></div>
        <div className="shot-list">
          {editableShots.map((shot) => (
            <article className="shot-card" key={shot.shotId}>
              <header><b>{shot.shotId}</b><span>时长: {shot.duration}s</span></header>
              <label>视觉提示词<textarea readOnly={readOnly} value={shot.prompt ?? ""} onChange={(event) => updateShot(shot.shotId, "prompt", event.target.value)} /></label>
              <label>动作 / 移动<input readOnly={readOnly} value={shot.action ?? ""} onChange={(event) => updateShot(shot.shotId, "action", event.target.value)} /></label>
              <label>对白 / 旁白<textarea readOnly={readOnly} value={shot.words ?? ""} onChange={(event) => updateShot(shot.shotId, "words", event.target.value)} /></label>
            </article>
          ))}
        </div>
      </section>
      <aside className="panel-card">
        <h3>编辑范围</h3>
        <div className="summary-list">
          <span>当前流程<b>{stageText(task.workflowType, task.stage)}</b></span>
          <span>任务状态<b>{statusText(task.status)}</b></span>
          <span>{readOnly ? "查看模式" : "可编辑字段"}<b>{readOnly ? "历史节点只读" : "prompt / action / words"}</b></span>
        </div>
        <p className="hint">保存后会保留分镜 ID、顺序、时长和参考素材，只用修改后的提示词重新生成后续图片和视频。</p>
      </aside>
    </div>
  );
}

function VideoUnderstandingStage({ task, editableShots, setEditableShots, readOnly }: StageViewProps) {
  function updateShot(shotId: string, key: "prompt" | "action" | "words" | "reference", value: string) {
    if (readOnly) return;
    setEditableShots(editableShots.map((shot) => shot.shotId === shotId ? { ...shot, [key]: value } : shot));
  }

  async function uploadReference(shotId: string, file: File | null) {
    if (!file || readOnly) return;
    const reference = await fileToJpegDataUrl(file);
    updateShot(shotId, "reference", reference);
  }

  return (
    <div className="two-pane">
      <section className="panel-card marketing-plan-card">
        <h3>视频理解结果</h3>
        <label>素材标题<input readOnly value={task.videoConfig?.productInfo?.name ?? task.request?.sourceVideoFileName ?? "视频素材"} /></label>
        <label>源视频链接<textarea readOnly value={task.request?.sourceVideoUrl ?? resolveLegacyVideoUrl(task.request)} /></label>
        <label>理解说明<textarea className="marketing-advice" readOnly value={task.videoConfig?.videoAdvice ?? ""} /></label>
      </section>
      <section className="panel-card">
        <div className="section-head"><h3>视频总结分镜</h3><span>{editableShots.length} 个分镜</span></div>
        <div className="shot-list">
          {editableShots.map((shot) => (
            <article className="shot-card" key={shot.shotId}>
              <header><b>{shot.shotId}</b><span>时长: {shot.duration}s</span></header>
              <label>画面总结<textarea readOnly={readOnly} value={shot.prompt ?? ""} onChange={(event) => updateShot(shot.shotId, "prompt", event.target.value)} /></label>
              <label>镜头动作<input readOnly={readOnly} value={shot.action ?? ""} onChange={(event) => updateShot(shot.shotId, "action", event.target.value)} /></label>
              <label>口播 / 字幕<textarea readOnly={readOnly} value={shot.words ?? ""} onChange={(event) => updateShot(shot.shotId, "words", event.target.value)} /></label>
              <div className="shot-reference-field">
                <span>分镜参考图</span>
                {isRenderableImage(shot.reference) ? (
                  <img className="shot-reference-preview" src={shot.reference} alt={`${shot.shotId}-reference`} />
                ) : (
                  <div className="shot-reference-empty">
                    {shot.reference ? "当前参考图不可预览" : "未上传参考图，将只按当前分镜内容生成候选图片"}
                  </div>
                )}
                {!readOnly && (
                  <label className="shot-upload-button">
                    <input
                      type="file"
                      accept="image/*"
                      onChange={(event) => void uploadReference(shot.shotId, event.target.files?.[0] ?? null)}
                    />
                    <span>{shot.reference ? "替换参考图" : "上传参考图"}</span>
                  </label>
                )}
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

function ImageGenerateStage({ task, selectedImages, setSelectedImages, onSaveSelections, readOnly }: StageViewProps) {
  const missingCount = countMissingSelections(task.imageGroups, selectedImages, "images");
  return (
    <div className="panel-card">
      <div className="section-head"><h3>生成图片候选</h3><span>{missingCount === 0 ? "已完成选择" : `${missingCount} 组待选择`}</span></div>
      <MediaGrid groups={task.imageGroups} selected={selectedImages} onSelect={setSelectedImages} type="image" readOnly={readOnly} />
      <button className="secondary" onClick={onSaveSelections} disabled={readOnly}>保存选择</button>
    </div>
  );
}

function ImageEvaluateStage({ task, selectedImages, setSelectedImages, imageScoresJson, setImageScoresJson, onSaveSelections, onSaveEdits, readOnly }: StageViewProps) {
  return (
    <div className="review-layout">
      <section className="panel-card">
        <div className="section-head"><h3>选定分镜图片 (Shot List)</h3><span>共 {task.scoredImageGroups.reduce((sum, group) => sum + group.images.length, 0)} 张图片已生成</span></div>
        <MediaGrid groups={task.scoredImageGroups} selected={selectedImages} onSelect={setSelectedImages} type="image" readOnly={readOnly} />
      </section>
      <aside className="panel-card review-panel">
        <h3>评分数据编辑</h3>
        <p className="hint">默认使用结构化表格编辑评分、原因和选中状态，原始 JSON 保留在高级区。</p>
        <ScoreEditor
          mode="image"
          value={imageScoresJson}
          readOnly={readOnly}
          onChange={setImageScoresJson}
          onSelect={setSelectedImages}
        />
        <div className="split-actions">
          <button onClick={onSaveSelections} disabled={readOnly}>保存选择</button>
          <button className="secondary" onClick={onSaveEdits} disabled={readOnly}>应用更改</button>
        </div>
      </aside>
    </div>
  );
}

function VideoGenerateStage({ task, selectedVideos, setSelectedVideos, onSaveSelections, readOnly }: StageViewProps) {
  const missingCount = countMissingSelections(task.videoGroups, selectedVideos, "videos");
  return (
    <div className="panel-card">
      <div className="section-head"><h3>分镜视频候选</h3><span>{missingCount === 0 ? "已完成选择" : `${missingCount} 组待选择`}</span></div>
      <MediaGrid groups={task.videoGroups} selected={selectedVideos} onSelect={setSelectedVideos} type="video" readOnly={readOnly} />
      <button className="secondary" onClick={onSaveSelections} disabled={readOnly}>保存选择</button>
    </div>
  );
}

function VideoEvaluateStage({ task, selectedVideos, setSelectedVideos, videoScoresJson, setVideoScoresJson, onSaveSelections, onSaveEdits, readOnly }: StageViewProps) {
  return (
    <div className="review-layout">
      <section className="panel-card">
        <div className="section-head"><h3>视频候选评审</h3><span>{task.scoredVideoGroups.length} 组分镜</span></div>
        <MediaGrid groups={task.scoredVideoGroups} selected={selectedVideos} onSelect={setSelectedVideos} type="video" readOnly={readOnly} />
      </section>
      <aside className="panel-card review-panel">
        <h3>评分数据编辑</h3>
        <p className="hint">默认使用结构化表格编辑评分、原因和选中状态，原始 JSON 保留在高级区。</p>
        <ScoreEditor
          mode="video"
          value={videoScoresJson}
          readOnly={readOnly}
          onChange={setVideoScoresJson}
          onSelect={setSelectedVideos}
        />
        <div className="split-actions">
          <button onClick={onSaveSelections} disabled={readOnly}>保存选择</button>
          <button className="secondary" onClick={onSaveEdits} disabled={readOnly}>应用更改</button>
        </div>
      </aside>
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

/**
 * 功能描述：用结构化表单编辑图片或视频评分草稿，并同步当前分镜的选中素材。
 * 参数解释：mode 表示评分对象类型；value 表示评分 JSON 字符串；readOnly 表示是否只读；onChange 用于回写 JSON；onSelect 用于同步选中素材。
 * 返回对象描述：返回分镜评分编辑表单的 React 节点。
 * 可能抛出的异常：无；当 JSON 无法解析时返回错误提示。
 */
function ScoreEditor({
  mode,
  value,
  readOnly,
  onChange,
  onSelect
}: {
  mode: ScoreEditorMode;
  value: string;
  readOnly: boolean;
  onChange: (value: string) => void;
  onSelect: (value: Record<string, string>) => void;
}) {
  const assetKey = mode === "image" ? "images" : "videos";
  const groups = parseScoreDraft(value);
  if (!groups) {
    return (
      <div className="score-editor">
        <div className="message">评分 JSON 无法解析，请在高级区修正格式。</div>
        <details className="json-details" open>
          <summary>原始 JSON</summary>
          <textarea readOnly={readOnly} value={value} onChange={(event) => onChange(event.target.value)} />
        </details>
      </div>
    );
  }

  const updateAsset = (shotId: string, assetId: string, patch: Partial<ScoreDraftAsset>) => {
    const nextGroups = groups.map((group) => {
      if (group.shotId !== shotId) return group;
      const assets = group[assetKey] as ScoreDraftAsset[];
      return {
        ...group,
        [assetKey]: assets.map((asset) => asset.assetId === assetId ? { ...asset, ...patch } : asset)
      };
    });
    onChange(JSON.stringify(nextGroups, null, 2));
  };

  const chooseAsset = (shotId: string, assetId: string) => {
    const nextGroups = groups.map((group) => {
      if (group.shotId !== shotId) return group;
      const assets = group[assetKey] as ScoreDraftAsset[];
      return {
        ...group,
        [assetKey]: assets.map((asset) => ({ ...asset, selected: asset.assetId === assetId }))
      };
    });
    onChange(JSON.stringify(nextGroups, null, 2));
    onSelect(selectedFromScoreGroups(nextGroups, assetKey));
  };

  return (
    <div className="score-editor">
      <div className="score-table">
        {groups.map((group) => {
          const assets = group[assetKey] as ScoreDraftAsset[];
          return (
            <section className="score-group" key={group.shotId}>
              <header>
                <b>{group.shotId}</b>
                <span>{assets.length} 个候选 · {assets.some((asset) => asset.selected) ? "已选择" : "待选择"}</span>
              </header>
              {assets.map((asset) => (
                <div className="score-row" key={asset.assetId}>
                  <label className="score-radio">
                    <input
                      type="radio"
                      name={`score-${mode}-${group.shotId}`}
                      checked={Boolean(asset.selected)}
                      disabled={readOnly}
                      onChange={() => chooseAsset(group.shotId, asset.assetId)}
                    />
                    <span>{asset.id}</span>
                  </label>
                  <input
                    type="number"
                    min={0}
                    max={100}
                    value={asset.score ?? ""}
                    readOnly={readOnly}
                    placeholder="评分"
                    onChange={(event) => updateAsset(group.shotId, asset.assetId, { score: Number(event.target.value || 0) })}
                  />
                  <input
                    value={asset.reason ?? ""}
                    readOnly={readOnly}
                    placeholder="评分原因"
                    onChange={(event) => updateAsset(group.shotId, asset.assetId, { reason: event.target.value })}
                  />
                </div>
              ))}
            </section>
          );
        })}
      </div>
      <details className="json-details">
        <summary>高级：查看原始 JSON</summary>
        <textarea readOnly={readOnly} value={value} onChange={(event) => onChange(event.target.value)} />
      </details>
    </div>
  );
}

function MediaGrid({
  groups,
  selected,
  onSelect,
  type,
  readOnly = false
}: {
  groups: Array<ShotImageGroup | ShotVideoGroup>;
  selected: Record<string, string>;
  onSelect: (value: Record<string, string>) => void;
  type: "image" | "video";
  readOnly?: boolean;
}) {
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
              {assets.map((asset) => (
                <button
                  type="button"
                  className={`media-card ${selected[group.shotId] === asset.assetId ? "selected" : ""}`}
                  key={asset.assetId}
                  onClick={() => {
                    if (!readOnly) onSelect({ ...selected, [group.shotId]: asset.assetId });
                  }}
                >
                  {type === "image" && isRenderableImage(asset.url) ? <img src={asset.url} alt={asset.assetId} /> : null}
                  {type === "video" && isRenderableVideo(asset.url) ? <video src={asset.url} controls muted /> : null}
                  {type === "image" && !isRenderableImage(asset.url) && <div className="mock-media">{asset.url}</div>}
                  {type === "video" && !isRenderableVideo(asset.url) && <div className="mock-media">{asset.url}</div>}
                  <span>{asset.score ?? "-"} 分</span>
                  <small>{asset.reason}</small>
                  {selected[group.shotId] === asset.assetId && (
                    <>
                      <b className="checkmark"><Check size={18} /></b>
                      <em className="selected-label">已选择</em>
                    </>
                  )}
                </button>
              ))}
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
  setRegenerateStage,
  setRegenerateReason,
  setRegenerateDraft,
  onRegenerate,
  busy
}: WorkflowViewProps) {
  const updateTaskInput = (patch: Partial<TaskRequest>) => setRegenerateDraft({ ...regenerateDraft, taskInput: { ...regenerateDraft.taskInput, ...patch } });
  const updateVideoConfig = (patch: Partial<VideoConfig>) => setRegenerateDraft({ ...regenerateDraft, videoConfig: { ...regenerateDraft.videoConfig, ...patch } });
  const updateProductInfo = (patch: Partial<ProductInfo>) => setRegenerateDraft({
    ...regenerateDraft,
    videoConfig: {
      ...regenerateDraft.videoConfig,
      productInfo: { ...regenerateDraft.videoConfig.productInfo, ...patch }
    }
  });
  const updateShot = (shotId: string, patch: Partial<Shot>) => setRegenerateDraft({
    ...regenerateDraft,
    shots: regenerateDraft.shots.map((shot) => shot.shotId === shotId ? { ...shot, ...patch } : shot)
  });
  const updateImageGroup = (shotId: string, patch: Partial<ShotImageGroup>) => setRegenerateDraft({
    ...regenerateDraft,
    imageGroups: regenerateDraft.imageGroups.map((group) => group.shotId === shotId ? { ...group, ...patch } : group)
  });
  const chooseRegenerateImage = (shotId: string, assetId: string) => setRegenerateDraft({
    ...regenerateDraft,
    selectedImages: { ...regenerateDraft.selectedImages, [shotId]: assetId }
  });
  const uploadShotReference = async (shotId: string, file: File | null) => {
    if (!file) return;
    const reference = await fileToJpegDataUrl(file);
    updateShot(shotId, { reference });
  };
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
          <select value={regenerateStage} onChange={(event) => setRegenerateStage(event.target.value as TaskStage)}>
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
          </section>
        )}
        {regenerateStage === "IMAGE_GENERATING" && (
          <section className="regen-section">
            <div className="metric-row">
              <label>候选图片数量<input type="number" min={1} max={10} value={regenerateDraft.taskInput.generateImageCount ?? 1} onChange={(event) => updateTaskInput({ generateImageCount: Number(event.target.value || 1) })} /></label>
              <label>画面比例<select value={regenerateDraft.taskInput.aspectRatio ?? "9:16"} onChange={(event) => updateTaskInput({ aspectRatio: event.target.value })}>{aspectRatioOptions.map((ratio) => <option key={ratio.value} value={ratio.value}>{ratio.label}</option>)}</select></label>
            </div>
            <div className="regen-shot-list">
              {regenerateDraft.shots.map((shot) => (
                <div className="regen-shot" key={shot.shotId}>
                  <b>{shot.shotId}</b>
                  <input type="number" min={1} value={shot.duration ?? 5} onChange={(event) => updateShot(shot.shotId, { duration: Number(event.target.value || 5) })} />
                  <textarea value={shot.prompt} onChange={(event) => updateShot(shot.shotId, { prompt: event.target.value })} />
                  <input value={shot.action} onChange={(event) => updateShot(shot.shotId, { action: event.target.value })} />
                  <input value={shot.words} onChange={(event) => updateShot(shot.shotId, { words: event.target.value })} />
                  {task.workflowType === "video_storyboard_ad" && (
                    <div className="regen-shot-reference">
                      <span>分镜参考图</span>
                      {isRenderableImage(shot.reference) ? (
                        <img src={shot.reference} alt={`${shot.shotId}-regen-reference`} />
                      ) : (
                        <div className="regen-shot-reference-empty">未上传参考图，将只按当前分镜文案生成候选图片</div>
                      )}
                      <label className="shot-upload-button">
                        <input
                          type="file"
                          accept="image/*"
                          onChange={(event) => void uploadShotReference(shot.shotId, event.target.files?.[0] ?? null)}
                        />
                        <span>{shot.reference ? "替换参考图" : "上传参考图"}</span>
                      </label>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}
        {regenerateStage === "VIDEO_GENERATING" && (
          <section className="regen-section">
            <div className="metric-row">
              <label>候选视频数量<input type="number" min={1} max={5} value={regenerateDraft.taskInput.generateVideoCount ?? 1} onChange={(event) => updateTaskInput({ generateVideoCount: Number(event.target.value || 1) })} /></label>
              <label>视频比例<select value={regenerateDraft.taskInput.aspectRatio ?? "9:16"} onChange={(event) => updateTaskInput({ aspectRatio: event.target.value })}>{aspectRatioOptions.map((ratio) => <option key={ratio.value} value={ratio.value}>{ratio.label}</option>)}</select></label>
            </div>
            {task.workflowType === "video_storyboard_ad" ? (
              <div className="regen-video-shot-list">
                {regenerateDraft.imageGroups.map((group) => {
                  const selectedAssetId = regenerateDraft.selectedImages[group.shotId] ?? group.images.find((item) => item.selected)?.assetId ?? group.images[0]?.assetId ?? "";
                  return (
                    <div className="regen-video-shot" key={group.shotId}>
                      <div className="regen-video-shot-head">
                        <b>{group.shotId}</b>
                        <span>{group.duration ?? "-"} 秒 · {group.images.length} 张候选图</span>
                      </div>
                      <div className="regen-video-shot-body">
                        <div className="regen-video-shot-editor">
                          <label>分镜视频时长（秒）<input type="number" min={1} max={120} value={group.duration ?? 5} onChange={(event) => updateImageGroup(group.shotId, { duration: Number(event.target.value || 5) })} /></label>
                          <label>视频画面提示<textarea value={group.prompt} onChange={(event) => updateImageGroup(group.shotId, { prompt: event.target.value })} /></label>
                          <label>镜头动作<input value={group.action} onChange={(event) => updateImageGroup(group.shotId, { action: event.target.value })} /></label>
                          <label>口播 / 字幕<textarea value={group.words} onChange={(event) => updateImageGroup(group.shotId, { words: event.target.value })} /></label>
                        </div>
                        <div className="regen-video-shot-assets">
                          {group.images.map((image) => {
                            const selected = selectedAssetId === image.assetId;
                            return (
                              <button
                                type="button"
                                key={image.assetId}
                                className={`regen-image-pick ${selected ? "selected" : ""}`}
                                onClick={() => chooseRegenerateImage(group.shotId, image.assetId)}
                              >
                                {isRenderableImage(image.url) ? (
                                  <img src={image.url} alt={image.assetId} />
                                ) : (
                                  <div className="mock-media">{image.url}</div>
                                )}
                                <span>{image.score ?? "-"} 分</span>
                                {selected && <em>当前用于生成分镜视频</em>}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <MediaGrid groups={draftImageGroups(regenerateDraft)} selected={regenerateDraft.selectedImages} onSelect={(value) => setRegenerateDraft({ ...regenerateDraft, selectedImages: value })} type="image" />
            )}
          </section>
        )}
        {regenerateStage === "FINAL_COMPOSING" && (
          <section className="regen-section">
            <MediaGrid groups={draftVideoGroups(regenerateDraft)} selected={regenerateDraft.selectedVideos} onSelect={(value) => setRegenerateDraft({ ...regenerateDraft, selectedVideos: value })} type="video" />
          </section>
        )}
      </div>
      <div className="regen-actions">
        <button onClick={onRegenerate} disabled={busy}>应用重燃</button>
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

function regeneratePayload(stage: TaskStage, draft: RegenerateDraft) {
  if (stage === "MARKET_PLANNING") {
    return { taskInput: draft.taskInput };
  }
  if (stage === "SHOT_SCRIPT_GENERATING") {
    return { taskInput: draft.taskInput, videoConfig: draft.videoConfig };
  }
  if (stage === "IMAGE_GENERATING") {
    return { taskInput: draft.taskInput, shots: draft.shots };
  }
  if (stage === "VIDEO_GENERATING") {
    return {
      taskInput: draft.taskInput,
      selectedImages: selectedImagesFromDraft(draft)
    };
  }
  if (stage === "FINAL_COMPOSING") {
    return {
      selectedVideos: selectedVideosFromDraft(draft)
    };
  }
  return {};
}

function selectedImagesFromDraft(draft: RegenerateDraft): SelectedImage[] {
  return Object.entries(draft.selectedImages)
    .flatMap(([shotId, assetId]) => {
      const group = draftImageGroups(draft).find((item) => item.shotId === shotId);
      const image = group?.images.find((item) => item.assetId === assetId);
      return group && image ? [{ shotId, duration: group.duration, image, prompt: group.prompt, action: group.action, words: group.words }] : [];
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

function toImageScoreDraft(groups: ShotImageGroup[], selected: Record<string, string>): ImageScoreDraftGroup[] {
  return groups.map((group) => ({
    shotId: group.shotId,
    prompt: group.prompt,
    action: group.action,
    words: group.words,
    images: group.images.map((image) => ({
      assetId: image.assetId,
      id: image.id,
      score: normalizeScore(image.score),
      reason: image.reason,
      selected: selected[group.shotId] === image.assetId || image.selected
    }))
  }));
}

function toVideoScoreDraft(groups: ShotVideoGroup[], selected: Record<string, string>): VideoScoreDraftGroup[] {
  return groups.map((group) => ({
    shotId: group.shotId,
    prompt: group.prompt,
    action: group.action,
    words: group.words,
    videos: group.videos.map((video) => ({
      assetId: video.assetId,
      id: video.id,
      score: normalizeScore(video.score),
      reason: video.reason,
      selected: selected[group.shotId] === video.assetId || video.selected
    }))
  }));
}

function applyImageScoreDraft(groups: ShotImageGroup[], drafts: ImageScoreDraftGroup[]): ShotImageGroup[] {
  return groups.map((group) => {
    const draft = drafts.find((item) => item.shotId === group.shotId);
    if (!draft) return group;
    return {
      ...group,
      prompt: draft.prompt ?? group.prompt,
      action: draft.action ?? group.action,
      words: draft.words ?? group.words,
      images: group.images.map((image) => {
        const edited = draft.images?.find((item) => item.assetId === image.assetId);
        return edited ? { ...image, score: edited.score, reason: edited.reason, selected: edited.selected ?? false } : image;
      })
    };
  });
}

function applyVideoScoreDraft(groups: ShotVideoGroup[], drafts: VideoScoreDraftGroup[]): ShotVideoGroup[] {
  return groups.map((group) => {
    const draft = drafts.find((item) => item.shotId === group.shotId);
    if (!draft) return group;
    return {
      ...group,
      prompt: draft.prompt ?? group.prompt,
      action: draft.action ?? group.action,
      words: draft.words ?? group.words,
      videos: group.videos.map((video) => {
        const edited = draft.videos?.find((item) => item.assetId === video.assetId);
        return edited ? { ...video, score: edited.score, reason: edited.reason, selected: edited.selected ?? false } : video;
      })
    };
  });
}

function markSelectedInScoreJson(previous: string, selected: Record<string, string>, key: "images" | "videos") {
  try {
    const groups = JSON.parse(previous) as Array<Record<string, unknown>>;
    return JSON.stringify(groups.map((group) => {
      const shotId = String(group.shotId ?? "");
      const assets = Array.isArray(group[key]) ? group[key] as ScoreDraftAsset[] : [];
      return {
        ...group,
        [key]: assets.map((asset) => ({
          ...asset,
          selected: selected[shotId] === asset.assetId
        }))
      };
    }), null, 2);
  } catch {
    return previous;
  }
}

/**
 * 功能描述：解析评分草稿 JSON，并统一为评分编辑器可消费的数据结构。
 * 参数解释：value 表示当前评分草稿 JSON 字符串。
 * 返回对象描述：解析成功时返回评分分组数组，解析失败时返回 null。
 * 可能抛出的异常：无；内部捕获 JSON 解析异常。
 */
function parseScoreDraft(value: string): ScoreDraftGroup[] | null {
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) return null;
    return parsed.filter((item): item is ScoreDraftGroup => {
      if (!item || typeof item !== "object") return false;
      const group = item as ScoreDraftGroup;
      return typeof group.shotId === "string" && (Array.isArray(group.images) || Array.isArray(group.videos));
    });
  } catch {
    return null;
  }
}

/**
 * 功能描述：从评分分组中提取每个分镜当前选中的素材 ID。
 * 参数解释：groups 表示评分分组数组；assetKey 表示候选素材字段名称。
 * 返回对象描述：返回以分镜 ID 为键、素材 ID 为值的选择映射。
 * 可能抛出的异常：无。
 */
function selectedFromScoreGroups(groups: ScoreDraftGroup[], assetKey: "images" | "videos") {
  return Object.fromEntries(groups.flatMap((group) => {
    const selectedAsset = group[assetKey]?.find((asset) => asset.selected);
    return selectedAsset ? [[group.shotId, selectedAsset.assetId]] : [];
  }));
}

/**
 * 功能描述：统计候选素材分组中还没有完成选择的分镜数量。
 * 参数解释：groups 表示候选素材分组；selected 表示当前选择映射；assetKey 表示候选素材字段名称。
 * 返回对象描述：返回待选择的分镜数量。
 * 可能抛出的异常：无。
 */
function countMissingSelections(groups: Array<ShotImageGroup | ShotVideoGroup>, selected: Record<string, string>, assetKey: "images" | "videos") {
  return groups.filter((group) => {
    const assets = assetKey === "images" && "images" in group ? group.images : "videos" in group ? group.videos : [];
    return assets.length > 0 && !selected[group.shotId];
  }).length;
}

function normalizeScore(value: unknown) {
  if (typeof value === "number") return value;
  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
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

function PlayIcon() {
  return <span className="play-icon">▶</span>;
}

createRoot(document.getElementById("root")!).render(<App />);
