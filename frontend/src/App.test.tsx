import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { App } from "./App";

describe("App", () => {
  it("ChatPage が描画される (タイトルが表示される)", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", { name: "Redmine Knowledge Agent" }),
    ).toBeInTheDocument();
  });

  it("メッセージ入力欄がある", () => {
    render(<App />);
    expect(screen.getByLabelText("メッセージ入力")).toBeInTheDocument();
  });
});
