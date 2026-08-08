/**
 * Script to replace hardcoded colors with CSS variables in Vue files.
 * Run: node scripts/replace-hardcoded-colors.mjs
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs'
import { join, relative } from 'path'

const SRC_DIR = join(import.meta.dirname, '..', 'src', 'views')

// Replacement rules (order matters - more specific patterns first)
const CSS_REPLACEMENTS = [
  // Pattern: var(--tf-accent, rgb(var(--primary-6))) → var(--tf-accent)
  [/var\(--tf-accent,\s*rgb\(var\(--primary-6\)\)\)/g, 'var(--tf-accent)'],
  // Pattern: var(--tf-accent, rgb(var(--primary-6, ...))) → var(--tf-accent)
  [/var\(--tf-accent,\s*rgb\(var\(--primary-6,\s*[^)]+\)\)\)/g, 'var(--tf-accent)'],
  // Pattern: var(--tf-warning, #d29922) → var(--tf-warning)
  [/var\(--tf-warning,\s*#d29922\)/g, 'var(--tf-warning)'],
  // Pattern: var(--tf-danger, #f85149) → var(--tf-danger)
  [/var\(--tf-danger,\s*#f85149\)/g, 'var(--tf-danger)'],
  // Pattern: var(--tf-success, #3fb950) → var(--tf-success)
  [/var\(--tf-success,\s*#3fb950\)/g, 'var(--tf-success)'],
  // Pattern: var(--tf-purple, #a371f7) → var(--tf-purple)
  [/var\(--tf-purple,\s*#a371f7\)/g, 'var(--tf-purple)'],
  // Pattern: var(--tf-accent, #58a6ff) → var(--tf-accent)
  [/var\(--tf-accent,\s*#58a6ff\)/g, 'var(--tf-accent)'],
  // Pattern: var(--tf-bg-elevated, #2a2d33) → var(--tf-bg-elevated)
  [/var\(--tf-bg-elevated,\s*#2a2d33\)/g, 'var(--tf-bg-elevated)'],
  // Pattern: var(--tf-bg-surface, #22252a) → var(--tf-bg-surface)
  [/var\(--tf-bg-surface,\s*#22252a\)/g, 'var(--tf-bg-surface)'],
  // Pattern: var(--tf-border, #30363d) → var(--tf-border)
  [/var\(--tf-border,\s*#30363d\)/g, 'var(--tf-border)'],
  // Pattern: var(--tf-text-primary, #e6edf3) → var(--tf-text-primary)
  [/var\(--tf-text-primary,\s*#e6edf3\)/g, 'var(--tf-text-primary)'],
  // Pattern: var(--tf-text-secondary, #9ca3af) → var(--tf-text-secondary)
  [/var\(--tf-text-secondary,\s*#9ca3af\)/g, 'var(--tf-text-secondary)'],
  // Pattern: var(--tf-text-tertiary, #6b7280) → var(--tf-text-tertiary)
  [/var\(--tf-text-tertiary,\s*#6b7280\)/g, 'var(--tf-text-tertiary)'],

  // Arco fallback patterns → tf variables
  [/var\(--color-bg-popup,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-popup-bg)'],
  [/var\(--color-bg-popup,\s*var\(--color-bg-2\)\)/g, 'var(--tf-popup-bg)'],
  [/var\(--color-bg-2,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-bg-surface)'],
  [/var\(--color-bg-3,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-bg-elevated)'],
  [/var\(--color-text-1,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-text-primary)'],
  [/var\(--color-text-3,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-text-tertiary)'],
  [/var\(--color-text-4,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-text-muted)'],
  [/var\(--color-border-1,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-border-light)'],
  [/var\(--color-border-2,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-border)'],
  [/var\(--color-border-3,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-border)'],
  [/var\(--color-fill-2,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-fill-medium)'],
  [/var\(--color-fill-3,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-fill-heavy)'],
  [/var\(--color-warning-6,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-warning)'],
  [/var\(--color-danger-light-4,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-danger)'],
  [/var\(--color-danger-6,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-danger)'],
  [/var\(--color-success-6,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-success)'],

  // rgb(var(--primary-6, ...)) → var(--tf-accent)  (standalone)
  [/(?<!var\(--tf-accent,\s*)rgb\(var\(--primary-6(?:,\s*[^)]+)?\)\)/g, 'var(--tf-accent)'],
  // rgb(var(--primary-5, ...)) → var(--tf-accent-hover)
  [/rgb\(var\(--primary-5(?:,\s*[^)]+)?\)\)/g, 'var(--tf-accent-hover)'],
  // rgb(var(--primary-7, ...)) → var(--tf-accent)
  [/rgb\(var\(--primary-7(?:,\s*[^)]+)?\)\)/g, 'var(--tf-accent)'],
  // rgb(var(--green-6)) → var(--tf-success)
  [/rgb\(var\(--green-6\)\)/g, 'var(--tf-success)'],
  // rgb(var(--red-5)) or rgb(var(--red-6)) → var(--tf-danger)
  [/rgb\(var\(--red-[56]\)\)/g, 'var(--tf-danger)'],
  // rgb(var(--blue-6)) → var(--tf-accent)
  [/rgb\(var\(--blue-6\)\)/g, 'var(--tf-accent)'],

  // rgba(var(--primary-6), 0.04-0.06) → var(--tf-accent-subtle)
  [/rgba\(var\(--primary-6\),\s*0\.0[3-6]\)/g, 'var(--tf-accent-subtle)'],
  // rgba(var(--primary-6), 0.08-0.12) → var(--tf-accent-light)
  [/rgba\(var\(--primary-6\),\s*0\.(?:0[7-9]|1[0-2]?)\)/g, 'var(--tf-accent-light)'],
  // rgba(var(--primary-6), 0.15-0.3) → var(--tf-accent-bg)
  [/rgba\(var\(--primary-6\),\s*0\.[1-3]\d*\)/g, 'var(--tf-accent-bg)'],
  // rgba(var(--blue-4), 0.1) → var(--tf-accent-light)
  [/rgba\(var\(--blue-4\),\s*0\.1\)/g, 'var(--tf-accent-light)'],
  // rgba(var(--green-4), 0.15) → var(--tf-success-bg)
  [/rgba\(var\(--green-4\),\s*0\.15\)/g, 'var(--tf-success-bg)'],
  // rgba(var(--red-4), 0.15) → var(--tf-danger-bg)
  [/rgba\(var\(--red-4\),\s*0\.15\)/g, 'var(--tf-danger-bg)'],

  // var(--color-primary-light-2, ...) → var(--tf-accent)
  [/var\(--color-primary-light-2,\s*[^)]+\)/g, 'var(--tf-accent)'],
  // var(--color-primary-light-1, ...) → var(--tf-accent-subtle)
  [/var\(--color-primary-light-1,\s*[^)]+\)/g, 'var(--tf-accent-subtle)'],

  // Box shadows with hardcoded rgba(0,0,0,x) → CSS variables
  [/box-shadow:\s*0\s+2px\s+8px\s+rgba\(0,\s*0,\s*0,\s*0\.\d+\)/g, 'box-shadow: var(--tf-shadow)'],
  [/box-shadow:\s*0\s+4px\s+16px\s+rgba\(0,\s*0,\s*0,\s*0\.\d+\)/g, 'box-shadow: var(--tf-shadow-xl)'],
  [/box-shadow:\s*0\s+8px\s+24px\s+rgba\(0,\s*0,\s*0,\s*0\.\d+\)/g, 'box-shadow: var(--tf-shadow-lg)'],
  [/box-shadow:\s*0\s+1px\s+[34]px\s+rgba\(0,\s*0,\s*0,\s*0\.\d+\)/g, 'box-shadow: var(--tf-shadow-sm)'],

  // Standalone colors in CSS (not in :style bindings)
  [/background:\s*#fff\s*!important/g, 'background: var(--tf-bg-body) !important'],
  [/border:\s*1px\s+solid\s+#ddd\s*!important/g, 'border: 1px solid var(--tf-border) !important'],

  // color: #fff in CSS (text on colored backgrounds) → var(--tf-text-on-accent)
  // Must be careful - only in CSS, not in :style bindings (dynamic)
  // Target: `.class { ... color: #fff; ... }`
  [/(;\s*)color:\s*#fff\s*;/g, '$1color: var(--tf-text-on-accent);'],
  [/({\s*)color:\s*#fff\s*;/g, '$1color: var(--tf-text-on-accent);'],
  [/;\s*color:\s*#fff\s*}/g, '; color: var(--tf-text-on-accent) }'],
  // color:#fff in inline cssText (careful not to break dynamic styles)
  [/color:#fff;/g, 'color:var(--tf-text-on-accent);'],

  // rgba(0,0,0,0.5) background overlays → var(--tf-overlay)
  [/background:\s*rgba\(0,\s*0,\s*0,\s*0\.5\)/g, 'background: var(--tf-overlay)'],
  [/background:\s*rgba\(0,\s*0,\s*0,\s*0\.6\)/g, 'background: var(--tf-overlay)'],
  // rgba(0,0,0,0.05) light overlays
  [/background:\s*rgba\(0,\s*0,\s*0,\s*0\.05\)/g, 'background: var(--tf-fill-light)'],

  // Semantic rgba backgrounds → tf variables
  [/background:\s*rgba\(88,\s*166,\s*255,\s*0\.1[0-2]?\)/g, 'background: var(--tf-accent-bg-light)'],
  [/background:\s*rgba\(63,\s*185,\s*80,\s*0\.(?:08|1[0-2]?|15)\)/g, 'background: var(--tf-success-bg)'],
  [/background:\s*rgba\(248,\s*81,\s*73,\s*0\.(?:08|1[0-2]?)\)/g, 'background: var(--tf-danger-bg)'],
  [/background:\s*rgba\(210,\s*153,\s*34,\s*0\.(?:08|1[0-2]?)\)/g, 'background: var(--tf-warning-bg)'],
  [/background:\s*rgba\(76,\s*175,\s*80,\s*0\.1[0-2]?\)/g, 'background: var(--tf-success-bg)'],
  [/background:\s*rgba\(244,\s*67,\s*54,\s*0\.1[0-2]?\)/g, 'background: var(--tf-danger-bg)'],
  [/background:\s*rgba\(255,\s*152,\s*0,\s*0\.1[0-2]?\)/g, 'background: var(--tf-warning-bg)'],

  // Standalone semantic colors
  [/color:\s*#58a6ff/g, 'color: var(--tf-accent)'],
  [/color:\s*#3fb950/g, 'color: var(--tf-success)'],
  [/color:\s*#d29922/g, 'color: var(--tf-warning)'],
  [/color:\s*#f85149/g, 'color: var(--tf-danger)'],
  [/color:\s*#a371f7/g, 'color: var(--tf-purple)'],
  [/color:\s*#f0883e/g, 'color: var(--tf-warning)'],
  
  // var(--color-success, #xxx) → var(--tf-success)
  [/var\(--color-success,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-success)'],
  // var(--color-danger, #xxx) → var(--tf-danger)
  [/var\(--color-danger,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-danger)'],
  // var(--accent-orange, #xxx) → var(--tf-warning)
  [/var\(--accent-orange,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-warning)'],
  // var(--tf-error, #xxx) → var(--tf-danger)
  [/var\(--tf-error,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-danger)'],

  // var(--color-warning-light-1, rgba(...)) → var(--tf-warning-bg)
  [/var\(--color-warning-light-1,\s*rgba\([^)]+\)\)/g, 'var(--tf-warning-bg)'],
  // var(--color-success-light-1, rgba(...)) → var(--tf-success-bg)
  [/var\(--color-success-light-1,\s*rgba\([^)]+\)\)/g, 'var(--tf-success-bg)'],
  // var(--color-fill-2, rgba(...)) → var(--tf-fill-medium)
  [/var\(--color-fill-2,\s*rgba\([^)]+\)\)/g, 'var(--tf-fill-medium)'],
  // var(--color-purple-light-1, rgba(...)) → var(--tf-purple-bg)
  [/var\(--color-purple-light-1,\s*rgba\([^)]+\)\)/g, 'var(--tf-purple-bg)'],
  // var(--color-border-2, rgba(...)) → var(--tf-border)
  [/var\(--color-border-2,\s*rgba\([^)]+\)\)/g, 'var(--tf-border)'],
  // var(--color-warning-light-4, #xxx) → var(--tf-warning)
  [/var\(--color-warning-light-4,\s*#[0-9a-fA-F]{3,8}\)/g, 'var(--tf-warning)'],
  // color: #b388ff → var(--tf-purple)
  [/color:\s*#b388ff/g, 'color: var(--tf-purple)'],

  // Arco theme-responsive patterns: rgba(var(--warning-6), x) → var(--tf-warning-bg) for < 0.15
  [/rgba\(var\(--warning-6\),\s*0\.0[4-8]\)/g, 'var(--tf-warning-bg)'],
  [/rgba\(var\(--danger-6\),\s*0\.0[4-9]\)/g, 'var(--tf-danger-bg)'],
  [/rgba\(var\(--danger-6\),\s*0\.1[0-5]?\)/g, 'var(--tf-danger-bg)'],
  [/rgba\(var\(--green-6\),\s*0\.0[4-9]\)/g, 'var(--tf-success-bg)'],
  [/rgba\(var\(--green-6\),\s*0\.1[0-5]?\)/g, 'var(--tf-success-bg)'],
  [/rgba\(var\(--success-6\),\s*0\.0[4-9]\)/g, 'var(--tf-success-bg)'],
  [/rgba\(var\(--arcoblue-6\),\s*0\.0[4-9]\)/g, 'var(--tf-accent-light)'],

  // Standalone automation/workflow colors → use wf- variables
  // #1e3a5f (dark blue bg) → var(--tf-accent-bg)
  [/background:\s*#1e3a5f/g, 'background: var(--tf-accent-bg)'],
  // #064e3b (dark green bg) → var(--tf-success-bg)
  [/background:\s*#064e3b/g, 'background: var(--tf-success-bg)'],
  // #450a0a (dark red bg) → var(--tf-danger-bg)
  [/background:\s*#450a0a/g, 'background: var(--tf-danger-bg)'],
  // #3b82f6 as accent → var(--tf-accent)
  [/color:\s*#3b82f6/g, 'color: var(--tf-accent)'],
  [/border-left-color:\s*#3b82f6/g, 'border-left-color: var(--tf-accent)'],
  [/border:\s*1px\s+solid\s+#3b82f6/g, 'border: 1px solid var(--tf-accent)'],
  // #93c5fd (light blue text) → var(--tf-accent)
  [/color:\s*#93c5fd/g, 'color: var(--tf-accent)'],
  // #6ee7b7 (green text) → var(--tf-success)
  [/color:\s*#6ee7b7/g, 'color: var(--tf-success)'],
  // #fca5a5 (red text) → var(--tf-danger)
  [/color:\s*#fca5a5/g, 'color: var(--tf-danger)'],
  // #e2e8f0 → var(--tf-text-primary)
  [/color:\s*#e2e8f0/g, 'color: var(--tf-text-primary)'],
  // #475569 → var(--tf-text-muted)
  [/color:\s*#475569/g, 'color: var(--tf-text-muted)'],
]

// Template/inline style replacements (more conservative)
const INLINE_REPLACEMENTS = [
  // style="color: #d29922..." → style="color: var(--tf-warning)..."
  [/style="([^"]*?)color:\s*#d29922([^"]*?)"/g, 'style="$1color: var(--tf-warning)$2"'],
  // color: '#fff' in :style bindings → use CSS variable string
  [/color: '#fff'/g, "color: 'var(--tf-text-on-accent)'"],
]

function getAllVueFiles(dir) {
  const files = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const stat = statSync(full)
    if (stat.isDirectory()) {
      files.push(...getAllVueFiles(full))
    } else if (entry.endsWith('.vue') || entry.endsWith('.css')) {
      files.push(full)
    }
  }
  return files
}

let totalReplacements = 0
const changedFiles = []

const files = getAllVueFiles(SRC_DIR)
for (const file of files) {
  let content = readFileSync(file, 'utf8')
  const original = content
  
  for (const [pattern, replacement] of CSS_REPLACEMENTS) {
    content = content.replace(pattern, replacement)
  }
  for (const [pattern, replacement] of INLINE_REPLACEMENTS) {
    content = content.replace(pattern, replacement)
  }
  
  if (content !== original) {
    writeFileSync(file, content, 'utf8')
    const rel = relative(join(import.meta.dirname, '..'), file)
    const count = (original.length - content.length !== 0) ? '✓' : '~'
    changedFiles.push(rel)
  }
}

console.log(`Modified ${changedFiles.length} files:`)
changedFiles.forEach(f => console.log(`  ${f}`))

// Verify reduction
let before = 0
let after = 0
const colorPattern = /#[0-9a-fA-F]{3,8}\b|rgba?\([^)]+\)/g

for (const file of files) {
  const content = readFileSync(file, 'utf8')
  const matches = content.match(colorPattern) || []
  after += matches.length
}

console.log(`\nRemaining hardcoded colors in views/: ${after}`)
