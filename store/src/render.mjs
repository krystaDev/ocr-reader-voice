// Renderuje grafiki do Sklepu Play z screens.html: npm install && node render.mjs
// Wynik: ../screenshots/phone-N.png (1080 × 1920), ../feature-graphic.png (1024 × 500), ../icon-512.png (512 × 512).
import { chromium } from 'playwright';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, '..');
const page_url = 'file://' + path.join(here, 'screens.html');

async function shoot(scale, jobs) {
  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 }, deviceScaleFactor: scale });
  await page.goto(page_url);
  await page.evaluate(() => document.fonts.ready);
  for (const { id, file, width, height } of jobs) {
    const box = await page.locator('#' + id).boundingBox();
    await page.screenshot({ path: path.join(out, file), clip: { x: box.x, y: box.y, width, height }, fullPage: true });
    console.log(file);
  }
  await browser.close();
}

// Telefon 432 × 768 dp przy gęstości 2,5 → 1080 × 1920 px (9:16).
const phoneScale = 2.5;
await shoot(phoneScale, [1, 2, 3, 4, 5].map(n => ({ id: `shot-${n}`, file: `screenshots/phone-${n}.png`, width: 1080 / phoneScale, height: 1920 / phoneScale })));
await shoot(1, [
  { id: 'feature', file: 'feature-graphic.png', width: 1024, height: 500 },
  { id: 'icon', file: 'icon-512.png', width: 512, height: 512 },
]);
