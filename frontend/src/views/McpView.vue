<template>
  <div class="mcp-container">
    <el-card shadow="hover" class="header-card">
      <h2><el-icon><Setting /></el-icon> MCP 工具管理</h2>
      <p>模型上下文协议工具列表与测试</p>
      <el-button type="primary" :icon="Refresh" @click="loadTools" :loading="loading" style="margin-top: 10px">
        刷新工具列表
      </el-button>
    </el-card>
    
    <el-card shadow="hover" class="tools-card" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>可用工具列表</span>
        </div>
      </template>
      
      <el-table :data="tools" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="name" label="工具名" width="200" />
        <el-table-column prop="description" label="描述" min-width="300" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'">
              {{ scope.row.enabled ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button type="primary" size="small" @click="openTestDialog(scope.row)" :icon="Tools">
              测试
            </el-button>
            <el-button 
              :type="scope.row.enabled ? 'danger' : 'success'" 
              size="small" 
              @click="toggleTool(scope.row)"
            >
              {{ scope.row.enabled ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 测试对话框 -->
    <el-dialog v-model="testDialogVisible" title="测试工具" width="600px">
      <el-form :model="testForm" label-width="100px">
        <el-form-item label="工具名称">
          <el-input v-model="testForm.name" disabled />
        </el-form-item>
        <el-form-item label="参数 (JSON)">
          <el-input
            v-model="testForm.params"
            type="textarea"
            :rows="6"
            placeholder='例如: {"city": "北京"}'
          />
        </el-form-item>
      </el-form>
      
      <div v-if="testResult" class="test-result">
        <el-divider>执行结果</el-divider>
        <pre>{{ testResult }}</pre>
      </div>
      
      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="executeTool" :loading="testing">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Refresh, Tools } from '@element-plus/icons-vue'
import { getMcpTools, callMcpTool, toggleMcpTool } from '@/api'

// 状态变量
const tools = ref([])
const loading = ref(false)
const testing = ref(false)
const testDialogVisible = ref(false)
const testForm = ref({
  name: '',
  params: '{}'
})
const testResult = ref('')

// 加载工具列表
const loadTools = async () => {
  loading.value = true
  try {
    const res = await getMcpTools()
    tools.value = res.data || []
  } catch (error) {
    ElMessage.error('加载工具列表失败：' + error.message)
  } finally {
    loading.value = false
  }
}

// 切换工具启用状态
const toggleTool = async (tool) => {
  try {
    await ElMessageBox.confirm(
      `确定要${tool.enabled ? '禁用' : '启用'}工具 "${tool.name}" 吗？`,
      '确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await toggleMcpTool(tool.name, !tool.enabled)
    tool.enabled = !tool.enabled
    ElMessage.success(`工具已${tool.enabled ? '启用' : '禁用'}`)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败：' + error.message)
    }
  }
}

// 打开测试对话框
const openTestDialog = (tool) => {
  testForm.value = {
    name: tool.name,
    params: '{}'
  }
  testResult.value = ''
  testDialogVisible.value = true
}

// 执行工具
const executeTool = async () => {
  testing.value = true
  testResult.value = ''
  try {
    let params = {}
    try {
      params = JSON.parse(testForm.value.params)
    } catch (e) {
      ElMessage.error('参数必须是有效的 JSON 格式')
      return
    }
    
    const res = await callMcpTool(testForm.value.name, params)
    testResult.value = JSON.stringify(res.data, null, 2)
    ElMessage.success('工具执行成功')
  } catch (error) {
    testResult.value = '执行失败：' + error.message
    ElMessage.error('工具执行失败')
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadTools()
})
</script>

<style scoped>
.mcp-container {
  padding: 0;
}

.header-card h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 24px;
}

.header-card p {
  margin: 8px 0 0 0;
  color: #909399;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.test-result pre {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 13px;
  max-height: 300px;
  overflow-y: auto;
}
</style>
