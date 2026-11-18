export const categories = [
  "Food pickup",
  "Errands",
  "Tutoring",
  "Programming",
  "Design",
  "Events",
  "Xerox",
  "Other",
];
export type JobStatus =
  "OPEN" | "ACCEPTED" | "IN_PROGRESS" | "DELIVERED" | "COMPLETED" | "CANCELLED";
export interface Job {
  id: number;
  owner_id: number;
  helper_id: number | null;
  owner_name: string;
  helper_name: string | null;
  title: string;
  description: string;
  category: string;
  reward: number;
  pickup: string;
  destination: string;
  deadline: string;
  status: JobStatus;
  payment_status: "UNPAID" | "COLLECTED";
  payment_method: string | null;
  created_at: string;
  hidden: boolean;
  progress_step?: string;
  original_name?: string;
  content_type?: string;
  file_size?: number;
  xerox_mode?: "BLACK_WHITE" | "COLOR";
  copies?: number;
}
export interface Notice {
  id: number;
  job_id: number;
  message: string;
  is_read: boolean;
  created_at: string;
}
export interface Message {
  id: number;
  sender_id: number;
  sender_name: string;
  body: string;
  created_at: string;
}
export interface Review {
  rating: number;
  comment: string;
  reviewer_name: string;
}
export const money = (n: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(n);
export const statusLabel = (s: string) => s.toLowerCase().replaceAll("_", " ");

export const deliverySteps = (category: string) =>
  category === "Xerox"
    ? [
        ["FILE_RECEIVED", "File received"],
        ["XEROX_COMPLETE", "Xerox complete"],
        ["ON_THE_WAY", "On the way"],
        ["AT_BLOCK", "At your block"],
      ]
    : [
        ["PICKED_UP", "Order picked up"],
        ["ON_THE_WAY", "On the way"],
        ["AT_BLOCK", "At your block"],
      ];
