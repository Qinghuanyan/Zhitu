备份时间: 2026-05-20

目的:
- 备份切换前项目使用的旧 HTML 入口文件
- 为后续对比 UI 迁移差异保留原始版本

备份文件:
- Zhitu.html
- Zhitu2.0.html

当前切换后的项目入口:
- ai_travel_planner.html

当前接线位置:
- web-ui/vite.config.ts
- app/build.gradle.kts

说明:
- Android 侧仍会把当前入口文件重命名为 android asset: zhitu.html
- 旧文件暂不删除，避免后续需要回看结构、动作语义或视觉差异
