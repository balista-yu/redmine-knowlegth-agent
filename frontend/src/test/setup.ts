import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// =============================================================================
// jsdom の window.AbortController / AbortSignal を Node 標準のものに置き換える。
//
// Node 24 + jsdom 25 + undici fetch の組合せでは、jsdom が独自実装の AbortController
// を window に注入する一方、undici (fetch) は Node 標準の AbortSignal を `instanceof`
// で要求する。このため component 側で `new AbortController()` した signal を fetch に
// 渡すと "RequestInit: Expected signal to be an instance of AbortSignal" で弾かれる。
// テスト環境でのみ Node 標準クラスに揃えて互換性を確保する。
// (本番ブラウザでは window.AbortController === fetch が要求するものなので問題なし)
// =============================================================================
if (typeof window !== "undefined") {
  Object.defineProperty(window, "AbortController", {
    configurable: true,
    writable: true,
    value: global.AbortController,
  });
  Object.defineProperty(window, "AbortSignal", {
    configurable: true,
    writable: true,
    value: global.AbortSignal,
  });
}

// 各テスト後に React の DOM ツリーをクリアする (Testing Library 推奨)
afterEach(() => {
  cleanup();
});
