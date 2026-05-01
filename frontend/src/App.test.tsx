import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { App } from "./App";

describe("App", () => {
  it("ヘッダにアプリ名が表示される", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Redmine Knowledge Agent" }),
    ).toBeInTheDocument();
  });

  it("Phase 3 雛形のプレースホルダ説明が表示される", () => {
    render(<App />);
    expect(
      screen.getByText(/Frontend skeleton ready/i),
    ).toBeInTheDocument();
  });
});
