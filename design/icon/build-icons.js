#!/usr/bin/env node
/**
 * MediaMix 图标构建脚本
 * ---------------------------------------------------------------
 * 从 design/icon/*.svg 渲染出各平台所需的全部图标资源。
 *
 *   Android  legacy 方形 / 圆形 ic_launcher（mipmap-{m,h,xh,xxh,xxxh}dpi）
 *            adaptive icon 前景 / 背景 / 单色层 + mipmap-anydpi-v26 XML
 *   Desktop  icon.ico（多尺寸，供 Compose Desktop 打包用）
 *   其它     512×512 PNG（应用商店 / 文档用）
 *
 * 用法：node design/icon/build-icons.js
 * 依赖：sharp（已装在 ~/.workbuddy/binaries/node/workspace/node_modules）
 */
const path = require('path');
const fs = require('fs');
const sharp = require('sharp');

const ICON_DIR = __dirname;
const ROOT = path.resolve(ICON_DIR, '..', '..');
const ANDROID_RES = path.join(ROOT, 'androidApp', 'src', 'main', 'res');
const DESKTOP_RES = path.join(ROOT, 'desktopApp', 'src', 'desktopMain', 'resources');

// 渲染时的光栅化倍率：先放大再降采样，保证各尺寸边缘平滑
const DENSITY = 288;

const DENSITIES = [
  ['mdpi', 1],
  ['hdpi', 1.5],
  ['xhdpi', 2],
  ['xxhdpi', 3],
  ['xxxhdpi', 4],
];

/** 把某个 SVG 渲染成指定边长的 PNG buffer（超采样后缩放） */
async function render(svgFile, size) {
  return sharp(path.join(ICON_DIR, svgFile), { density: DENSITY })
    .resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png({ compressionLevel: 9 })
    .toBuffer();
}

async function writePng(buf, ...segments) {
  const dest = path.join(...segments);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.writeFileSync(dest, buf);
  return dest;
}

async function main() {
  const written = [];

  // ---------- 1. Android legacy 方形 ----------
  for (const [d, scale] of DENSITIES) {
    const size = Math.round(48 * scale);
    written.push(await writePng(await render('appicon.svg', size), ANDROID_RES, `mipmap-${d}`, 'ic_launcher.png'));
  }

  // ---------- 2. Android legacy 圆形 ----------
  for (const [d, scale] of DENSITIES) {
    const size = Math.round(48 * scale);
    written.push(await writePng(await render('appicon_round.svg', size), ANDROID_RES, `mipmap-${d}`, 'ic_launcher_round.png'));
  }

  // ---------- 3. Adaptive icon 三层（108dp 基准） ----------
  const layers = [
    ['ic_foreground.svg', 'ic_launcher_foreground.png'],
    ['ic_background.svg', 'ic_launcher_background.png'],
    ['ic_monochrome.svg', 'ic_launcher_monochrome.png'],
  ];
  for (const [d, scale] of DENSITIES) {
    const size = Math.round(108 * scale);
    for (const [svg, out] of layers) {
      written.push(await writePng(await render(svg, size), ANDROID_RES, `mipmap-${d}`, out));
    }
  }

  // ---------- 4. adaptive icon 的 XML 描述 ----------
  const anydpi = path.join(ANDROID_RES, 'mipmap-anydpi-v26');
  fs.mkdirSync(anydpi, { recursive: true });

  const adaptiveXml = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
`;
  for (const name of ['ic_launcher.xml', 'ic_launcher_round.xml']) {
    const p = path.join(anydpi, name);
    fs.writeFileSync(p, adaptiveXml);
    written.push(p);
  }

  // ---------- 5. 512×512 商店图标 ----------
  written.push(await writePng(await render('appicon.svg', 512), ICON_DIR, 'ic_launcher-512.png'));

  // ---------- 6. Windows .ico ----------
  // 直接嵌入「每个尺寸各自光栅化」的 PNG，而不是从大图缩放 —— 小尺寸下笔画更清晰。
  // ICO 容器：ICONDIR(6B) + ICONDIRENTRY*N(16B each) + 各图 PNG 数据。
  const icoSizes = [16, 20, 24, 32, 48, 64, 128, 256];
  const icoImages = [];
  for (const s of icoSizes) icoImages.push(await render('appicon.svg', s));

  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // type: 1 = ICON
  header.writeUInt16LE(icoImages.length, 4);

  const dir = Buffer.alloc(16 * icoImages.length);
  let offset = 6 + dir.length;
  icoImages.forEach((buf, i) => {
    const s = icoSizes[i];
    const o = i * 16;
    dir.writeUInt8(s >= 256 ? 0 : s, o + 0); // width（0 表示 256）
    dir.writeUInt8(s >= 256 ? 0 : s, o + 1); // height
    dir.writeUInt8(0, o + 2); // 调色板数（真彩色为 0）
    dir.writeUInt8(0, o + 3); // reserved
    dir.writeUInt16LE(1, o + 4); // color planes
    dir.writeUInt16LE(32, o + 6); // bits per pixel
    dir.writeUInt32LE(buf.length, o + 8);
    dir.writeUInt32LE(offset, o + 12);
    offset += buf.length;
  });

  fs.mkdirSync(DESKTOP_RES, { recursive: true });
  const icoPath = path.join(DESKTOP_RES, 'icon.ico');
  fs.writeFileSync(icoPath, Buffer.concat([header, dir, ...icoImages]));
  written.push(icoPath);

  // ---------- 7. Desktop 用的 PNG（供 Linux/macOS 或文档备用） ----------
  written.push(await writePng(await render('appicon.svg', 512), DESKTOP_RES, 'icon.png'));

  console.log(`已生成 ${written.length} 个文件`);
  for (const w of written) console.log('  ' + path.relative(ROOT, w).replace(/\\/g, '/'));
}

main().catch((e) => {
  console.error('生成失败:', e.message);
  process.exit(1);
});
