import type { IncomingMessage, ServerResponse } from "node:http";
import handler from "../server/handler";
export default function campus(req: IncomingMessage, res: ServerResponse) {
  return handler(req, res);
}
