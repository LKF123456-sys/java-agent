<template>
  <CyberLayout>
    <div class="mcp-view">
      <div class="page-header">
        <div class="header-decoration left"></div>
        <h1 class="page-title blue-glow">MCP 工具协议</h1>
        <div class="header-decoration right"></div>
      </div>

      <div class="endpoint-panel cyber-card blue-card">
        <div class="card-corner tl"></div>
        <div class="card-corner tr"></div>
        <div class="card-corner bl"></div>
        <div class="card-corner br"></div>
        
        <div class="panel-header">
          <span class="panel-icon">◆</span>
          <h2 class="panel-title">MCP 服务端点</h2>
          <span class="panel-line"></span>
        </div>
        
        <div class="endpoint-body">
          <div class="endpoint-desc">
            <p class="desc-text">本服务实现了 Model Context Protocol (MCP) 协议，支持外部 MCP 客户端连接，如 Cherry Studio、Chatbox 等。</p>
          </div>
          <div class="endpoint-list">
            <div class="endpoint-item">
              <span class="endpoint-label">SSE 端点</span>
              <code class="endpoint-code">/sse</code>
            </div>
            <div class="endpoint-item">
              <span class="endpoint-label">消息端点</span>
              <code class="endpoint-code">/mcp/message</code>
            </div>
          </div>
          <div class="client-hint">
            <span class="hint-icon">💡</span>
            <span class="hint-text">在 MCP 客户端中配置 SSE 连接地址为：<code class="inline-code">{{ sseUrl }}</code></span>
          </div>
        </div>
      </div>

      <div class="server-panel cyber-card blue-card">
        <div class="card-corner tl"></div>
        <div class="card-corner tr"></div>
        <div class="card-corner bl"></div>
        <div class="card-corner br"></div>
        
        <div class="panel-header">
          <span class="panel-icon">◆</span>
          <h2 class="panel-title">MCP 服务器信息</h2>
          <span class="panel-line"></span>
          <button class="refresh-btn" @click="fetchServerInfo" :disabled="loading">
            <span v-if="loading" class="loading-spinner small"></span>
            <span v-else>⟳</span>
          </button>
        </div>
        
        <div class="server-info">
          <div class="info-item">
            <span class="info-label">服务器状态</span>
            <span class="info-value">
              <span class="status-badge online">
                <span class="status-dot"></span>
                在线
              </span>
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">服务器名称</span>
            <span class="info-value">{{ serverInfo.name }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">版本</span>
            <span class="info-value">{{ serverInfo.version }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">工具数量</span>
            <span class="info-value">{{ tools.length }} 个</span>
          </div>
        </div>
      </div>

      <div class="content-grid">
        <div class="tools-panel cyber-card blue-card">
          <div class="card-corner tl"></div>
          <div class="card-corner tr"></div>
          <div class="card-corner bl"></div>
          <div class="card-corner br"></div>
          
          <div class="panel-header">
            <span class="panel-icon">◆</span>
            <h2 class="panel-title">已注册工具</h2>
            <span class="panel-line"></span>
          </div>
          
          <div class="tools-list cyber-scrollbar">
            <div v-for="tool in tools" :key="tool.name" class="tool-item">
              <div class="tool-header">
                <div class="tool-info">
                  <span class="tool-icon">{{ tool.icon }}</span>
                  <span class="tool-name">{{ tool.name }}</span>
                  <span class="tool-status-badge enabled">
                    已注册
                  </span>
                </div>
              </div>
              <p class="tool-description">{{ tool.description }}</p>
              <button class="test-btn" @click="selectTool(tool)">
                <span>测试调用</span>
                <span class="test-icon">▶</span>
              </button>
            </div>
          </div>
        </div>

        <div class="right-column">
          <div class="test-panel cyber-card blue-card">
            <div class="card-corner tl"></div>
            <div class="card-corner tr"></div>
            <div class="card-corner bl"></div>
            <div class="card-corner br"></div>
            
            <div class="panel-header">
              <span class="panel-icon">◆</span>
              <h2 class="panel-title">工具调用测试</h2>
              <span class="panel-line"></span>
            </div>
            
            <div class="test-body">
              <div v-if="selectedTool" class="selected-tool-info">
                <span class="selected-tool-name">{{ selectedTool.name }}</span>
                <button class="clear-selection" @click="selectedTool = null; testResult = ''; testParams = '{}'">✕</button>
              </div>
              <div v-else class="no-selection">
                <p>请从左侧选择一个工具进行测试</p>
              </div>
              
              <div v-if="selectedTool" class="test-form">
                <div class="form-group">
                  <label class="form-label">参数 (JSON)</label>
                  <textarea 
                    v-model="testParams" 
                    class="cyber-input blue-input textarea code-area" 
                    rows="6"
                    :placeholder="selectedTool.paramsHint"
                  ></textarea>
                </div>
                
                <button 
                  class="cyber-btn blue-btn action-btn" 
                  @click="handleTestTool"
                  :disabled="testing"
                >
                  <span v-if="testing" class="loading-spinner"></span>
                  <span v-else class="btn-icon">▶</span>
                  <span>{{ testing ? '调用中...' : '执行调用' }}</span>
                </button>

                <div v-if="testError" class="error-msg">
                  <span class="error-icon">✕</span>
                  {{ testError }}
                </div>
                
                <div v-if="testResult" class="result-area">
                  <div class="result-label">调用结果</div>
                  <pre class="result-content">{{ testResult }}</pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </CyberLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import CyberLayout from '@/components/CyberLayout.vue'
import { post } from '@/utils/request'

const sseUrl = ref(window.location.origin + '/sse')

const loading = ref(false)
const serverInfo = ref({
  name: 'Spring AI MCP Server',
  version: '1.0.0',
  status: 'online'
})

const tools = ref([
  { 
    name: 'get_weather', 
    icon: '🌤',
    description: '天气查询：查询指定城市的当前天气信息',
    paramsHint: '{"city": "北京"}'
  },
  { 
    name: 'calculator', 
    icon: '🔢',
    description: '计算器：执行基础数学计算，支持加减乘除运算',
    paramsHint: '{"expression": "1 + 2 * 3"}'
  },
  { 
    name: 'get_system_time', 
    icon: '⏰',
    description: '系统时间：获取当前系统时间',
    paramsHint: '{}'
  },
  { 
    name: 'get_system_info', 
    icon: '💻',
    description: '系统信息：获取系统运行状态信息，包括CPU、内存等',
    paramsHint: '{}'
  },
  { 
    name: 'list_agents', 
    icon: '🤖',
    description: '可用Agent列表：获取系统中所有可用的Agent列表',
    paramsHint: '{}'
  },
  { 
    name: 'eval_expression', 
    icon: '🧮',
    description: '表达式计算：计算复杂的数学表达式',
    paramsHint: '{"expression": "sin(PI/2) + cos(0)"}'
  }
])

const selectedTool = ref(null)
const testParams = ref('{}')
const testing = ref(false)
const testResult = ref('')
const testError = ref('')

const fetchServerInfo = async () => {
  loading.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 500))
  } finally {
    loading.value = false
  }
}

