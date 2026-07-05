<template>
    <div class="structured-view">
      <div class="page-header">
        <div class="header-decoration left"></div>
        <h1 class="page-title yellow-glow">结构化输出</h1>
        <div class="header-decoration right"></div>
      </div>

      <div class="cards-grid">
        <div class="feature-card cyber-card yellow-card">
          <div class="card-corner tl"></div>
          <div class="card-corner tr"></div>
          <div class="card-corner bl"></div>
          <div class="card-corner br"></div>
          
          <div class="card-header">
            <div class="card-icon book-icon">📖</div>
            <div class="card-title-wrap">
              <h2 class="card-title">书籍信息查询</h2>
              <p class="card-subtitle">获取结构化的书籍详细信息</p>
            </div>
          </div>
          
          <div class="card-body">
            <div class="form-group">
              <label class="form-label yellow">书名</label>
              <div class="input-row">
                <input 
                  v-model="bookTitle" 
                  type="text" 
                  class="cyber-input yellow-input" 
                  placeholder="请输入书名，例如：三体"
                  @keyup.enter="handleBookQuery"
                />
                <button 
                  class="cyber-btn yellow-btn query-btn" 
                  @click="handleBookQuery"
                  :disabled="bookLoading"
                >
                  <span v-if="bookLoading" class="loading-spinner small"></span>
                  <span v-else>查询</span>
                </button>
              </div>
            </div>

            <div v-if="bookLoading" class="inline-loading">
              <span class="typing-dot yellow"></span>
              <span class="typing-dot yellow"></span>
              <span class="typing-dot yellow"></span>
              <span class="loading-text-yellow">正在查询书籍信息...</span>
            </div>
            <div v-else-if="bookError" class="inline-error">
              <span class="error-icon">✕</span>
              {{ bookError }}
            </div>
          </div>
        </div>

        <div class="feature-card cyber-card yellow-card">
          <div class="card-corner tl"></div>
          <div class="card-corner tr"></div>
          <div class="card-corner bl"></div>
          <div class="card-corner br"></div>
          
          <div class="card-header">
            <div class="card-icon movie-icon">🎬</div>
            <div class="card-title-wrap">
              <h2 class="card-title">电影推荐</h2>
              <p class="card-subtitle">按类型获取电影推荐列表</p>
            </div>
          </div>
          
          <div class="card-body">
            <div class="form-group">
              <label class="form-label yellow">电影类型</label>
              <select v-model="movieGenre" class="cyber-input yellow-input genre-select">
                <option value="科幻">科幻</option>
                <option value="动作">动作</option>
                <option value="爱情">爱情</option>
                <option value="喜剧">喜剧</option>
                <option value="恐怖">恐怖</option>
                <option value="剧情">剧情</option>
                <option value="动画">动画</option>
                <option value="悬疑">悬疑</option>
              </select>
            </div>
            
            <div class="form-group">
              <label class="form-label yellow">推荐数量</label>
              <div class="number-input-wrap">
                <button class="num-btn yellow-num" @click="movieCount > 1 && movieCount--">-</button>
                <input 
                  v-model.number="movieCount" 
                  type="number" 
                  class="cyber-input yellow-input number-input" 
                  min="1"
                  max="10"
                />
                <button class="num-btn yellow-num" @click="movieCount < 10 && movieCount++">+</button>
              </div>
            </div>
            
            <button 
              class="cyber-btn yellow-btn action-btn" 
              @click="handleMovieRecommend"
              :disabled="movieLoading"
            >
              <span v-if="movieLoading" class="loading-spinner"></span>
              <span v-else class="btn-icon">◈</span>
              <span>{{ movieLoading ? '推荐中...' : '获取推荐' }}</span>
            </button>

            <div v-if="movieError" class="inline-error">
              <span class="error-icon">✕</span>
              {{ movieError }}
            </div>
          </div>
        </div>
      </div>

      <div v-if="bookResult || movieResult || bookLoading || movieLoading" class="result-area">
        <div v-if="bookResult" class="book-result cyber-card">
          <div class="result-section-header">
            <span class="section-icon">◆</span>
            <h3 class="section-title yellow-glow">书籍信息</h3>
            <span class="section-line"></span>
          </div>
          
          <div class="book-card">
            <div class="book-cover">
              <div class="cover-placeholder">
                <span class="cover-icon">📚</span>
                <span class="cover-text">{{ bookResult.title || '书籍封面' }}</span>
              </div>
            </div>
            <div class="book-info">
              <h3 class="book-title">{{ bookResult.title || '未知书名' }}</h3>
              <div class="info-row">
                <span class="info-label">作者</span>
                <span class="info-value">{{ bookResult.author || '未知' }}</span>
              </div>
              <div v-if="bookResult.year" class="info-row">
                <span class="info-label">出版年份</span>
                <span class="info-value">{{ bookResult.year }}</span>
              </div>
              <div v-if="bookResult.genre" class="info-row">
                <span class="info-label">类型</span>
                <span class="tag yellow-tag">{{ bookResult.genre }}</span>
              </div>
              <div v-if="bookResult.rating" class="info-row">
                <span class="info-label">评分</span>
                <span class="rating">
                  <span class="star">★</span>
                  {{ bookResult.rating }}
                </span>
              </div>
              <div v-if="bookResult.description" class="book-desc">
                <p>{{ bookResult.description }}</p>
              </div>
            </div>
          </div>
        </div>

        <div v-if="movieResult" class="movie-result cyber-card">
          <div class="result-section-header">
            <span class="section-icon">◆</span>
            <h3 class="section-title yellow-glow">电影推荐</h3>
            <span class="section-line"></span>
          </div>
          
          <div class="movies-grid">
            <div v-for="(movie, index) in normalizedMovies" :key="index" class="movie-card">
              <div class="movie-poster">
                <div class="poster-placeholder">
                  <span class="poster-icon">🎞</span>
                </div>
                <div class="movie-rank">{{ index + 1 }}</div>
              </div>
              <div class="movie-details">
                <h4 class="movie-title">{{ movie.title || '未知电影' }}</h4>
                <div class="movie-meta">
                  <span v-if="movie.year" class="meta-item">{{ movie.year }}</span>
                  <span v-if="movie.director" class="meta-item">导演: {{ movie.director }}</span>
                </div>
                <div v-if="movie.rating" class="movie-rating">
                  <span class="star yellow">★</span>
                  {{ movie.rating }}
                </div>
                <p v-if="movie.description" class="movie-desc">{{ movie.description }}</p>
                <div v-if="movie.genre" class="movie-tags">
                  <span v-for="(g, i) in (Array.isArray(movie.genre) ? movie.genre : [movie.genre])" :key="i" class="tag yellow-tag small">
                    {{ g }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { get } from '@/utils/request'

const bookTitle = ref('')
const bookLoading = ref(false)
const bookError = ref('')
const bookResult = ref(null)

const movieGenre = ref('科幻')
const movieCount = ref(5)
const movieLoading = ref(false)
const movieError = ref('')
const movieResult = ref(null)

const normalizedMovies = computed(() => {
  if (!movieResult.value) return []
  if (Array.isArray(movieResult.value)) return movieResult.value
  if (movieResult.value.movies && Array.isArray(movieResult.value.movies)) return movieResult.value.movies
  if (movieResult.value.recommendations && Array.isArray(movieResult.value.recommendations)) return movieResult.value.recommendations
  if (movieResult.value.list && Array.isArray(movieResult.value.list)) return movieResult.value.list
  return []
})

const handleBookQuery = async () => {
  if (!bookTitle.value.trim()) {
    bookError.value = '请输入书名'
    return
  }
  
  bookLoading.value = true
  bookError.value = ''
  bookResult.value = null
  
  try {
    const result = await get('/api/structured/book', { title: bookTitle.value.trim() })
    bookResult.value = typeof result === 'string' ? { title: bookTitle.value, description: result } : result
  } catch (error) {
    bookError.value = error.message || '查询失败'
  } finally {
    bookLoading.value = false
  }
}

const handleMovieRecommend = async () => {
  movieLoading.value = true
  movieError.value = ''
  movieResult.value = null
  
  try {
    const result = await get('/api/structured/movies', {
      genre: movieGenre.value,
      count: movieCount.value
    })
    movieResult.value = result
  } catch (error) {
    movieError.value = error.message || '获取推荐失败'
  } finally {
    movieLoading.value = false
  }
}
</script>

<style scoped>
.structured-view {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 32px;
  padding: 20px 0;
}

.header-decoration {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--cyber-yellow), transparent);
  position: relative;
}

