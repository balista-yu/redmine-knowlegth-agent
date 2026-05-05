/**
 * `/api/chat` SSE エンドポイントの DTO 型。
 * 受信側 (frontend) では `ChatStreamEvent` で discriminated union 化する。
 */

export type TicketHit = {
  ticketId: number;
  subject: string;
  url: string;
  snippet: string;
  score: number;
  status: string;
  tracker: string;
  projectName: string;
};

export type ChatDeltaEvent = {
  type: "delta";
  text: string;
};

export type ChatSourcesEvent = {
  type: "sources";
  items: TicketHit[];
};

export type ChatDoneEvent = {
  type: "done";
  conversationId: string;
};

export type ChatErrorEvent = {
  type: "error";
  code: string;
  message: string;
};

export type ChatStreamEvent =
  | ChatDeltaEvent
  | ChatSourcesEvent
  | ChatDoneEvent
  | ChatErrorEvent;

/** UI 表示用の会話メッセージ。 */
export type ChatMessage = {
  role: "user" | "assistant";
  content: string;
};