const selectTool = (tool) => {
  selectedTool.value = tool
  testParams.value = '{}'
  testResult.value = ''
  testError.value = ''
}

const handleTestTool = async () => {
  if (!selectedTool.value) return
  
  testing.value = true
  testError.value = ''
  testResult.value = ''
  
  try {
    let params = {}
    try {
      params = JSON.parse(testParams.value)
    } catch (e) {
      testError.value = '参数格式错误，请输入有效的JSON'
      return
    }
    
    const result = await post(`/api/mcp/tools/${selectedTool.value.name}/invoke`, params)
    testResult.value = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
  } catch (error) {
    testError.value = error.message || '调用失败'
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  fetchServerInfo()
})
</script>

<style scoped>
.mcp-view {
  max-width: 1600px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 24px;
  padding: 20px 0;
}

.header-decoration {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-blue), transparent);
  position: relative;
}

.header-decoration::after {
  content: '';
  position: absolute;
  top: -4px;
  width: 9px;
  height: 9px;
  border: 1px solid var(--cyber-blue);
  transform: rotate(45deg);
  box-shadow: 0 0 10px var(--cyber-blue);
}

.header-decoration.left::after { right: 0; }
.header-decoration.right::after { left: 0; }

.page-title {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 4px;
  margin: 0;
  white-space: nowrap;
}

