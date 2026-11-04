#!/usr/bin/env python3
"""GitHub 이벤트를 담당 서비스의 디스코드 채널로 보냅니다.

서비스는 라벨과 PR에서 바뀐 폴더로 정합니다. 정확히 한 서비스면 그 채널로,
없거나 여러 서비스에 걸치면 공통 채널로 보냅니다.
웹후크 주소는 저장소 시크릿(DISCORD_WEBHOOK_MSA_*)에서 환경변수로 받습니다.
"""
import json
import os
import subprocess
import sys
import urllib.request

SERVICES = {
    "INFRA": {"name": "인프라", "owners": "이지원", "labels": {"gateway", "discovery", "config", "kafka", "docker", "k8s", "infra"},
              "paths": ("infra/", "docker/", "k8s/")},
    "USER": {"name": "user-service", "owners": "강태현·차현준", "labels": {"user"}, "paths": ("services/user-service/",)},
    "POLICY": {"name": "policy-service", "owners": "김예준·홍준서", "labels": {"policy"}, "paths": ("services/policy-service/",)},
    "ENGAGEMENT": {"name": "engagement-service", "owners": "손수연", "labels": {"engagement"},
                   "paths": ("services/engagement-service/",)},
    "RECOMMENDATION": {"name": "recommendation-service", "owners": "류지범", "labels": {"recommendation"},
                       "paths": ("services/recommendation-service/",)},
}
COLORS = {"open": 0x2DA44E, "merged": 0x8250DF, "closed": 0xCF222E, "review": 0xBF8700, "comment": 0x57606A}


def changed_files(repo, number):
    out = subprocess.run(["gh", "api", f"repos/{repo}/pulls/{number}/files", "--paginate", "-q", ".[].filename"],
                         capture_output=True, text=True)
    return out.stdout.split() if out.returncode == 0 else []


def pick_targets(labels, files):
    hit = set()
    for key, svc in SERVICES.items():
        if labels & svc["labels"] or any(f.startswith(svc["paths"]) for f in files):
            hit.add(key)
    return sorted(hit) if len(hit) == 1 else ["COMMON"]


def short(text, limit=300):
    text = (text or "").strip()
    return text if len(text) <= limit else text[:limit] + "…"


def describe(event, p):
    """(제목, 링크, 설명, 색, 대상 PR/이슈) — 보낼 필요가 없으면 None."""
    action = p.get("action")
    actor = p.get("sender", {}).get("login", "")
    if event == "issues":
        i = p["issue"]
        verb = {"opened": "이슈 생성", "closed": "이슈 닫힘", "reopened": "이슈 다시 열림", "assigned": "이슈 담당자 지정"}.get(action)
        if not verb:
            return None
        color = COLORS["closed"] if action == "closed" else COLORS["open"]
        body = short(i.get("body")) if action == "opened" else ""
        if action == "assigned":
            body = f"담당: {p.get('assignee', {}).get('login', '')}"
        return f"[{verb}] #{i['number']} {i['title']}", i["html_url"], body, color, i, actor
    if event == "issue_comment" and action == "created":
        i = p["issue"]
        kind = "PR" if "pull_request" in i else "이슈"
        return (f"[{kind} 댓글] #{i['number']} {i['title']}", p["comment"]["html_url"], short(p["comment"]["body"]),
                COLORS["comment"], i, actor)
    if event == "pull_request":
        pr = p["pull_request"]
        if action == "closed":
            verb, color = ("PR 머지", COLORS["merged"]) if pr.get("merged") else ("PR 닫힘", COLORS["closed"])
        elif action in ("opened", "reopened", "ready_for_review"):
            verb, color = {"opened": "PR 생성", "reopened": "PR 다시 열림", "ready_for_review": "PR 리뷰 준비 완료"}[action], COLORS["open"]
        elif action == "review_requested":
            who = p.get("requested_reviewer", {}).get("login") or p.get("requested_team", {}).get("name", "")
            verb, color = f"리뷰 요청 → {who}", COLORS["review"]
        else:
            return None
        body = f"{pr['head']['ref']} → {pr['base']['ref']}"
        if action == "opened":
            body += "\n" + short(pr.get("body"), 200)
        return f"[{verb}] #{pr['number']} {pr['title']}", pr["html_url"], body, color, pr, actor
    if event == "pull_request_review" and action == "submitted":
        pr, r = p["pull_request"], p["review"]
        state = {"approved": "승인", "changes_requested": "변경 요청", "commented": "리뷰 코멘트"}.get(r["state"].lower(), r["state"])
        return (f"[리뷰 {state}] #{pr['number']} {pr['title']}", r["html_url"], short(r.get("body")), COLORS["review"], pr, actor)
    if event == "pull_request_review_comment" and action == "created":
        pr, c = p["pull_request"], p["comment"]
        return (f"[코드 리뷰 댓글] #{pr['number']} {pr['title']}", c["html_url"], f"`{c.get('path', '')}`\n" + short(c.get("body")),
                COLORS["comment"], pr, actor)
    return None


def main():
    event = os.environ["GITHUB_EVENT_NAME"]
    with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as f:
        payload = json.load(f)
    info = describe(event, payload)
    if not info:
        print(f"{event}/{payload.get('action')}: 보낼 알림 없음")
        return
    title, url, body, color, target, actor = info
    labels = {l["name"] for l in target.get("labels", [])}
    files = changed_files(os.environ["GITHUB_REPOSITORY"], target["number"]) if (
        "pull_request" in target or "head" in target) else []
    for key in pick_targets(labels, files):
        hook = os.environ.get(f"DISCORD_WEBHOOK_MSA_{key}")
        if not hook:
            print(f"{key}: 웹후크 시크릿 없음, 건너뜀")
            continue
        footer = "여러 서비스 또는 공통" if key == "COMMON" else f"{SERVICES[key]['name']} · 담당 {SERVICES[key]['owners']}"
        msg = {"username": "hyeja-msa", "allowed_mentions": {"parse": []}, "embeds": [{
            "title": title[:256], "url": url, "description": body[:2000], "color": color,
            "author": {"name": actor}, "footer": {"text": footer}}]}
        req = urllib.request.Request(hook, data=json.dumps(msg).encode(), headers={
            "Content-Type": "application/json", "User-Agent": "hyeja-msa-notify"})
        try:
            urllib.request.urlopen(req, timeout=10).read()
            print(f"{key}: 보냄 — {title}")
        except Exception as e:  # 알림 실패가 다른 작업을 막지 않게 로그만 남긴다
            print(f"{key}: 전송 실패 {e}", file=sys.stderr)


if __name__ == "__main__":
    main()
