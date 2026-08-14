import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      // Minden API-hívás a src/api/client.ts-en keresztül menjen — az kezeli
      // a 401-interceptort. Nyers axios import máshol kikerüli azt (ld.
      // plans/frontend_redesign_2026.md 10.2 szekció).
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'axios',
              message:
                'Ne importálj közvetlenül axios-t — használd a src/api/client.ts-t, különben kikerülöd a 401-interceptort.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['src/api/client.ts'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
])
