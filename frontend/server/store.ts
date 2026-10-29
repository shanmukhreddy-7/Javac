import { DatabaseSync } from "node:sqlite";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { neon } from "@neondatabase/serverless";
import type { Student, CampusRequest } from "../src/types/index";
type Row = Record<string, unknown>;
export interface SqlDatabase {
  query(sql: string, args?: Array<string | number>): Promise<Row[]>;
  close?: () => void;
}
export function sqliteDatabase(filename: string): SqlDatabase {
  if (filename !== ":memory:")
    mkdirSync(dirname(filename), { recursive: true });
  const db = new DatabaseSync(filename);
  db.exec(
    "PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;",
  );
  return {
    query: async (sql, args = []) => db.prepare(sql).all(...args) as Row[],
    close: () => db.close(),
  };
}
function postgresDatabase(url: string): SqlDatabase {
  const sql = neon(url);
  return {
    query: async (text, args = []) => {
      let parameter = 0;
      return (await sql.query(
        text.replace(/\?/g, () => "$" + ++parameter),
        args,
      )) as Row[];
    },
  };
}
export class CampusStore {
  private readonly ready: Promise<void>;
  constructor(
    private db: SqlDatabase,
    postgres = false,
  ) {
    const id = postgres
      ? "BIGSERIAL PRIMARY KEY"
      : "INTEGER PRIMARY KEY AUTOINCREMENT";
    this.ready = (async () => {
      await db.query(
        `CREATE TABLE IF NOT EXISTS campus_profiles (id ${id}, email TEXT NOT NULL UNIQUE, registration_number TEXT NOT NULL UNIQUE, document TEXT NOT NULL)`,
      );
      await db.query(
        `CREATE TABLE IF NOT EXISTS campus_requests (id ${id}, sender_id BIGINT NOT NULL REFERENCES campus_profiles(id), recipient_id BIGINT NOT NULL REFERENCES campus_profiles(id), title TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','DECLINED')), created_at TEXT NOT NULL, updated_at TEXT NOT NULL)`,
      );
      await db.query(
        "CREATE INDEX IF NOT EXISTS campus_requests_recipient ON campus_requests(recipient_id)",
      );
    })();
  }
  private student(row: Row): Student {
    return {
      ...JSON.parse(String(row.document)),
      id: Number(row.id),
    } as Student;
  }
  async find(id: number) {
    await this.ready;
    const rows = await this.db.query(
      "SELECT * FROM campus_profiles WHERE id=?",
      [id],
    );
    return rows[0] ? this.student(rows[0]) : null;
  }
  async byEmail(email: string) {
    await this.ready;
    const rows = await this.db.query(
      "SELECT * FROM campus_profiles WHERE email=?",
      [email],
    );
    return rows[0] ? this.student(rows[0]) : null;
  }
  async create(student: Student) {
    await this.ready;
    const rows = await this.db.query(
      "INSERT INTO campus_profiles (email,registration_number,document) VALUES (?,?,?) RETURNING *",
      [student.email, student.registrationNumber, JSON.stringify(student)],
    );
    return this.student(rows[0]);
  }
  async save(student: Student) {
    await this.ready;
    const rows = await this.db.query(
      "UPDATE campus_profiles SET document=? WHERE id=? RETURNING *",
      [JSON.stringify(student), student.id],
    );
    return rows[0] ? this.student(rows[0]) : null;
  }
  async people(except: number) {
    await this.ready;
    const rows = await this.db.query(
      "SELECT * FROM campus_profiles WHERE id<>? ORDER BY id DESC LIMIT 200",
      [except],
    );
    return rows.map((r) => {
      const {
        id,
        fullName,
        department,
        studyYear,
        bio,
        preferredLocation,
        skills,
      } = this.student(r);
      return {
        id,
        fullName,
        department,
        studyYear,
        bio,
        preferredLocation,
        skills,
      };
    });
  }
  async send(
    sender: number,
    recipient: number,
    title: string,
    description: string,
  ) {
    await this.ready;
    const now = new Date().toISOString();
    const rows = await this.db.query(
      "INSERT INTO campus_requests (sender_id,recipient_id,title,description,status,created_at,updated_at) VALUES (?,?,?,?,'PENDING',?,?) RETURNING id",
      [sender, recipient, title, description, now, now],
    );
    return Number(rows[0].id);
  }
  async requests(user: number): Promise<CampusRequest[]> {
    await this.ready;
    const rows = await this.db.query(
      "SELECT r.*, s.document AS sender_document, p.document AS recipient_document FROM campus_requests r JOIN campus_profiles s ON s.id=r.sender_id JOIN campus_profiles p ON p.id=r.recipient_id WHERE r.sender_id=? OR r.recipient_id=? ORDER BY r.id DESC LIMIT 200",
      [user, user],
    );
    return rows.map((r) => ({
      id: Number(r.id),
      senderId: Number(r.sender_id),
      recipientId: Number(r.recipient_id),
      senderName: JSON.parse(String(r.sender_document)).fullName,
      recipientName: JSON.parse(String(r.recipient_document)).fullName,
      title: String(r.title),
      description: String(r.description),
      status: String(r.status) as CampusRequest["status"],
      createdAt: String(r.created_at),
      updatedAt: String(r.updated_at),
    }));
  }
  async respond(
    id: number,
    recipient: number,
    status: "ACCEPTED" | "DECLINED",
  ) {
    await this.ready;
    const rows = await this.db.query(
      "UPDATE campus_requests SET status=?, updated_at=? WHERE id=? AND recipient_id=? AND status='PENDING' RETURNING id",
      [status, new Date().toISOString(), id, recipient],
    );
    return rows.length > 0;
  }
}
let store: CampusStore | undefined;
export function getStore() {
  if (store) return store;
  const url = process.env.DATABASE_URL;
  if (process.env.VERCEL && !url) throw new Error("SHARED_DATABASE_MISSING");
  store = new CampusStore(
    url
      ? postgresDatabase(url)
      : sqliteDatabase(
          resolve(process.env.JAVAC_DATA_DIR ?? ".local-data", "campus.sqlite"),
        ),
    !!url,
  );
  return store;
}
