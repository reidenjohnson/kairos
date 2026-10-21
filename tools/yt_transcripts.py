"""Pull YouTube transcripts into notes/transcripts/<topic>/ for the Kairos knowledge base.

Usage:
    python tools/yt_transcripts.py                 # process every URL in notes/transcripts/queue.txt
    python tools/yt_transcripts.py deer <url> ...  # one-off: topic + video/playlist/channel URLs
    python tools/yt_transcripts.py --timestamps    # re-fetch saved transcripts lacking [m:ss] marks

queue.txt format ("# topic" starts a section; URLs can be videos, playlists, or channels):
    # deer
    https://www.youtube.com/playlist?list=...
    # fishing
    https://www.youtube.com/@SomeChannel/videos

Already-downloaded videos are skipped, so re-running a playlist only grabs new ones.
Saved text carries a [m:ss] mark about every 30 seconds so extracted rules can cite a timestamp.
"""

import html
import re
import sys
import tempfile
import textwrap
import time
from pathlib import Path

from yt_dlp import YoutubeDL
from youtube_transcript_api import YouTubeTranscriptApi

ROOT = Path(__file__).resolve().parent.parent / "notes" / "transcripts"
QUEUE = ROOT / "queue.txt"
FAILED = ROOT / "failed.txt"
DELAY_SECONDS = 5  # be gentle so YouTube doesn't rate-limit the home IP
BATCH_SIZE = 75    # videos per run; re-run later to continue where it stopped
MAX_THROTTLED = 3  # stop the run after this many throttled videos in a row
MARK_EVERY = 30    # seconds between [m:ss] marks in the saved text


def throttled(e):
    s = f"{type(e).__name__} {e}"
    return any(k in s for k in ("429", "Too Many", "IpBlocked", "RequestBlocked", "ConnectionError", "10054"))


def read_queue():
    jobs, topic = [], "misc"
    for line in QUEUE.read_text(encoding="utf-8-sig").splitlines():  # -sig: tolerate Notepad/PowerShell BOM
        line = line.strip()
        if not line:
            continue
        if line.startswith("#"):
            topic = line.lstrip("#").strip().lower() or "misc"
        else:
            jobs.append((topic, line))
    return jobs


def expand(url):
    """Resolve a video/playlist/channel URL into a list of video dicts (id, title, channel)."""
    opts = {"quiet": True, "no_warnings": True, "extract_flat": "in_playlist", "skip_download": True}
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
    return list(_videos(info, info.get("channel") or info.get("uploader")))


def _videos(info, channel):
    if "entries" not in info:
        yield {
            "id": info["id"],
            "title": info.get("title") or info["id"],
            "channel": info.get("channel") or info.get("uploader") or channel or "Unknown",
            "date": info.get("upload_date") or "",
            "duration": info.get("duration") or 0,
        }
        return
    for entry in info["entries"] or []:
        if not entry:
            continue
        # Channel pages list tabs (Videos, Shorts, ...) as nested playlists.
        if entry.get("ie_key") == "YoutubeTab" or entry.get("_type") == "playlist":
            with YoutubeDL({"quiet": True, "no_warnings": True, "extract_flat": "in_playlist"}) as ydl:
                yield from _videos(ydl.extract_info(entry["url"], download=False), channel)
        else:
            entry.setdefault("channel", entry.get("uploader") or channel)
            yield from _videos(entry, channel)


def stamp(sec):
    sec = int(sec)
    return f"[{sec // 3600}:{sec // 60 % 60:02d}:{sec % 60:02d}]" if sec >= 3600 else f"[{sec // 60}:{sec % 60:02d}]"


