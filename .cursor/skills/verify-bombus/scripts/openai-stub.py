from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json

def read_chunked(rfile):
    chunks = []
    while True:
        line = rfile.readline()
        if not line:
            break
        size_str = line.strip().split(b";", 1)[0]
        size = int(size_str, 16)
        if size == 0:
            # trailer
            while True:
                trailer = rfile.readline()
                if trailer in (b"\r\n", b"\n", b""):
                    break
            break
        chunk = rfile.read(size)
        chunks.append(chunk)
        rfile.read(2)  # CRLF
    return b"".join(chunks)

def user_text(messages):
    for msg in reversed(messages):
        if msg.get("role") == "user" and isinstance(msg.get("content"), str):
            return msg["content"].lower()
    return ""

def has_tool_result(messages):
    return any(m.get("role") == "tool" for m in messages)

def tool_call(name, args, call_id="call_verify_1"):
    return {"id": call_id, "type": "function", "function": {"name": name, "arguments": json.dumps(args)}}

def completion(body):
    messages = body.get("messages") or []
    text = user_text(messages)
    print(f"USER_TEXT={text!r}", flush=True)
    if has_tool_result(messages):
        tool_payloads = [m.get("content", "") for m in messages if m.get("role") == "tool"]
        last = tool_payloads[-1] if tool_payloads else "{}"
        message = {"role": "assistant", "content": f"Ok. Resultado da ferramenta: {last[:400]}"}
    elif any(k in text for k in ("liste", "listar")):
        message = {"role": "assistant", "content": None, "tool_calls": [tool_call("list_colmeias", {"limit": 20})]}
    elif any(k in text for k in ("criar", "nova colmeia", "cadastrar")):
        message = {"role": "assistant", "content": None, "tool_calls": [tool_call("create_colmeia", {"speciesId": 1})]}
    elif any(k in text for k in ("atualizar", "mudar status")):
        message = {"role": "assistant", "content": None, "tool_calls": [tool_call("update_colmeia", {"code": 1, "statusId": 1})]}
    elif any(k in text for k in ("perder", "excluir", "remover", "apagar", "soft")):
        message = {"role": "assistant", "content": None, "tool_calls": [tool_call("soft_delete_colmeia", {"code": 1})]}
    elif any(k in text for k in ("quantas", "contar")):
        message = {"role": "assistant", "content": None, "tool_calls": [tool_call("count_colmeias", {"speciesId": None, "statusId": None, "groupBy": []})]}
    else:
        message = {"role": "assistant", "content": "Posso contar, listar, criar, atualizar ou marcar colmeias como perdidas."}
    return {"id": "chatcmpl-verify", "object": "chat.completion", "choices": [{"index": 0, "message": message, "finish_reason": "stop"}]}

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *args):
        return
    def do_POST(self):
        te = (self.headers.get("Transfer-Encoding") or "").lower()
        length = int(self.headers.get("Content-Length") or "0")
        if "chunked" in te:
            raw = read_chunked(self.rfile)
        else:
            raw = self.rfile.read(length)
        print("RAW_LEN", len(raw), flush=True)
        body = json.loads(raw.decode("utf-8") or "{}") if raw else {}
        payload = json.dumps(completion(body)).encode("utf-8")
        print("RESP", payload[:300].decode(), flush=True)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(payload)

print("listening", flush=True)
ThreadingHTTPServer(("127.0.0.1", 18099), Handler).serve_forever()
