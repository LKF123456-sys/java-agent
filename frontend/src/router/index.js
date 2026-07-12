import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import Home from '../views/Home.vue'
import ChatView from '../views/ChatView.vue'
import MemoryView from '../views/MemoryView.vue'
import RagView from '../views/RagView.vue'
import AgentView from '../views/AgentView.vue'
import SearchAgentView from '../views/SearchAgentView.vue'
import StructuredView from '../views/StructuredView.vue'
import ToolsView from '../views/ToolsView.vue'
import MultiAgentView from '../views/MultiAgentView.vue'
import McpView from '../views/McpView.vue'

const publicRoutes = ['Home']

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/chat',
    name: 'Chat',
    component: ChatView,
    meta: { requiresAuth: true }
  },
  {
    path: '/memory',
    name: 'Memory',
    component: MemoryView,
    meta: { requiresAuth: true }
  },
  {
    path: '/rag',
    name: 'Rag',
    component: RagView,
    meta: { requiresAuth: true }
  },
  {
    path: '/agent',
    name: 'Agent',
    component: AgentView,
    meta: { requiresAuth: true }
  },
  {
    path: '/search-agent',
    name: 'SearchAgent',
    component: SearchAgentView,
    meta: { requiresAuth: true }
  },
  {
    path: '/structured',
    name: 'Structured',
    component: StructuredView,
    meta: { requiresAuth: true }
  },
  {
    path: '/tools',
    name: 'Tools',
    component: ToolsView,
    meta: { requiresAuth: true }
  },
  {
    path: '/multi-agent',
    name: 'MultiAgent',
    component: MultiAgentView,
    meta: { requiresAuth: true }
  },
  {
    path: '/mcp',
    name: 'Mcp',
    component: McpView,
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isLoggedIn = userStore.isLoggedIn

  if (to.meta.requiresAuth && !isLoggedIn) {
    next({ name: 'Home' })
  } else {
    next()
  }
})

export default router
