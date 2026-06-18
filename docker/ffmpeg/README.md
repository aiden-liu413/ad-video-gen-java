# FFmpeg 本地构建包

Docker 构建使用 Linux x64 GPL 版本的 FFmpeg 本地压缩包，不在镜像构建阶段联网安装。

将安装包放到当前目录，并保持文件名为：

```text
ffmpeg-master-latest-linux64-gpl.tar.xz
```

当前已验证安装包：

```text
SHA-256: adbde5379c9aeed6919b6fa8f837de76abeae5e4b18b21e345926d717f33ab4f
架构: Linux x86_64
```

压缩包体积较大，已通过项目 `.gitignore` 排除，不提交到 Git。
