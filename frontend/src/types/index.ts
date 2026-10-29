export interface Skill {
  id: number;
  name: string;
  category: string;
}
export interface Student {
  id: number;
  fullName: string;
  email: string;
  registrationNumber: string;
  department: string;
  studyYear: number;
  bio: string;
  preferredLocation: string;
  emailVerified: boolean;
  accountStatus: string;
  createdAt: string;
  skills: Skill[];
  completion: number;
}
export interface ProfileInput {
  fullName: string;
  department: string;
  studyYear: number;
  bio: string;
  preferredLocation: string;
}
export interface ApiError {
  message: string;
  fields?: Record<string, string>;
}

export interface CampusPerson {
  id: number;
  fullName: string;
  department: string;
  studyYear: number;
  bio: string;
  preferredLocation: string;
  skills: Skill[];
}
export interface CampusRequest {
  id: number;
  senderId: number;
  recipientId: number;
  senderName: string;
  recipientName: string;
  title: string;
  description: string;
  status: "PENDING" | "ACCEPTED" | "DECLINED";
  createdAt: string;
  updatedAt: string;
}
