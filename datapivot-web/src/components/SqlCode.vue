<script setup lang="ts">
import { computed } from 'vue'

/**
 * SQL 代码高亮组件（轻量实现，不引入第三方高亮库）
 *
 * 安全说明：内容先做 HTML 转义（& < >），再按注释/字符串/关键字/数字
 * 追加高亮 span，最终通过 v-html 渲染——由于原始内容已全部转义，
 * 不存在 XSS 注入风险（此组件不接受渲染未转义的不可信内容）。
 */
const props = defineProps<{ code: string }>()

const KEYWORDS = [
  'CREATE',
  'TABLE',
  'ALTER',
  'ADD',
  'COLUMN',
  'MODIFY',
  'DROP',
  'PRIMARY',
  'KEY',
  'NOT',
  'NULL',
  'COMMENT',
  'ON',
  'IS',
  'TYPE',
  'SET',
  'SELECT',
  'INSERT',
  'INTO',
  'VALUES',
  'UPDATE',
  'DELETE',
  'FROM',
  'WHERE',
  'AND',
  'OR',
  'DEFAULT',
  'CONSTRAINT',
  'UNIQUE',
  'INDEX',
]

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const highlighted = computed(() => {
  let html = escapeHtml(props.code)
  // 1. 行注释
  html = html.replace(/(--[^\n]*)/g, '<span class="tok-comment">$1</span>')
  // 2. 字符串字面量
  html = html.replace(/('(?:[^'\n]|'')*')/g, '<span class="tok-string">$1</span>')
  // 3. 数字
  html = html.replace(/\b(\d+)\b/g, '<span class="tok-number">$1</span>')
  // 4. 关键字
  html = html.replace(new RegExp(`\\b(${KEYWORDS.join('|')})\\b`, 'g'), '<span class="tok-keyword">$1</span>')
  return html
})
</script>

<template>
  <!-- 安全说明：highlighted 已对原始内容做完整 HTML 转义后才注入高亮标签，不存在 XSS 风险 -->
  <!-- eslint-disable-next-line vue/no-v-html -->
  <pre class="sql-code" v-html="highlighted"></pre>
</template>
