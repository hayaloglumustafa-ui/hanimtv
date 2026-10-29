"""Reklam arası işaretlerini arar: kanalların yayın listelerini 25 dk boyunca 8 sn'de bir okur,
standart dışı etiketleri (CUE-OUT, DATERANGE, SCTE35, DISCONTINUITY) ve parça adresi değişimlerini kaydeder."""
import json, re, time, urllib.request
from urllib.parse import urljoin
NOLAR = [22, 18, 20, 16, 24, 31, 23]
SURE = 25 * 60
kanallar = {k["no"]: k for k in json.load(open("app/src/main/assets/kanallar.json", encoding="utf-8"))["kanallar"]}
def al(url, bas):
    h = {"User-Agent": bas.get("User-Agent", "Mozilla/5.0")}
    for a in ("Referer", "Origin"):
        if a in bas: h[a] = bas[a]
    return urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=15).read().decode("utf-8", "replace")
SIRADAN = ("#EXTM3U", "#EXT-X-VERSION", "#EXT-X-TARGETDURATION", "#EXT-X-MEDIA-SEQUENCE", "#EXTINF", "#EXT-X-PROGRAM-DATE-TIME", "#EXT-X-INDEPENDENT-SEGMENTS", "#EXT-X-DISCONTINUITY-SEQUENCE")
durum = {}
for no in NOLAR:
    k = kanallar[no]; bas = k.get("basliklar", {})
    for u in k["urls"]:
        try:
            m = al(u, bas)
            if "#EXT-X-STREAM-INF" in m:
                satir = [l for l in m.splitlines() if l and not l.startswith("#")]
                u2 = urljoin(u, satir[0]); al(u2, bas); u = u2
            durum[no] = {"ad": k["name"], "url": u, "bas": bas, "olay": [], "gorulen": set(), "onek": None}
            break
        except Exception as e:
            print(no, k["name"], "açılmadı:", u, e)
bas_zaman = time.time()
while time.time() - bas_zaman < SURE:
    for no, d in durum.items():
        try:
            p = al(d["url"], d["bas"])
        except Exception as e:
            d["olay"].append(f"{int(time.time()-bas_zaman)}s hata {e}"); continue
        for l in p.splitlines():
            if l.startswith("#") and not l.startswith(SIRADAN):
                anahtar = l[:120]
                if anahtar not in d["gorulen"]:
                    d["gorulen"].add(anahtar); d["olay"].append(f"{int(time.time()-bas_zaman)}s ETIKET {anahtar}")
            elif l and not l.startswith("#"):
                onek = re.sub(r"[0-9]+", "N", urljoin(d["url"], l).split("?")[0])
                onek = onek[:100]
                if onek != d["onek"]:
                    d["olay"].append(f"{int(time.time()-bas_zaman)}s PARCA {onek}"); d["onek"] = onek
    time.sleep(8)
with open("sonuc.txt", "w") as f:
    for no, d in durum.items():
        f.write(f"== {no} {d['ad']} {d['url'][:100]}\n")
        for o in d["olay"][:80]: f.write("  " + o + "\n")
