import { z } from "zod";
export const passwordSchema = z
  .string()
  .min(12, "Use at least 12 characters.")
  .max(64, "Use at most 64 characters.")
  .regex(/[a-z]/, "Add a lowercase letter.")
  .regex(/[A-Z]/, "Add an uppercase letter.")
  .regex(/[0-9]/, "Add a number.")
  .regex(/[^a-zA-Z0-9]/, "Add a symbol.");
export const loginSchema = z.object({
  email: z.email("Enter a valid university email.").max(254),
  password: z.string().min(1, "Enter your password.").max(64),
});
export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, "Enter your full name.").max(80),
    email: z.email("Enter a valid university email.").max(254),
    registrationNumber: z
      .string()
      .regex(/^[a-zA-Z0-9-]{3,40}$/, "Use 3–40 letters, numbers or hyphens."),
    department: z.string().trim().min(1, "Choose your department.").max(80),
    studyYear: z.number().min(1).max(6),
    password: passwordSchema,
    confirmPassword: z.string(),
    termsAccepted: z
      .boolean()
      .refine((v) => v, "Accept the terms to continue."),
  })
  .refine((v) => v.password === v.confirmPassword, {
    message: "Passwords do not match.",
    path: ["confirmPassword"],
  });
export const profileSchema = z.object({
  fullName: z.string().trim().min(2, "Enter your full name.").max(80),
  department: z.string().trim().min(1, "Enter your department.").max(80),
  studyYear: z.number().min(1).max(6),
  bio: z.string().max(500, "Keep your bio under 500 characters."),
  preferredLocation: z.string().max(100),
});
export const departments = [
  "Computer Science",
  "Electronics & Communication",
  "Electrical Engineering",
  "Mechanical Engineering",
  "Civil Engineering",
  "Business & Management",
  "Arts & Humanities",
  "Sciences",
  "Other",
];

export const presentationRegistrationSchema = z.object({
  fullName: z.string().trim().min(2, "Enter your full name.").max(80),
  email: z.email("Enter a valid university email.").max(254),
  registrationNumber: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9-]{3,40}$/, "Use 3–40 letters, numbers or hyphens."),
  department: z.string().trim().min(1, "Choose your department.").max(80),
  studyYear: z.number().min(1).max(6),
  termsAccepted: z.boolean().refine((v) => v, "Accept the terms to continue."),
});