def with_marks(pieces):
    """Join (start_seconds, text) pieces, dropping a [m:ss] mark every MARK_EVERY seconds."""
    out, next_mark = [], 0
    for start, text in pieces:
        if start >= next_mark:
            out.append(stamp(start))
            next_mark = (int(start) // MARK_EVERY + 1) * MARK_EVERY
        out.append(text)
    return " ".join(out)


def fetch_api(video_id):
    snippets = YouTubeTranscriptApi().fetch(video_id, languages=["en", "en-US", "en-GB"])
    return with_marks((s.start, s.text.replace("\n", " ")) for s in snippets)


def fetch(video_id):
    """Try the transcript API, then yt-dlp; back off and retry when YouTube throttles."""
    last = None
    for wait in (0, 20, 60):
        time.sleep(wait)
        for fn in (fetch_api, fetch_ytdlp):
            try:
                return fn(video_id)
            except Exception as e:
                last = e
        if not throttled(last):
            break  # a real "no captions" failure, not throttling; retrying won't help
    raise last


def fetch_ytdlp(video_id):
    """Fallback: download English captions with yt-dlp and flatten the VTT."""
    with tempfile.TemporaryDirectory() as tmp:
        opts = {
            "quiet": True, "no_warnings": True, "noprogress": True, "skip_download": True,
            "writesubtitles": True, "writeautomaticsub": True,
            "subtitleslangs": ["en.*", "en"], "subtitlesformat": "vtt",
            "outtmpl": str(Path(tmp) / "%(id)s.%(ext)s"),
        }
        with YoutubeDL(opts) as ydl:
            ydl.download([f"https://www.youtube.com/watch?v={video_id}"])
        vtts = sorted(Path(tmp).glob("*.vtt"))
        if not vtts:
            raise RuntimeError("no captions available")
        return vtt_to_text(vtts[0].read_text(encoding="utf-8"))


def vtt_seconds(cue):
    """'00:01:02.500 --> ...' (or '01:02.500 --> ...') -> 62.5"""
    parts = cue.split("-->")[0].strip().replace(",", ".").split(":")
    return sum(float(x) * 60 ** i for i, x in enumerate(reversed(parts)))


def vtt_to_text(vtt):
    pieces, last, start = [], None, 0.0
    for line in vtt.splitlines():
        line = line.strip()
        if "-->" in line:
            start = vtt_seconds(line)
            continue
        if not line or line.startswith(("WEBVTT", "Kind:", "Language:", "NOTE")):
            continue
        line = html.unescape(re.sub(r"<[^>]+>", "", line)).strip()
        # Auto-captions repeat the previous line in each cue; drop consecutive duplicates.
        if line and line != last:
            pieces.append((start, line))
            last = line
    return with_marks(pieces)


def safe(name):
    return re.sub(r'[<>:"/\\|?*\x00-\x1f]', "", name).strip()[:90]


def done_ids():
    ids = {m.group(1) for p in ROOT.rglob("*.txt") if (m := re.search(r"\[([\w-]{11})\]\.txt$", p.name))}
    if FAILED.exists():  # no-caption videos: don't retry every run (delete the line to force a retry)
        ids |= {line.split("\t")[1] for line in FAILED.read_text(encoding="utf-8").splitlines() if "\t" in line}
    return ids


def save(topic, v, text):
    folder = ROOT / topic
    folder.mkdir(parents=True, exist_ok=True)
    mins = f"{v['duration'] // 60:.0f} min" if v["duration"] else ""
    header = "\n".join([
        f"Title: {v['title']}",
        f"Channel: {v['channel']}",
        f"URL: https://www.youtube.com/watch?v={v['id']}",
        f"Uploaded: {v['date']}  {mins}".rstrip(),
        "",
    ])
    body = textwrap.fill(text, width=100)
    path = folder / f"{safe(v['channel'])} - {safe(v['title'])} [{v['id']}].txt"
    path.write_text(header + "\n" + body + "\n", encoding="utf-8")
    return path


def backfill_timestamps():
    """Re-fetch saved transcripts that predate [m:ss] marks and rewrite their text in place."""
    todo = [p for p in sorted(ROOT.glob("*/*.txt"))
            if re.search(r"\[[\w-]{11}\]\.txt$", p.name)
            and not re.search(r"\[\d+:\d\d\]", p.read_text(encoding="utf-8"))]
    print(f"{len(todo)} transcripts without timestamps")
    done = throttled_in_a_row = 0
    for p in todo:
        vid = re.search(r"\[([\w-]{11})\]\.txt$", p.name).group(1)
        try:
            text = fetch(vid)
        except Exception as e:
            if throttled(e):
                throttled_in_a_row += 1
                print(f"  ~ throttled: {p.name}")
                if throttled_in_a_row >= MAX_THROTTLED:
                    print("YouTube is throttling this IP - wait an hour or so, then run again.")
                    break
            else:
                print(f"  x {p.name} ({type(e).__name__})")
            time.sleep(DELAY_SECONDS)
            continue
        header = p.read_text(encoding="utf-8").split("\n\n", 1)[0]
        p.write_text(header + "\n\n" + textwrap.fill(text, width=100) + "\n", encoding="utf-8")
        done += 1
        throttled_in_a_row = 0
        print(f"  + {p.name}")
        time.sleep(DELAY_SECONDS)
    print(f"\n{done} of {len(todo)} timestamped")


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    if sys.argv[1:] == ["--timestamps"]:
        backfill_timestamps()
        return
    if len(sys.argv) > 2:
        jobs = [(sys.argv[1].lower(), u) for u in sys.argv[2:]]
    elif QUEUE.exists():
        jobs = read_queue()
    else:
        QUEUE.write_text("# deer\n\n# fishing\n\n# turkey\n", encoding="utf-8")
        print(f"Created {QUEUE} - paste URLs under a topic and run again.")
        return

    done = done_ids()
    saved = failed = skipped = remaining = 0
    throttled_in_a_row, stop = 0, None
    for topic, url in jobs:
        try:
            videos = expand(url)
        except Exception as e:
            print(f"!! could not read {url}: {e}")
            continue
        todo = [v for v in videos if v["id"] not in done]
        skipped += len(videos) - len(todo)
        print(f"[{topic}] {url} -> {len(videos)} videos, {len(todo)} new")
        for v in todo:
            if stop:
                remaining += 1
                continue
            try:
                text = fetch(v["id"])
                path = save(topic, v, text)
                done.add(v["id"])
                saved += 1
                throttled_in_a_row = 0
                print(f"  + {path.name}")
            except Exception as e:
                if throttled(e):
                    # Not a real failure: leave it undone so the next run picks it up.
                    throttled_in_a_row += 1
                    remaining += 1
                    print(f"  ~ throttled: {v['title']}")
                    if throttled_in_a_row >= MAX_THROTTLED:
                        stop = "YouTube is throttling this IP - wait an hour or so, then run again."
                else:
                    failed += 1
                    done.add(v["id"])
                    msg = f"{topic}\t{v['id']}\t{v['title']}\t{type(e).__name__}: {str(e).splitlines()[0][:150]}"
                    with FAILED.open("a", encoding="utf-8") as f:
                        f.write(msg + "\n")
                    print(f"  x {v['title']} ({type(e).__name__})")
            if saved >= BATCH_SIZE and not stop:
                stop = f"Batch of {BATCH_SIZE} done - run again later for the rest."
            time.sleep(DELAY_SECONDS)

    print(f"\n{saved} saved, {skipped} already had, {failed} failed (no captions), {remaining} still to do"
          + (f"\n{stop}" if stop else "")
          + (f"\nFailures listed in {FAILED}" if failed else ""))


if __name__ == "__main__":
    main()
