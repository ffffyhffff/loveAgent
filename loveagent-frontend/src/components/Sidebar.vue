<template>
  <aside class="sidebar" :class="{ open: sidebarOpen }">
    <div class="sidebar-header">
      <h3>聊天记录</h3>
      <button class="new-btn glass-btn" @click="$emit('new')">新对话</button>
    </div>
    <div class="conv-list">
      <div v-for="conv in conversations" :key="conv.id"
        class="conv-item" :class="{ active: conv.id === activeId }"
        @click="$emit('switch', conv.id)">
        <div class="conv-info">
          <span class="conv-title">{{ conv.title }}</span>
          <span class="conv-count">{{ conv.messageCount }} 条消息</span>
        </div>
        <button class="del-btn" @click.stop="$emit('delete', conv.id)" title="删除">✕</button>
      </div>
      <div v-if="conversations.length === 0" class="empty-tip">还没有对话记录</div>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  conversations: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
  sidebarOpen: { type: Boolean, default: false },
})

defineEmits(['toggle', 'new', 'switch', 'delete'])
</script>

<style scoped>
.sidebar {
  width: 260px; height: 100vh;
  background: rgba(255,255,255,0.15); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
  border-right: 1px solid rgba(255,255,255,0.25);
  display: flex; flex-direction: column; flex-shrink: 0;
}
.sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px; border-bottom: 1px solid rgba(255,255,255,0.2);
}
.sidebar-header h3 { font-size: 0.9rem; font-weight: 700; }
.new-btn { display: flex; align-items: center; gap: 4px; font-size: 12px; padding: 6px 12px; border-radius: 10px; }

.conv-list { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 2px; }
.conv-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 12px; cursor: pointer;
  transition: all 0.25s var(--transition-spring); position: relative;
}
.conv-item:hover { background: rgba(255,255,255,0.2); }
.conv-item.active { background: rgba(233,30,99,0.1); border: 1px solid rgba(233,30,99,0.12); }
.conv-info { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.conv-title { font-size: 0.82rem; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-count { font-size: 0.68rem; color: var(--color-text-dim); margin-top: 1px; }
.del-btn {
  opacity: 0; background: none; border: none; color: var(--color-text-dim);
  cursor: pointer; padding: 4px; border-radius: 6px; transition: all 0.2s;
}
.conv-item:hover .del-btn { opacity: 1; }
.del-btn:hover { color: #e91e63; background: rgba(233,30,99,0.08); }
.empty-tip { text-align: center; color: var(--color-text-dim); font-size: 0.8rem; padding: 40px 0; }

@media (max-width: 768px) {
  .sidebar {
    position: fixed; left: -280px; top: 0; z-index: 100;
    transition: left 0.3s var(--transition-spring); box-shadow: 4px 0 24px rgba(0,0,0,0.1);
  }
  .sidebar.open { left: 0; }
}
</style>
