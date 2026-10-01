"""檢查根目錄及 docs/ 的 Markdown 本地檔案連結；不存取外部網址。"""
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


def main():
    root = Path(__file__).resolve().parents[1]
    documents = sorted(set(root.glob("*.md")) | set((root / "docs").rglob("*.md")))
    errors = []
    checked = 0
    for document in documents:
        text = document.read_text(encoding="utf-8")
        text = re.sub(r"```.*?```", "", text, flags=re.S)
        for target in re.findall(r"!?\[[^\]]*\]\(([^)]+)\)", text):
            target = target.strip().split(' "', 1)[0].strip("<>")
            url = urlsplit(target)
            if url.scheme or url.netloc or not url.path:
                continue
            path = document.parent / unquote(url.path)
            checked += 1
            if not path.exists():
                errors.append(f"{document.relative_to(root)}: {target}")
            elif path.is_relative_to(root):
                # Windows 可忽略大小寫，GitHub 路徑不能，因此比對每層實際名稱。
                relative = path.absolute().relative_to(root)
                current = root
                for part in relative.parts:
                    if part == "..":
                        current = current.parent
                        continue
                    if part not in {entry.name for entry in current.iterdir()}:
                        errors.append(f"大小寫不符：{document.relative_to(root)}: {target}")
                        break
                    current /= part
    if errors:
        raise SystemExit("文件連結錯誤：\n" + "\n".join(errors))
    print(f"文件檢查通過：{len(documents)} 份 Markdown，{checked} 個本地連結。")


if __name__ == "__main__":
    main()
