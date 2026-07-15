<template>
  <div class="tools-container">
    <el-card shadow="hover" class="header-card">
      <h2><el-icon><Tools /></el-icon> 工具演示</h2>
      <p>内置工具功能演示：天气查询、计算器等</p>
    </el-card>
    
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span>选择工具</span>
          </template>
          
          <el-radio-group v-model="selectedTool" vertical>
            <el-radio label="weather">
              <el-icon><Sunny /></el-icon> 天气查询
            </el-radio>
            <el-radio label="calculator">
              <el-icon><DataBoard /></el-icon> 计算器
            </el-radio>
            <el-radio label="datetime">
              <el-icon><Clock /></el-icon> 日期时间
            </el-radio>
          </el-radio-group>
        </el-card>
      </el-col>
      
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span>工具参数</span>
          </template>
          
          <el-form :model="toolParams" label-width="100px">
            <!-- 天气查询参数 -->
            <template v-if="selectedTool === 'weather'">
              <el-form-item label="城市名称">
                <el-input v-model="toolParams.city" placeholder="请输入城市名称，如：北京" />
              </el-form-item>
            </template>
            
            <!-- 计算器参数 -->
            <template v-if="selectedTool === 'calculator'">
              <el-form-item label="表达式">
                <el-input v-model="toolParams.expression" placeholder="请输入数学表达式，如：2+3*4" />
              </el-form-item>
            </template>
            
            <!-- 日期时间参数 -->
            <template v-if="selectedTool === 'datetime'">
              <el-form-item label="格式">
                <el-select v-model="toolParams.format" placeholder="选择格式" style="width: 100%">
                  <el-option label="标准格式" value="standard" />
                  <el-option label="时间戳" value="timestamp" />
                  <el-option label="相对时间" value="relative" />
                </el-select>
              </el-form-item>
            </template>
            
            <el-form-item>
              <el-button type="primary" @click="executeTool" :loading="executing" :icon="Right">
                执行工具
              </el-button>
              <el-button @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        
        <el-card shadow="hover" style="margin-top: 20px" v-if="result">
          <template #header>
            <span>执行结果</span>
          </template>
          <div class="result-content">
            <pre>{{ result }}</pre>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Tools, Sunny, DataBoard, Clock, Right } from '@element-plus/icons-vue'
import { callBuiltinTool } from '@/api'

// 状态变量
const selectedTool = ref('weather')
const executing = ref(false)
const result = ref('')

// 工具参数
const toolParams = ref({
  city: '北京',
  expression: '',
  format: 'standard'
})

// 监听工具切换，重置参数
watch(selectedTool, (newTool) => {
  result.value = ''
  if (newTool === 'weather') {
    toolParams.value = { city: '北京' }
  } else if (newTool === 'calculator') {
    toolParams.value = { expression: '' }
  } else if (newTool === 'datetime') {
    toolParams.value = { format: 'standard' }
  }
})

// 重置表单
const resetForm = () => {
  result.value = ''
  if (selectedTool.value === 'weather') {
    toolParams.value = { city: '北京' }
  } else if (selectedTool.value === 'calculator') {
    toolParams.value = { expression: '' }
  } else if (selectedTool.value === 'datetime') {
    toolParams.value = { format: 'standard' }
  }
}

// 执行工具
const executeTool = async () => {
  executing.value = true
  result.value = ''
  try {
    let params = {}
    if (selectedTool.value === 'weather') {
      if (!toolParams.value.city.trim()) {
        ElMessage.error('请输入城市名称')
        return
      }
      params = { city: toolParams.value.city }
    } else if (selectedTool.value === 'calculator') {
      if (!toolParams.value.expression.trim()) {
        ElMessage.error('请输入数学表达式')
        return
      }
      params = { expression: toolParams.value.expression }
    } else if (selectedTool.value === 'datetime') {
      params = { format: toolParams.value.format }
    }
    
    const res = await callBuiltinTool(selectedTool.value, params)
    result.value = JSON.stringify(res.data, null, 2)
    ElMessage.success('工具执行成功')
  } catch (error) {
    result.value = '执行失败：' + error.message
    ElMessage.error('工具执行失败')
  } finally {
    executing.value = false
  }
}
</script>

<style scoped>
.tools-container {
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

.result-content {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
}

.result-content pre {
  margin: 0;
  font-size: 13px;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
