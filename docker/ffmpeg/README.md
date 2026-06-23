# FFmpeg Docker 构建包

仓库已提交 Linux x64 FFmpeg 压缩包：

[`ffmpeg.zip`](ffmpeg.zip)

**构建镜像前**解压为 `docker/ffmpeg/ffmpeg`（该文件在 `.gitignore` 中，不入库）：

```bash
unzip -o -j docker/ffmpeg/ffmpeg.zip ffmpeg -d docker/ffmpeg/
chmod +x docker/ffmpeg/ffmpeg
```

然后执行 `docker build`。
