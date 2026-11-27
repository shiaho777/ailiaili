import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
DOCUMENTS = [
    ROOT / "README.md",
    ROOT / "README.zh-CN.md",
    ROOT / "AGENTS.md",
    *sorted((ROOT / "static/doc").glob("*.md")),
]
FENCES = re.compile(r"^ {0,3}(`{3,}|~{3,})[^\n]*\n.*?^ {0,3}\1\s*$", re.M | re.S)
MARKDOWN_LINK = re.compile(r"\]\((<[^>]+>|[^\s)]+)")
HTML_LINK = re.compile(r"""\b(?:href|src)=["']([^"']+)["']""", re.I)
HEADING = re.compile(r"^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$", re.M)
HTML_ANCHOR = re.compile(r"""\bid=["']([^"']+)["']""", re.I)


def text(path):
    return FENCES.sub("", path.read_text(encoding="utf-8"))


def anchors(document):
    result = set(HTML_ANCHOR.findall(document))
    for title in HEADING.findall(document):
        base = re.sub(r"[^\w-]", "", title.lower().replace(" ", "-"))
        slug = base
        index = 1
        while slug in result:
            slug = f"{base}-{index}"
            index += 1
        result.add(slug)
    return result


def check():
    errors = []
    for source in DOCUMENTS:
        if not source.is_file():
            errors.append(f"Missing {source.relative_to(ROOT)}")
            continue
        document = text(source)
        for link in MARKDOWN_LINK.findall(document) + HTML_LINK.findall(document):
            target = urlsplit(link.strip("<>"))
            if target.scheme or target.netloc:
                continue
            path = (source.parent / unquote(target.path)).resolve() if target.path else source
            if not path.is_relative_to(ROOT) or not path.exists():
                errors.append(f"{source.relative_to(ROOT)}: missing local target {link}")
                continue
            if target.fragment and path.suffix.lower() == ".md":
                if unquote(target.fragment) not in anchors(text(path)):
                    errors.append(f"{source.relative_to(ROOT)}: missing heading {link}")
    if errors:
        sys.exit("\n".join(errors))
    print(f"Checked local links in {len(DOCUMENTS)} Markdown files.")


if __name__ == "__main__":
    check()
