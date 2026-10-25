# NavenAlpha Client

这是一个基于 Minecraft Forge 的客户端模组项目。

## 👥 作者与社区

- **作者**: jiuxian_baka
- **交流群**: [Skidder Team 2k26](https://qm.qq.com/q/oyDLXAUeoo) (点击链接加入)

## ⚖️ 许可证

本项目采用 **MIT License** 开源。
这意味着你可以随意修改、分发，但别以此为借口写烂代码。

## 🛠️ 删验证指南 (DRM Removal)

**警告**：原始代码包含大量极其愚蠢的、硬编码的 DRM 验证（Base64 混淆的 `System.exit`）。这不仅拖慢性能，而且毫无安全性可言。

如果你想正常开发或使用，**必须**执行以下清理步骤：

1.  **清理核心入口 (`Naven.java`)**
    打开 `src/main/java/com/heypixel/heypixelmod/obsoverlay/Naven.java`。
    你会发现大量的 Getter 方法（如 `getEventManager`, `getValueManager` 等）中包含如下垃圾代码：
    ```java
    if (AuthUtils.transport == null || AuthUtils.authed.get().length() != 32) {
        try {
            // ... 解码 Base64 并调用 System.exit(0) ...
        } catch (Exception ex) {}
    }
    ```
    **操作**：全选这些 `if` 块，直接删除。它们是纯粹的性能毒瘤。

2.  **阉割 `AuthUtils`**
    定位到 `com.heypixel.heypixelmod.obsoverlay.utils.auth` 包。
    **操作**：将验证逻辑替换为始终返回 `true` 或空操作。不需要去连接那个可能已经不存在的验证服务器。

3.  **净化 `ModuleManager`**
    打开 `src/main/java/com/heypixel/heypixelmod/obsoverlay/modules/ModuleManager.java`。
    **操作**：找到初始化的 `b(...)` 方法，清理掉里面的脏话参数。代码应该保持专业，不要像个发脾气的小孩。

## 🏗️ 构建指南 (Build)

本项目使用 Gradle 构建。请确保已安装 **JDK 17**。

### Windows
```powershell
./gradlew build
```

### Linux / macOS
```bash
chmod +x gradlew
./gradlew build
```

构建成功后，文件将生成在 `build/libs/` 目录下。

## 🚀 使用方法

1.  确保你已安装对应版本的 Minecraft Forge。
2.  将构建生成的 `.jar` 文件放入 `.minecraft/mods` 文件夹。
3.  启动游戏。

## 🤝 贡献流程

如果你想改进这个项目（即使只是清理掉那些糟糕的缩进）：

1.  Fork 本仓库。
2.  创建你的特性分支 (`git checkout -b feature/AmazingFeature`)。
4.  提交更改 (`git commit -m 'Add some AmazingFeature'`)。
5.  推送到分支 (`git push origin feature/AmazingFeature`)。
6.  提交 Pull Request。
