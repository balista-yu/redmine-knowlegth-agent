import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// 各テスト後に React の DOM ツリーをクリアする (Testing Library 推奨)
afterEach(() => {
  cleanup();
});
