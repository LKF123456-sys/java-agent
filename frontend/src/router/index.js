import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import ChatView from '../views/ChatView.vue'
import MemoryView from '../views/MemoryView.vue'
import RagView from '../views/RagView.vue'
import AgentView from '../views/AgentView.vue'
import StructuredView from '../views/StructuredView.vue'
import ToolsView from '../views/ToolsView.vue'
import MultiAgentView from '../views/MultiAgentView.vue'
import McpView from '../views/McpView.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/chat',
    name: 'Chat',
    component: ChatView
  },
  {
    path: '/memory',
    name: 'Memory',
    component: MemoryView
  },
  {
    path: '/rag',
    name: 'Rag',
    component: RagView
  },
  {
    path: '/agent',
    name: 'Agent',
    component: AgentView
  },
  {
    path: '/structured',
    name: 'Structured',
    component: StructuredView
  },
  {
    path: '/tools',
    name: 'Tools',
    component: ToolsView
  },
  {
    path: '/multi-agent',
    name: 'MultiAgent',
    component: MultiAgentView
  },
  {
    path: '/mcp',
    name: 'Mcp',
    component: McpView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
