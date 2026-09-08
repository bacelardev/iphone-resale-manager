import { existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
if (!process.env.CI && process.env.HUSKY !== '0' && existsSync('../.git')) {
  execFileSync(process.execPath, ['frontend/node_modules/husky/bin.js', 'frontend/.husky'], {
    cwd: '..',
    stdio: 'inherit',
  });
}
