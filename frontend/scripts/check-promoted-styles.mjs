/**
 * Five views each carried their own near-identical copy of the list-page CSS
 * before those classes moved to src/styles/patterns.css. This fails the build
 * if a scoped block starts redeclaring them, which is how the duplication
 * would come back.
 */
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

const PROMOTED = ['.toolbar', '.search', '.filters', '.chip', '.empty', '.pager', '.pgbtn']

async function vueFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  const found = await Promise.all(
    entries.map((entry) => {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) return vueFiles(path)
      return entry.name.endsWith('.vue') ? [path] : []
    }),
  )
  return found.flat()
}

const violations = []

for (const file of await vueFiles('src')) {
  const source = await readFile(file, 'utf8')
  const styleBlock = source.slice(source.indexOf('<style'))
  if (!styleBlock) continue

  for (const selector of PROMOTED) {
    // Matches the selector only where a rule is being declared, so a usage
    // like `.summary .empty { ... }` in a view is still allowed.
    if (new RegExp(`^\\s*\\${selector}[\\s,:.{]`, 'm').test(styleBlock)) {
      violations.push(`${file} redeclares ${selector}`)
    }
  }
}

if (violations.length > 0) {
  console.error('Promoted styles redeclared in a component:\n')
  for (const violation of violations) console.error(`  ${violation}`)
  console.error('\nThese live in src/styles/patterns.css. Use them, or pick a different class name.')
  process.exit(1)
}

console.log('No promoted styles redeclared.')