.blue-glow {
  color: var(--cyber-blue);
  text-shadow: 0 0 10px var(--cyber-blue), 0 0 20px var(--cyber-blue), 0 0 40px var(--cyber-cyan);
}

.endpoint-panel, .server-panel, .tools-panel, .test-panel {
  position: relative;
  padding: 0;
  margin-bottom: 24px;
  clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 15px, 100% 100%, 15px 100%, 0 calc(100% - 15px));
}

.blue-card::before {
  background: linear-gradient(90deg, var(--cyber-blue), var(--cyber-cyan), var(--cyber-blue));
}

.card-corner {
  position: absolute;
  width: 20px;
  height: 20px;
  border: 2px solid var(--cyber-blue);
  z-index: 10;
}

.card-corner.tl { top: 10px; left: 10px; border-right: none; border-bottom: none; }
.card-corner.tr { top: 10px; right: 10px; border-left: none; border-bottom: none; }
.card-corner.bl { bottom: 10px; left: 10px; border-right: none; border-top: none; }
.card-corner.br { bottom: 10px; right: 10px; border-left: none; border-top: none; }

.endpoint-body {
  padding: 24px;
}

.endpoint-desc {
  margin-bottom: 20px;
}

.desc-text {
  font-size: 14px;
  color: var(--cyber-text-secondary);
  line-height: 1.7;
  margin: 0;
}

.endpoint-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.endpoint-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: rgba(0, 102, 255, 0.05);
  border: 1px solid rgba(0, 102, 255, 0.2);
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 10px 100%, 0 calc(100% - 10px));
}

.endpoint-label {
  font-size: 11px;
  color: var(--cyber-text-muted);
  letter-spacing: 1px;
  text-transform: uppercase;
}

.endpoint-code {
  font-family: 'Consolas', monospace;
  font-size: 14px;
  color: var(--cyber-blue);
  font-weight: 600;
}

.client-hint {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  background: rgba(0, 255, 136, 0.05);
  border: 1px solid rgba(0, 255, 136, 0.2);
}

.hint-icon {
  font-size: 16px;
}

.hint-text {
  font-size: 13px;
  color: var(--cyber-text-secondary);
  line-height: 1.6;
}

.inline-code {
  font-family: 'Consolas', monospace;
  font-size: 12px;
  padding: 2px 8px;
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.3);
  color: var(--cyber-green);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(0, 102, 255, 0.2);
  background: rgba(0, 102, 255, 0.05);
}

.panel-icon {
  color: var(--cyber-blue);
  font-size: 10px;
  text-shadow: 0 0 10px var(--cyber-blue);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--cyber-blue);
  letter-spacing: 2px;
  margin: 0;
}

.panel-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--cyber-blue), transparent);
}

