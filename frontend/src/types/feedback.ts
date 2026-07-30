export interface FeedbackIssueResponse {
  number: number;
  title: string;
  bodyPreview: string;
  url: string;
  state: "open" | "closed";
  authorUsername: string | null;
  createdAt: string;
}

export interface CreateFeedbackRequest {
  title: string;
  description: string;
}
