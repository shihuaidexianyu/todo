"""Render the research report to a portable, offline HTML reading copy."""
from pathlib import Path
import base64
import html
import re

root = Path(__file__).resolve().parent
source = (root / 'report.md').read_text(encoding='utf-8')
toc = []

def inline(value):
    value = html.escape(value)
    value = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2">\1</a>', value)
    value = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', value)
    return value

lines = source.splitlines()
blocks = []
i = 0
while i < len(lines):
    line = lines[i].strip()
    if not line:
        i += 1
        continue
    heading = re.match(r'^(#{1,3}) (.+)', line)
    if heading:
        level = len(heading[1])
        anchor = f'section-{len(toc)}' if level == 2 else f'heading-{i}'
        if level == 2:
            toc.append((anchor, heading[2]))
        blocks.append(f'<h{level} id="{anchor}">{inline(heading[2])}</h{level}>')
    elif line.startswith('!['):
        match = re.fullmatch(r'!\[([^\]]+)\]\(([^)]+)\)', line)
        path = (root / match[2]).resolve()
        data = base64.b64encode(path.read_bytes()).decode('ascii')
        blocks.append(f'<figure><a href="{html.escape(match[2])}"><img src="data:image/png;base64,{data}" alt="{html.escape(match[1])}"></a><figcaption>{inline(match[1])}</figcaption></figure>')
    elif line.startswith('|'):
        rows = []
        while i < len(lines) and lines[i].strip().startswith('|'):
            cells = [cell.strip() for cell in lines[i].strip().strip('|').split('|')]
            if not all(re.fullmatch(r':?-+:?', cell) for cell in cells):
                rows.append(cells)
            i += 1
        header = '<tr>' + ''.join(f'<th>{inline(cell)}</th>' for cell in rows[0]) + '</tr>'
        body = ''.join('<tr>' + ''.join(f'<td>{inline(cell)}</td>' for cell in row) + '</tr>' for row in rows[1:])
        blocks.append(f'<div class="table"><table><thead>{header}</thead><tbody>{body}</tbody></table></div>')
        continue
    elif re.match(r'^\d+\. ', line):
        items = []
        while i < len(lines) and re.match(r'^\d+\. ', lines[i]):
            items.append('<li>' + inline(re.sub(r'^\d+\. ', '', lines[i])) + '</li>')
            i += 1
        blocks.append('<ol class="sources">' + ''.join(items) + '</ol>')
        continue
    else:
        paragraphs = [line]
        while i + 1 < len(lines) and lines[i+1].strip():
            if re.match(r'^(#|\||!\[|\d+\. )', lines[i+1]):
                break
            i += 1
            paragraphs.append(lines[i].strip())
        blocks.append('<p>' + inline(' '.join(paragraphs)) + '</p>')
    i += 1

navigation = ''.join(f'<a href="#{anchor}">{html.escape(title)}</a>' for anchor, title in toc)
document = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>todo 界面调研与改版行动方案</title>
<style>
*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:#fff;color:#252525;font:16px/1.85 "Segoe UI","Microsoft YaHei",sans-serif}
main{max-width:1040px;margin:auto;padding:64px 44px 100px}h1{font-size:34px;letter-spacing:-.7px;line-height:1.4;margin:0 0 32px}h2{font-size:25px;line-height:1.5;margin:60px 0 20px;padding-top:18px;border-top:1px solid #bbb}h3{font-size:19px;margin:34px 0 12px}p{max-width:850px;margin:16px 0}a{color:#31536c;text-underline-offset:4px}nav{display:flex;flex-wrap:wrap;gap:8px 22px;border-bottom:1px solid #ddd;padding:0 0 25px;font-size:14px}nav a{color:#555}figure{margin:28px 0 30px}figure img{max-width:100%;max-height:490px;width:auto;height:auto;display:block;object-fit:contain;object-position:left}figcaption{font-size:13px;color:#666;margin-top:10px}table{border-collapse:collapse;width:100%;font-size:14px;line-height:1.7}th{text-align:left;border-bottom:2px solid #555;padding:12px;background:#f4f4f4}td{vertical-align:top;padding:13px 12px;border-bottom:1px solid #ddd}th:first-child{min-width:110px}tbody tr:nth-child(even){background:#fafafa}.table{overflow:auto;margin:24px 0}.sources{padding-left:24px;font-size:14px}.sources li{margin-bottom:14px}strong{font-weight:650}h2,h3{scroll-margin-top:20px}
@media(max-width:650px){main{padding:28px 20px 60px}h1{font-size:27px}h2{font-size:22px}body{font-size:15px}figure img{max-height:460px}th,td{min-width:130px}}
@media print{main{max-width:none;padding:0}nav{display:none}body{font-size:10pt}h1{font-size:24pt}h2{font-size:17pt;break-after:avoid}h3{break-after:avoid}figure{break-inside:avoid}figure img{max-height:105mm}tr{break-inside:avoid}a{color:inherit}table{font-size:9pt}.table{overflow:visible}}
</style><main>'''
document += blocks[0] + '<nav aria-label="报告目录">' + navigation + '</nav>' + ''.join(blocks[1:]) + '</main></html>'
(root / 'index.html').write_text(document, encoding='utf-8')
print(f'Created {root / "index.html"}; {len(toc)} sections; {len(re.findall("<figure>", document))} evidence figures')
