import prettierConfig from 'eslint-config-prettier'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

// ESLint 9 扁平配置：Vue3 + TypeScript + Prettier
// 注意：vue 配置必须在 tseslint 之后展开，否则 tseslint 会覆盖 .vue 文件的 vue-eslint-parser
export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', '*.config.*'] },
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
  },
  prettierConfig,
  {
    rules: {
      // 视图组件按业务命名，允许单词组件名（如 SqlCode）
      'vue/multi-word-component-names': 'off',
      // 严格禁止 any 类型
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    },
  },
)