.header-decoration::after {
  content: '';
  position: absolute;
  top: -4px;
  width: 9px;
  height: 9px;
  border: 1px solid var(--cyber-yellow);
  transform: rotate(45deg);
  box-shadow: 0 0 10px var(--cyber-yellow);
}

.header-decoration.left::after {
  right: 0;
}

.header-decoration.right::after {
  left: 0;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 4px;
  margin: 0;
  white-space: nowrap;
}

.yellow-glow {
  color: var(--cyber-yellow);
  text-shadow: 0 0 10px var(--cyber-yellow), 0 0 20px rgba(255, 255, 0, 0.5);
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}

.feature-card {
  padding: 0;
  position: relative;
}

.yellow-card::before {
  background: linear-gradient(90deg, var(--cyber-yellow), #ffa500, var(--cyber-yellow));
}

.card-corner {
  position: absolute;
  width: 20px;
  height: 20px;
  border: 2px solid var(--cyber-yellow);
}

.card-corner.tl {
  top: 10px;
  left: 10px;
  border-right: none;
  border-bottom: none;
}

.card-corner.tr {
  top: 10px;
  right: 10px;
  border-left: none;
  border-bottom: none;
}

.card-corner.bl {
  bottom: 10px;
  left: 10px;
  border-right: none;
  border-top: none;
}

.card-corner.br {
  bottom: 10px;
  right: 10px;
  border-left: none;
  border-top: none;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 28px 20px;
  border-bottom: 1px solid rgba(255, 255, 0, 0.2);
}

.card-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  background: linear-gradient(135deg, var(--cyber-yellow), #ffa500);
  box-shadow: 0 0 20px rgba(255, 255, 0, 0.4);
}

.card-title-wrap {
  flex: 1;
}

.card-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--cyber-yellow);
  letter-spacing: 2px;
  margin: 0 0 6px 0;
}

