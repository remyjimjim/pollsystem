#!/usr/bin/env python3
"""
Export a Claude Code session transcript to Markdown with bash commands EXPANDED.

Unlike `/export` (which collapses tool calls to "Ran 1 shell command"), this
reads the auto-saved session JSONL and inlines each Bash command verbatim plus
its output, alongside the user and assistant text.

Usage:
  expand-session.py                      # ALL sessions for the CURRENT project, oldest
                                         #   first -> ./sessions.transcript.expanded.md
  expand-session.py OUTPUT.md            # all sessions -> OUTPUT.md
  expand-session.py --latest [OUT.md]    # only the newest session (the old default)
  expand-session.py SESSION.jsonl OUT.md # explicit session file -> OUT.md

The output is rebuilt from the session logs on every run (not appended), so
re-running never duplicates anything and never loses an earlier session: each
Claude Code session keeps its own .jsonl, and they're all included in order.

Caveat: the JSONL format is internal to Claude Code and may change between
versions. Great for personal transcripts/review; don't build automation on it.
"""
import json
import os
import re
import sys
from pathlib import Path

PROJECTS = Path.home() / ".claude" / "projects"
MAX_OUTPUT_CHARS = 4000  # truncate long tool outputs (e.g. big file reads)


def slug_for(cwd: str) -> str:
    """Claude Code names a project dir by replacing non-alphanumerics with '-'."""
    return re.sub(r"[^a-zA-Z0-9]", "-", cwd)


def sessions_for_cwd() -> list:
    """All of this project's session logs, oldest first (by their first timestamp)."""
    d = PROJECTS / slug_for(os.getcwd())
    if not d.is_dir():
        avail = "\n  ".join(sorted(p.name for p in PROJECTS.iterdir())) if PROJECTS.is_dir() else "(none)"
        sys.exit(f"No Claude project dir for this cwd:\n  {d}\nAvailable projects:\n  {avail}")
    sessions = list(d.glob("*.jsonl"))
    if not sessions:
        sys.exit(f"No .jsonl sessions in {d}")
    # mtime changes whenever a session is resumed, so order by when it started.
    return sorted(sessions, key=lambda p: (first_timestamp(p) or "", p.name))


def newest_session_for_cwd() -> Path:
    return max(sessions_for_cwd(), key=lambda p: p.stat().st_mtime)


def first_timestamp(session: Path):
    """ISO timestamp of the session's first timestamped entry, or None."""
    with open(session, encoding="utf-8") as f:
        for line in f:
            try:
                ts = json.loads(line).get("timestamp")
            except (json.JSONDecodeError, AttributeError):
                continue
            if ts:
                return ts
    return None


def truncate(text: str) -> str:
    if len(text) <= MAX_OUTPUT_CHARS:
        return text
    return text[:MAX_OUTPUT_CHARS] + f"\n… [truncated {len(text) - MAX_OUTPUT_CHARS} chars]"


def as_text(content) -> str:
    """tool_result .content is sometimes a string, sometimes a list of blocks."""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        out = []
        for b in content:
            if isinstance(b, dict):
                out.append(b.get("text", "") if b.get("type") == "text" else json.dumps(b))
            else:
                out.append(str(b))
        return "\n".join(out)
    return str(content)


def render_user(msg) -> str:
    content = msg.get("content", "")
    if isinstance(content, str):
        return f"### 🧑 User\n\n{content}\n"
    parts = []
    for item in content:
        if not isinstance(item, dict):
            continue
        if item.get("type") == "text":
            parts.append(item.get("text", ""))
        elif item.get("type") == "tool_result":
            body = truncate(as_text(item.get("content", "")).rstrip())
            err = " (error)" if item.get("is_error") else ""
            if body:
                parts.append(f"**Output{err}:**\n```\n{body}\n```")
    text = "\n\n".join(p for p in parts if p.strip())
    # A pure tool_result turn isn't a real "user" message — label by content.
    is_tool = all(isinstance(i, dict) and i.get("type") == "tool_result" for i in content) and content
    header = "### ⚙️ Tool result" if is_tool else "### 🧑 User"
    return f"{header}\n\n{text}\n" if text.strip() else ""


def render_assistant(msg) -> str:
    content = msg.get("content", "")
    if isinstance(content, str):
        return f"### 🤖 Assistant\n\n{content}\n"
    parts = []
    for item in content:
        if not isinstance(item, dict):
            continue
        t = item.get("type")
        if t == "text":
            parts.append(item.get("text", ""))
        elif t == "tool_use":
            name = item.get("name", "Tool")
            inp = item.get("input", {}) or {}
            if name == "Bash":
                desc = inp.get("description", "")
                cmd = inp.get("command", "")
                head = f"**$ Bash** — _{desc}_" if desc else "**$ Bash**"
                parts.append(f"{head}\n```bash\n{cmd}\n```")
            else:
                # Non-bash tools: compact one-liner so the file stays readable.
                summary = inp.get("file_path") or inp.get("pattern") or inp.get("description") or ""
                parts.append(f"**→ {name}**" + (f" `{summary}`" if summary else ""))
    text = "\n\n".join(p for p in parts if p.strip())
    return f"### 🤖 Assistant\n\n{text}\n" if text.strip() else ""


def render_session(session: Path) -> list:
    """The rendered user / assistant / tool blocks of one session log."""
    blocks = []
    with open(session, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            typ = e.get("type")
            if typ == "user":
                r = render_user(e.get("message", {}))
            elif typ == "assistant":
                r = render_assistant(e.get("message", {}))
            else:
                r = ""
            if r.strip():
                blocks.append(r)
    return blocks


def main():
    args = sys.argv[1:]
    if any(a in ("-h", "--help") for a in args):
        print(__doc__.strip())
        return
    unknown = [a for a in args if a.startswith("-") and a != "--latest"]
    if unknown:
        sys.exit(f"Unknown option(s): {' '.join(unknown)} (try --help)")
    latest = "--latest" in args
    args = [a for a in args if a != "--latest"]
    sessions = None
    out = "sessions.transcript.expanded.md"
    if len(args) == 1:
        if args[0].endswith(".jsonl"):
            sessions = [Path(args[0])]
        else:
            out = args[0]
    elif len(args) >= 2:
        sessions, out = [Path(args[0])], args[1]
    if sessions is None:
        sessions = [newest_session_for_cwd()] if latest else sessions_for_cwd()
    for s in sessions:
        if not s.exists():
            sys.exit(f"Session file not found: {s}")

    blocks = ["# Session transcript (expanded)\n\n" + "\n".join(f"_source: `{s}`_" for s in sessions) + "\n"]
    for s in sessions:
        started = (first_timestamp(s) or "unknown start")[:16].replace("T", " ")
        if len(sessions) > 1:
            blocks.append(f"## Session {s.stem} (started {started} UTC)\n")
        blocks.extend(render_session(s))

    # Rebuilt in full each run: re-running is idempotent and no session is dropped.
    Path(out).write_text("\n---\n\n".join(blocks), encoding="utf-8")
    print(f"Expanded transcript written to: {out}\n  ({len(sessions)} session(s): "
          + ", ".join(s.stem[:8] for s in sessions) + ")")


if __name__ == "__main__":
    main()
