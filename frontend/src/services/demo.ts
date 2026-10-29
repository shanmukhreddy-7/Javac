import type { Student, Skill } from "../types";
export const demoEnabled = import.meta.env.VITE_DEMO_MODE === "true";
export const demoSkills: Skill[] = [
  { id: 1, name: "Java", category: "Programming" },
  { id: 2, name: "Python", category: "Programming" },
  { id: 3, name: "Web development", category: "Programming" },
  { id: 4, name: "C++", category: "Programming" },
  { id: 5, name: "Mathematics", category: "Tutoring" },
  { id: 6, name: "Physics", category: "Tutoring" },
  { id: 7, name: "English", category: "Tutoring" },
  { id: 8, name: "Logo design", category: "Graphic design" },
  { id: 9, name: "Figma", category: "Graphic design" },
  { id: 10, name: "Illustration", category: "Graphic design" },
  { id: 11, name: "Video editing", category: "Video editing" },
  { id: 12, name: "Motion graphics", category: "Video editing" },
  { id: 13, name: "Photography", category: "Photography" },
  { id: 14, name: "Photo editing", category: "Photography" },
  { id: 15, name: "Presentation design", category: "Presentation design" },
  { id: 16, name: "Report formatting", category: "Document assistance" },
  { id: 17, name: "Proofreading", category: "Document assistance" },
  { id: 18, name: "Campus errands", category: "Delivery and errands" },
  { id: 19, name: "Event coordination", category: "Event support" },
  { id: 20, name: "Social media design", category: "Graphic design" },
];
export const sampleStudent: Student = {
  id: 0,
  fullName: "Alex Morgan",
  email: "alex@demo.example",
  registrationNumber: "DEMO-2026-001",
  department: "Computer Science",
  studyYear: 2,
  bio: "Curious about code, good design, and making campus life a little easier.",
  preferredLocation: "Central library",
  emailVerified: false,
  accountStatus: "DEMO",
  createdAt: "2026-10-01T00:00:00Z",
  skills: [demoSkills[0], demoSkills[8], demoSkills[14]],
  completion: 100,
};
export function completion(student: Student) {
  return (
    60 +
    (student.bio.trim() ? 15 : 0) +
    (student.preferredLocation.trim() ? 10 : 0) +
    (student.skills.length ? 15 : 0)
  );
}
