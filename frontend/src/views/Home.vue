<template>
  <CyberLayout>
    <div class="home-container">
      <div class="hero-section">
        <h1 class="hero-title cyber-glow-text">CYBER AI LEARN PLATFORM</h1>
        <p class="hero-subtitle">基于 Spring AI + Ollama 的智能学习平台</p>
        <div class="hero-line"></div>
      </div>

      <div class="features-grid">
        <div 
          v-for="feature in features" 
          :key="feature.path"
          class="feature-card cyber-card"
          :style="`--feature-color: ${feature.color}`"
          @click="navigateTo(feature.path)"
        >
          <div class="card-glow"></div>
          <div class="card-icon" :style="{ color: feature.color }">
            <span class="icon-text">{{ feature.icon }}</span>
          </div>
          <h3 class="card-title" :style="{ color: feature.color }">{{ feature.title }}</h3>
          <p class="card-desc">{{ feature.description }}</p>
          <div class="card-arrow">
            <span>→</span>
          </div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { useRouter } from 'vue-router'
import CyberLayout from '@/components/CyberLayout.vue'

const router = useRouter()

const features = [
  {
    path: '/chat',
    title: '基础聊天',
    description: '与AI进行基础对话，支持流式响应',
    icon: '💬',
    color: 'var(--cyber-cyan)'
  },
  {
    path: '/memory',
    title: '记忆对话',
    description: '多轮对话持久化记忆，MySQL存储历史记录',
    icon: '🧠',
    color: 'var(--cyber-magenta)'
  },
  {
    path: '/rag',
    title: 'RAG检索',
    description: 'PDF/Word/Excel/PPT文档解析，PgVector向量存储',
    icon: '🔍',
    color: 'var(--cyber-green)'
  },
  {
    path: '/agent',
    title: 'Agent智能体',
    description: '自主调用工具完成复杂任务',
    icon: '🤖',
    color: 'var(--cyber-purple)'
  },
  {
    path: '/multi-agent',
    title: '多Agent协作',
    description: 'Planner/Researcher/Coder/Executor协同工作',
    icon: '👥',
    color: 'var(--cyber-yellow)'
  },
  {
    path: '/mcp',
    title: 'MCP协议',
    description: 'Model Context Protocol，支持Cherry Studio等客户端连接',
    icon: '🔌',
    color: 'var(--cyber-blue)'
  },
  {
    path: '/structured',
    title: '结构化输出',
    description: '让AI返回结构化JSON数据',
    icon: '📊',
    color: 'var(--cyber-yellow)'
  },
  {
    path: '/tools',
    title: '工具调用',
    description: 'Function Calling，天气查询/计算器等工具',
    icon: '🔧',
    color: 'var(--cyber-pink)'
  }
]

const navigateTo = (path) => {
  router.push(path)
}
</script>

<style scoped>
.home-container {
  min-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.hero-section {
  text-align: center;
  margin-bottom: 60px;
}

.hero-title {
  font-size: 48px;
  font-weight: 800;
  letter-spacing: 6px;
  color: var(--cyber-cyan);
  margin-bottom: 20px;
  animation: title-glow 2s ease-in-out infinite alternate;
}

@keyframes title-glow {
  0% {
    text-shadow: 0 0 10px var(--cyber-cyan), 0 0 20px var(--cyber-cyan), 0 0 40px var(--cyber-cyan);
  }
  100% {
    text-shadow: 0 0 20px var(--cyber-cyan), 0 0 40px var(--cyber-cyan), 0 0 60px var(--cyber-cyan), 0 0 80px var(--cyber-magenta);
  }
}

.hero-subtitle {
  font-size: 18px;
  color: var(--cyber-text-secondary);
  letter-spacing: 2px;
  margin-bottom: 30px;
}

.hero-line {
  width: 300px;
  height: 2px;
  margin: 0 auto;
  background: linear-gradient(90deg, transparent, var(--cyber-cyan), var(--cyber-magenta), var(--cyber-cyan), transparent);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  max-width: 1400px;
  width: 100%;
}

.feature-card {
  padding: 32px 28px;
  cursor: pointer;
  transition: all 0.4s ease;
  clip-path: polygon(0 0, calc(100% - 16px) 0, 100% 16px, 100% 100%, 16px 100%, 0 calc(100% - 16px));
  position: relative;
}

.feature-card:hover {
  transform: translateY(-8px);
}

.card-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0;
  transition: opacity 0.4s ease;
  box-shadow: 0 0 30px var(--feature-color);
  pointer-events: none;
}

.feature-card:hover .card-glow {
  opacity: 1;
}

.card-icon {
  font-size: 48px;
  margin-bottom: 20px;
  filter: drop-shadow(0 0 10px currentColor);
  transition: all 0.3s ease;
}

.feature-card:hover .card-icon {
  transform: scale(1.1);
  filter: drop-shadow(0 0 20px currentColor);
}

.icon-text {
  font-size: 48px;
}

.card-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
  margin-bottom: 12px;
  text-shadow: 0 0 10px currentColor;
}

.card-desc {
  font-size: 13px;
  color: var(--cyber-text-secondary);
  line-height: 1.8;
  letter-spacing: 0.5px;
}

.card-arrow {
  position: absolute;
  bottom: 24px;
  right: 28px;
  font-size: 20px;
  color: var(--feature-color);
  opacity: 0;
  transform: translateX(-10px);
  transition: all 0.3s ease;
}

.feature-card:hover .card-arrow {
  opacity: 1;
  transform: translateX(0);
}

@media (max-width: 1200px) {
  .features-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .hero-title {
    font-size: 32px;
    letter-spacing: 3px;
  }
}

@media (max-width: 640px) {
  .features-grid {
    grid-template-columns: 1fr;
  }
  
  .hero-title {
    font-size: 24px;
    letter-spacing: 2px;
  }
  
  .hero-subtitle {
    font-size: 14px;
  }
}
</style>
