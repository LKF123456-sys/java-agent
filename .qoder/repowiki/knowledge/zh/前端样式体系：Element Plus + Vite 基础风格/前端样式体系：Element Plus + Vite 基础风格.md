---
kind: frontend_style
name: 前端样式体系：Element Plus + Vite 基础风格
category: frontend_style
scope:
    - '**'
source_files:
    - frontend/package.json
    - frontend/src/main.js
    - frontend/vite.config.js
    - frontend/src/assets/styles/global.css
    - frontend/src/App.vue
---

## 1. 系统/方法概述
- 技术栈：Vue3 + Vite + Element Plus（UI 组件库）+ Pinia（状态管理）+ Vue Router。
- 样式方案：以 Element Plus 默认主题为主，辅以少量全局 CSS 与组件内联样式；未引入 SCSS、Tailwind 等预处理或原子化框架。
- 代码高亮：使用 highlight.js + atom-one-dark 主题，配合 marked 渲染 Markdown。
- 构建工具：Vite，通过 alias `@` 指向 `src`，开发服务器代理 `/api` 到后端 8080，并针对 SSE 流做了特殊 header 处理。

## 2. 关键文件与包
- 入口与依赖
  - `frontend/package.json`：声明 element-plus、@element-plus/icons-vue、highlight.js、marked、pinia、vue-router 等依赖。
  - `frontend/src/main.js`：注册 ElementPlus、图标、Pinia、Router，并导入全局样式。
  - `frontend/vite.config.js`：定义别名、开发代理与 SSE 相关 proxy 配置。
- 全局样式
  - `frontend/src/assets/styles/global.css`：reset、body 背景色、markdown-body 排版、代码块复制按钮交互等。
- 应用壳与主题基色
  - `frontend/src/App.vue`：基于 el-container/el-aside/el-header/el-main 的后台布局；大量使用内联 style 与 Element Plus 语义色（如 #409EFF、#303133、#e4e7ed、#f5f7fa）；同时包含一份较完整的 markdown-body 样式覆盖。

## 3. 架构与约定
- 主题来源
  - 主色调来自 Element Plus 默认主题（primary: #409EFF），页面中多处直接使用该色值作为强调色。
  - 背景与边框沿用 Element Plus 中性色板（#f5f7fa、#e4e7ed、#303133）。
- 样式组织方式
  - 全局 reset/markdown 样式集中在 `global.css`，由 `main.js` 统一引入。
  - 业务组件基本不写独立 `.css` 文件，而是直接在 `<template>` 中使用内联 style 或 `<style>` 块（App.vue 即典型示例）。
  - 未建立统一的 design tokens 文件或 CSS 变量体系，颜色/间距多为硬编码常量。
- 响应式策略
  - 采用 Element Plus 栅格与容器组件实现基础布局；侧边栏通过 collapse 属性控制宽度，无媒体查询断点定制。
- 暗色/多主题
  - 未发现 dark mode 开关或主题切换逻辑，整体为浅色主题。

## 4. 开发者应遵循的规则
- 优先使用 Element Plus 组件及其内置语义色（primary/info/success/warning/danger 等），避免随意新增色值。
- 全局通用样式放入 `assets/styles/global.css`，并在 `main.js` 中集中引入；组件级样式尽量使用 `<style scoped>`，减少全局污染。
- 复用 App.vue 中已定义的 `.markdown-body` 样式规范，保持 AI 输出内容排版一致。
- 代码高亮统一使用 highlight.js 的 atom-one-dark 主题，并通过 `code-block-wrapper` 包裹展示。
- 新增页面布局建议沿用 App.vue 的 el-container 结构，保持侧边栏/头部/主内容区一致的视觉节奏。
- 如需自定义主题色，建议在 `vite.config.js` 或 Element Plus 主题定制处统一管理，而非在各组件散落硬编码。