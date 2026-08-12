import pluginVue from 'eslint-plugin-vue'
import tsParser from '@typescript-eslint/parser'
import tsPlugin from '@typescript-eslint/eslint-plugin'
import vueParser from 'vue-eslint-parser'

export default [
  // 忽略构建产物
  {
    ignores: ['dist/**', 'node_modules/**', '*.d.ts']
  },

  // ===== TypeScript 文件 =====
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tsParser,
      parserOptions: { ecmaVersion: 'latest', sourceType: 'module' }
    },
    plugins: { '@typescript-eslint': tsPlugin },
    rules: {
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_|^e$',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_|^e$',
        ignoreRestSiblings: true
      }],
      'no-unused-vars': 'off' // 由 @typescript-eslint/no-unused-vars 接管
    }
  },

  // ===== Vue 文件 =====
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tsParser,
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue']
      }
    },
    plugins: {
      vue: pluginVue,
      '@typescript-eslint': tsPlugin
    },
    rules: {
      // 未使用变量（含 script setup 中的 import）
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_|^e$',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_|^e$',
        ignoreRestSiblings: true
      }],
      'no-unused-vars': 'off',

      // Vue 模板：未使用组件、未定义变量
      'vue/no-unused-components': 'warn',
      'vue/no-unused-vars': ['warn', { ignorePattern: '^_' }],

      // Vue 基础规范
      'vue/multi-word-component-names': 'off',   // 允许单词组件名（如 index.vue）
      'vue/require-v-for-key': 'error',
      'vue/no-use-v-if-with-v-for': 'error',
      'vue/no-template-shadow': 'warn',
      'vue/no-dupe-keys': 'error',

      // 允许 any（项目内大量使用）
      '@typescript-eslint/no-explicit-any': 'off'
    }
  }
]
