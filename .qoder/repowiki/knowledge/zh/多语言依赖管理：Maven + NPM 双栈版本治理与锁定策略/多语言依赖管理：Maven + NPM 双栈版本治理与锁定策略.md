---
kind: dependency_management
name: 多语言依赖管理：Maven + NPM 双栈版本治理与锁定策略
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - frontend/package.json
    - frontend/package-lock.json
    - .github/workflows/ci-cd.yml
---

## 系统概览
本仓库采用「后端 Java（Maven）+ 前端 Node.js（NPM）」双栈依赖管理，通过根级聚合 POM 统一声明 Spring Boot 生态与 AI 相关依赖版本，前端使用 package.json + package-lock.json 锁定构建产物。CI/CD 基于 GitHub Actions 在每次 push 时触发 Maven 编译与 npm install，确保依赖可复现。

## 核心文件与位置
- `pom.xml`：Spring Boot 3.4.13 父 POM、全局 `<properties>` 版本属性、`<dependencyManagement>` 引入 spring-ai-bom、所有运行时/测试依赖声明、maven-compiler-plugin 与 spring-boot-maven-plugin 配置。
- `frontend/package.json`：Vue3 + Vite + Element Plus 等生产与开发依赖声明，scripts 提供 dev/build/preview 命令。
- `frontend/package-lock.json`：npm lockfile v3，记录每个包的精确版本与 sha512 integrity，保证 CI 与本地构建一致。
- `.github/workflows/ci-cd.yml`：调用 `mvn -B verify` 与 `npm ci --prefix frontend`，是依赖拉取与校验的入口。

## 架构与约定
- **Java 侧**
  - 以 `spring-boot-starter-parent:3.4.13` 为父 POM，继承其 BOM 与插件默认版本。
  - 通过 `<properties>` 集中维护关键第三方版本：`spring-ai.version=1.1.8`、`jjwt.version=0.12.7`、`springdoc.version=2.8.17`、`resilience4j.version=2.3.0`、`tess4j.version=5.19.0`、`hutool.version=5.8.46`、`logstash-logback-encoder.version=8.1`、`micrometer-tracing.version=1.4.3`，以及 `java.version/maven.compiler.source/target=21`。
  - 使用 `spring-ai-bom`（import scope）统一管理 Spring AI 子模块版本，避免各 starter 之间版本冲突。
  - 对非 Spring 生态组件（如 mybatis-plus-spring-boot3-starter、mybatis-plus-jsqlparser、resilience4j-*、tess4j、hutool-all、springdoc-openapi-starter-webmvc-ui 等）直接在 `<dependencies>` 中显式声明版本，保持可控性。
  - Lombok 作为 annotationProcessor 路径参与编译期生成，并在 spring-boot-maven-plugin 的 `<excludes>` 中排除打包，减小镜像体积。
  - 测试依赖使用 H2 内存库与 Testcontainers（mysql），隔离外部数据库依赖。
- **前端侧**
  - 仅声明顶层依赖，无私有 registry 或 .npmrc；`package-lock.json` 由 npm 自动生成并随代码提交，CI 使用 `npm ci` 严格遵循锁文件。
  - 生产依赖集中在 Vue3 生态（vue、vue-router、pinia）、UI 框架（element-plus、@element-plus/icons-vue）、HTTP（axios）、安全与渲染（dompurify、highlight.js、marked）。
  - 开发依赖仅包含 vite 与 @vitejs/plugin-vue，保持构建工具链精简。
- **容器与 CI**
  - Dockerfile 基于 maven:3.9-eclipse-temurin-21-alpine 进行多阶段构建，最终镜像只包含运行时代码，不携带源码与依赖源。
  - GitHub Actions 同时执行 `mvn -B verify` 与 `npm ci --prefix frontend`，将依赖解析与校验纳入流水线。

## 开发者应遵守的规则
1. **新增/升级 Java 依赖**：优先在 `<properties>` 中定义版本号，再在 `<dependencies>` 中引用 `${xxx.version}`；若该依赖属于 Spring AI 生态，则通过 spring-ai-bom 间接管理，不要重复声明版本。
2. **禁止在子模块 POM 中覆盖父 POM 已管理的版本**，除非确有必要且需经评审。
3. **Lombok 仅用于编译期**，不得在业务逻辑中直接依赖 lombok 生成的类签名做运行时判断。
4. **前端依赖升级必须同步更新 `package-lock.json`**，提交时一并变更，CI 会拒绝与锁文件不一致的安装。
5. **测试依赖与生产依赖严格分离**：H2、Testcontainers、spring-security-test 等均标记 `test` scope，不得泄漏到运行包。
6. **私有仓库/代理**：当前未配置任何私有 Maven/NPM 仓库或代理，如需接入需在 `settings.xml` 与 `.npmrc` 中补充，并确保 CI 环境变量注入凭据。
7. **Docker 构建环境**：容器内使用 alpine + temurin-21，新增 native 依赖（如 tess4j 的 Tesseract）时需同步调整 Dockerfile 基础镜像或安装步骤。