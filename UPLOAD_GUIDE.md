# 上传到 GitHub 并自动生成 APK 指南

这份包已经内置了 GitHub Actions 自动编译配置（`.github/workflows/build-apk.yml`）。
你只需要把它完整上传到你刚创建的 GitHub 仓库，服务器就会自动帮你编出 APK。

## 上传步骤（网页拖拽，不用装 Git）

1. 打开你的仓库页面 `https://github.com/superjnn/jnn`
2. 点击仓库里的 **Add file** → **Upload files**
3. 把**这个文件夹里的所有内容**（README.md、build.gradle、settings.gradle、
   gradlew、gradlew.bat、gradle.properties、.gitignore、.github 文件夹、
   app 文件夹）**全部选中拖进去**
4. 注意：不要只传 `app` 或只传 `.github`，必须**全部文件一起上传**
5. 底部 Commit changes 填写 "Initial commit"，点 Commit

## 触发自动编译

上传完成后：

1. 点仓库顶部的 **Actions** 标签
2. 左侧会看到 **Build Debug APK** 工作流
3. 点进去，再点右上角的 **Run workflow** → **Run workflow**
4. 等约 3~5 分钟，显示绿色 ✅ 就成功了
5. 点进这次运行，页面底部 **Artifacts** 区域下载 `FineVolume-debug-apk`
6. 解压 zip，里面就是 `app-debug.apk`，装到手机即可

## 如果 Actions 变红失败

点进红色的运行记录，看日志里红色报错部分，截图发给我，我帮你排查。
最常见的原因是 `android-actions/setup-android` 拉 SDK 慢，多试一次通常就好。

## 安装到手机

1. 把 `app-debug.apk` 传到手机并安装
2. 打开 App，确认三段映射（默认 0.6 / 0.8 / 2.0）
3. 点「保存并启用后台拦截」
4. 到 **系统设置 → 无障碍 → 精细音量 → 开启**
5. 按物理音量键测试分段步进效果
