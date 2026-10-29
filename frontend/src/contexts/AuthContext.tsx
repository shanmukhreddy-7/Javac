import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useRef,
} from "react";
import type { ReactNode } from "react";
import { api, RequestError } from "../services/api";
import { demoEnabled, sampleStudent, completion } from "../services/demo";
import { presentationMode } from "../services/mode";
import type { Student, ProfileInput, Skill } from "../types";
interface AuthValue {
  student: Student | null;
  loading: boolean;
  isDemo: boolean;
  sessionError: string;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  startDemo: () => void;
  saveProfile: (input: ProfileInput) => Promise<void>;
  saveSkills: (skills: Skill[]) => Promise<void>;
  refresh: () => Promise<void>;
  clearSession: () => void;
}
const AuthContext = createContext<AuthValue | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const generation = useRef(0);
  const [student, setStudent] = useState<Student | null>(null);
  const [isDemo, setDemo] = useState(
    () => demoEnabled && sessionStorage.getItem("javac-demo") === "active",
  );
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState("");
  const refresh = useCallback(async () => {
    const request = ++generation.current;
    if (isDemo) {
      setStudent(structuredClone(sampleStudent));
      setLoading(false);
      return;
    }
    setLoading(true);
    setSessionError("");
    try {
      const result = await api<Student>("/api/auth/me");
      if (request === generation.current) setStudent(result);
    } catch (e) {
      if (request !== generation.current) return;
      setStudent(null);
      if (!(e instanceof RequestError && e.status === 401))
        setSessionError((e as Error).message);
    } finally {
      if (request === generation.current) setLoading(false);
    }
  }, [isDemo]);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  const clearSession = () => {
    generation.current++;
    setLoading(false);
    setStudent(null);
    setDemo(false);
    sessionStorage.removeItem("javac-demo");
  };
  const login = async (email: string, password: string) => {
    generation.current++;
    const data = await api<Student>("/api/auth/login", "POST", {
      email,
      ...(presentationMode ? {} : { password }),
    });
    sessionStorage.removeItem("javac-demo");
    setDemo(false);
    setStudent(data);
    setLoading(false);
    setSessionError("");
  };
  const logout = async () => {
    if (!isDemo) await api("/api/auth/logout", "POST");
    clearSession();
  };
  const startDemo = () => {
    if (!demoEnabled) return;
    generation.current++;
    setLoading(false);
    sessionStorage.setItem("javac-demo", "active");
    setDemo(true);
    setStudent(structuredClone(sampleStudent));
    setSessionError("");
  };
  const saveProfile = async (input: ProfileInput) => {
    if (isDemo) {
      setStudent((s) =>
        s ? { ...s, ...input, completion: completion({ ...s, ...input }) } : s,
      );
      return;
    }
    setStudent(await api<Student>("/api/students/me", "PATCH", input));
  };
  const saveSkills = async (skills: Skill[]) => {
    if (isDemo) {
      setStudent((s) =>
        s ? { ...s, skills, completion: completion({ ...s, skills }) } : s,
      );
      return;
    }
    setStudent(
      await api<Student>("/api/students/me/skills", "PUT", {
        skillIds: skills.map((s) => s.id),
      }),
    );
  };
  return (
    <AuthContext.Provider
      value={{
        student,
        loading,
        isDemo,
        sessionError,
        login,
        logout,
        startDemo,
        saveProfile,
        saveSkills,
        refresh,
        clearSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("AuthProvider is required");
  return value;
}