.card-subtitle {
  font-size: 12px;
  color: var(--cyber-text-muted);
  letter-spacing: 1px;
  margin: 0;
}

.card-body {
  padding: 24px 28px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label.yellow {
  display: block;
  font-size: 12px;
  color: var(--cyber-yellow);
  letter-spacing: 1px;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.yellow-input:focus {
  border-color: var(--cyber-yellow);
  box-shadow: 0 0 20px rgba(255, 255, 0, 0.3);
}

.input-row {
  display: flex;
  gap: 12px;
}

.input-row .yellow-input {
  flex: 1;
}

.query-btn {
  min-width: 90px;
  padding: 14px 20px;
}

.genre-select {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23ffff00' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 16px center;
  padding-right: 40px;
}

.number-input-wrap {
  display: flex;
  gap: 8px;
  align-items: center;
}

.num-btn.yellow-num {
  width: 48px;
  height: 48px;
  background: rgba(255, 255, 0, 0.1);
  border: 1px solid rgba(255, 255, 0, 0.3);
  color: var(--cyber-yellow);
  font-size: 20px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.num-btn.yellow-num:hover {
  background: rgba(255, 255, 0, 0.2);
  box-shadow: 0 0 15px rgba(255, 255, 0, 0.3);
}

.number-input {
  width: 100px;
  text-align: center;
}

.yellow-btn {
  background: linear-gradient(135deg, var(--cyber-yellow) 0%, #ffa500 100%);
  color: var(--cyber-bg-primary);
}

.yellow-btn:hover {
  box-shadow: 0 0 30px rgba(255, 255, 0, 0.5);
}

.action-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
  margin-top: 8px;
}

.btn-icon {
  font-size: 16px;
}

.inline-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
}

.typing-dot.yellow {
  background: var(--cyber-yellow);
  box-shadow: 0 0 10px var(--cyber-yellow);
}

.loading-text-yellow {
  font-size: 13px;
  color: var(--cyber-yellow);
  margin-left: 8px;
}

.inline-error {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 0, 128, 0.1);
  border: 1px solid rgba(255, 0, 128, 0.3);
  color: var(--cyber-pink);
  font-size: 13px;
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.error-icon {
  font-weight: 700;
}

.result-area {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.book-result,
.movie-result {
  clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 15px, 100% 100%, 15px 100%, 0 calc(100% - 15px));
}

.book-result::before,
.movie-result::before {
  background: linear-gradient(90deg, var(--cyber-yellow), #ffa500, var(--cyber-yellow));
}

.result-section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255, 255, 0, 0.2);
  background: rgba(255, 255, 0, 0.05);
}

.section-icon {
  color: var(--cyber-yellow);
  font-size: 10px;
  text-shadow: 0 0 10px var(--cyber-yellow);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
  margin: 0;
}

.section-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--cyber-yellow), transparent);
}