.refresh-btn {
  width: 36px;
  height: 36px;
  background: transparent;
  border: 1px solid rgba(0, 102, 255, 0.3);
  color: var(--cyber-blue);
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.refresh-btn:hover {
  background: rgba(0, 102, 255, 0.1);
  border-color: var(--cyber-blue);
  box-shadow: 0 0 15px rgba(0, 102, 255, 0.3);
}

.server-info {
  padding: 24px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  font-size: 11px;
  color: var(--cyber-text-muted);
  letter-spacing: 1px;
  text-transform: uppercase;
}

.info-value {
  font-size: 14px;
  color: var(--cyber-text-primary);
  font-weight: 500;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  font-size: 12px;
  letter-spacing: 1px;
  align-self: flex-start;
}

.status-badge.online {
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid var(--cyber-green);
  color: var(--cyber-green);
}

.status-badge .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse-glow 2s ease-in-out infinite;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 450px;
  gap: 24px;
}

.right-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.tools-list {
  padding: 20px;
  max-height: 500px;
  overflow-y: auto;
}

.tool-item {
  padding: 16px;
  margin-bottom: 12px;
  background: rgba(0, 102, 255, 0.05);
  border: 1px solid rgba(0, 102, 255, 0.2);
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 10px 100%, 0 calc(100% - 10px));
  transition: all 0.3s ease;
}

.tool-item:hover {
  border-color: rgba(0, 102, 255, 0.4);
  background: rgba(0, 102, 255, 0.08);
}

.tool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tool-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tool-icon {
  font-size: 20px;
}

.tool-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--cyber-blue);
  font-family: 'Consolas', monospace;
}

.tool-status-badge {
  font-size: 10px;
  padding: 2px 8px;
  letter-spacing: 1px;
}

.tool-status-badge.enabled {
  background: rgba(0, 255, 136, 0.1);
  color: var(--cyber-green);
  border: 1px solid var(--cyber-green);
}

.tool-description {
  font-size: 13px;
  color: var(--cyber-text-secondary);
  margin-bottom: 12px;
  line-height: 1.5;
}

.test-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid rgba(0, 102, 255, 0.4);
  color: var(--cyber-blue);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.test-btn:hover {
  background: rgba(0, 102, 255, 0.1);
  box-shadow: 0 0 15px rgba(0, 102, 255, 0.3);
}

.test-icon {
  font-size: 10px;
}

.test-body {
  padding: 24px;
}

.selected-tool-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba(0, 102, 255, 0.1);
  border: 1px solid rgba(0, 102, 255, 0.3);
  margin-bottom: 20px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.selected-tool-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--cyber-blue);
  font-family: 'Consolas', monospace;
}

.clear-selection {
  width: 24px;
  height: 24px;
  background: transparent;
  border: none;
  color: var(--cyber-text-muted);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.clear-selection:hover {
  color: var(--cyber-pink);
}

.no-selection {
  text-align: center;
  padding: 40px 20px;
  color: var(--cyber-text-muted);
  font-size: 13px;
}

.form-group {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 12px;
  color: var(--cyber-blue);
  letter-spacing: 1px;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.blue-input:focus {
  border-color: var(--cyber-blue);
  box-shadow: 0 0 20px rgba(0, 102, 255, 0.3);
}

.textarea {
  resize: none;
  font-family: 'Consolas', monospace;
  line-height: 1.6;
}

.code-area {
  font-size: 13px;
}

.blue-btn {
  background: linear-gradient(135deg, var(--cyber-blue) 0%, var(--cyber-cyan) 100%);
}

.blue-btn:hover {
  box-shadow: 0 0 30px rgba(0, 102, 255, 0.5);
}

.action-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
}

.btn-icon { font-size: 16px; }

.error-msg {
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(255, 0, 128, 0.1);
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.error-icon { font-weight: 700; }

.result-area {
  margin-top: 20px;
}

.result-label {
  font-size: 12px;
  color: var(--cyber-green);
  letter-spacing: 1px;
  margin-bottom: 10px;
  text-transform: uppercase;
}

.result-content {
  padding: 16px;
  background: var(--cyber-bg-secondary);
  border: 1px solid rgba(0, 255, 136, 0.2);
  font-family: 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--cyber-green);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-spinner.small {
  width: 14px;
  height: 14px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