.book-card {
  display: flex;
  gap: 28px;
  padding: 28px;
}

.book-cover {
  flex-shrink: 0;
}

.cover-placeholder {
  width: 160px;
  height: 220px;
  background: linear-gradient(135deg, rgba(255, 255, 0, 0.1), rgba(255, 165, 0, 0.1));
  border: 2px solid rgba(255, 255, 0, 0.3);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 10px 100%, 0 calc(100% - 10px));
  box-shadow: 0 0 20px rgba(255, 255, 0, 0.2);
}

.cover-icon {
  font-size: 48px;
}

.cover-text {
  font-size: 12px;
  color: var(--cyber-yellow);
  text-align: center;
  padding: 0 12px;
  line-height: 1.4;
}

.book-info {
  flex: 1;
}

.book-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--cyber-yellow);
  letter-spacing: 1px;
  margin: 0 0 20px 0;
  text-shadow: 0 0 10px rgba(255, 255, 0, 0.3);
}

.info-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}

.info-label {
  font-size: 13px;
  color: var(--cyber-text-muted);
  min-width: 80px;
  letter-spacing: 1px;
}

.info-value {
  font-size: 14px;
  color: var(--cyber-text-primary);
}

.tag {
  padding: 4px 12px;
  font-size: 12px;
  letter-spacing: 1px;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.yellow-tag {
  background: rgba(255, 255, 0, 0.15);
  border: 1px solid rgba(255, 255, 0, 0.3);
  color: var(--cyber-yellow);
}

.yellow-tag.small {
  padding: 2px 10px;
  font-size: 11px;
}

.rating {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--cyber-yellow);
}

.star {
  color: var(--cyber-yellow);
  text-shadow: 0 0 8px var(--cyber-yellow);
}

.star.yellow {
  color: var(--cyber-yellow);
}

.book-desc {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 0, 0.15);
}

.book-desc p {
  font-size: 14px;
  line-height: 1.8;
  color: var(--cyber-text-secondary);
  margin: 0;
}

.movies-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  padding: 24px;
}

.movie-card {
  background: rgba(255, 255, 0, 0.05);
  border: 1px solid rgba(255, 255, 0, 0.2);
  overflow: hidden;
  transition: all 0.3s ease;
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 10px, 100% 100%, 10px 100%, 0 calc(100% - 10px));
}

.movie-card:hover {
  border-color: rgba(255, 255, 0, 0.5);
  box-shadow: 0 0 25px rgba(255, 255, 0, 0.2);
  transform: translateY(-4px);
}

.movie-poster {
  position: relative;
  height: 160px;
  background: linear-gradient(135deg, rgba(255, 255, 0, 0.08), rgba(255, 165, 0, 0.08));
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.poster-placeholder {
  font-size: 48px;
  opacity: 0.6;
}

.movie-rank {
  position: absolute;
  top: 12px;
  left: 12px;
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, var(--cyber-yellow), #ffa500);
  color: var(--cyber-bg-primary);
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
}

.movie-details {
  padding: 16px;
}

.movie-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--cyber-yellow);
  margin: 0 0 8px 0;
  letter-spacing: 0.5px;
}

.movie-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.meta-item {
  font-size: 12px;
  color: var(--cyber-text-muted);
}

.movie-rating {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--cyber-yellow);
  margin-bottom: 8px;
}

.movie-desc {
  font-size: 12px;
  color: var(--cyber-text-secondary);
  line-height: 1.6;
  margin: 0 0 12px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.movie-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(10, 10, 31, 0.3);
  border-top-color: var(--cyber-bg-primary);
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
