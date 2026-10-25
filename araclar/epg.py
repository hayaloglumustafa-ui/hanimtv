"""Ücretsiz yayın akışı (XMLTV) kaynaklarından HanımTV kanallarının akışını çıkarıp epg.json yazar.

GitHub Actions'ta 6 saatte bir çalışır. Uygulama küçük epg.json dosyasını indirir.
Kanal eşleştirmesi isimle yapılır; kanallar.json'da bir kanala "epg": "kaynaktaki-kimlik"
yazılırsa o kimlik kullanılır.
"""
import gzip, io, json, re, sys, time, urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime

KAYNAKLAR = [
    "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz",
    "https://www.open-epg.com/files/turkey1.xml",
    "https://www.open-epg.com/files/turkey2.xml",
    "https://www.open-epg.com/files/turkey3.xml",
    "https://epgshare01.online/epgshare01/epg_ripper_AZ1.xml.gz",
    "https://epgshare01.online/epgshare01/epg_ripper_CY1.xml.gz",
]
TR = str.maketrans("çğıöşüÇĞİÖŞÜəƏ", "cgiosuCGIOSUeE")


def norm(s):
    s = re.sub(r"[^a-z0-9]", "", s.translate(TR).lower())
    for ek in ("hd", "fhd", "uhd", "4k"):
        if s.endswith(ek) and len(s) > len(ek) + 1:
            s = s[: -len(ek)]
    return s


def varyantlar(s):
    n = norm(s)
    v = {n}
    for ek in ("tr", "turkiye", "turkey", "az", "tv"):
        if n.endswith(ek) and len(n) > len(ek) + 1:
            v.add(n[: -len(ek)])
    return v


def zaman(t):
    return int(datetime.strptime(t[:20].strip(), "%Y%m%d%H%M%S %z").timestamp())


def indir(adres):
    istek = urllib.request.Request(adres, headers={"User-Agent": "Mozilla/5.0 HanimTV-EPG"})
    veri = urllib.request.urlopen(istek, timeout=120).read()
    if veri[:2] == b"\x1f\x8b":
        veri = gzip.decompress(veri)
    return veri


def main():
    kanallar = json.load(open("app/src/main/assets/kanallar.json", encoding="utf-8"))["kanallar"]
    simdi = time.time()
    son = simdi + 36 * 3600
    sonuc = {}
    for adres in KAYNAKLAR:
        try:
            veri = indir(adres)
        except Exception as e:
            print("Kaynak alınamadı:", adres, e)
            continue
        kimlikIsim = {}
        programlar = {}
        for _, el in ET.iterparse(io.BytesIO(veri)):
            if el.tag == "channel":
                kimlikIsim[el.get("id")] = [d.text or "" for d in el.findall("display-name")] + [el.get("id") or ""]
                el.clear()
            elif el.tag == "programme":
                try:
                    b, s = zaman(el.get("start")), zaman(el.get("stop"))
                except Exception:
                    el.clear(); continue
                if s > simdi and b < son:
                    baslik = (el.findtext("title") or "").strip()
                    if baslik:
                        programlar.setdefault(el.get("channel"), []).append((b, s, baslik[:60]))
                el.clear()
        dizin = {}
        for kimlik, isimler in kimlikIsim.items():
            if kimlik not in programlar:
                continue
            for i in isimler:
                for v in varyantlar(i):
                    dizin.setdefault(v, kimlik)
        eslesen = 0
        for k in kanallar:
            if k["name"] in sonuc:
                continue
            kimlik = k.get("epg") if k.get("epg") in programlar else None
            if not kimlik:
                for v in varyantlar(k["name"]):
                    if v in dizin:
                        kimlik = dizin[v]; break
            if kimlik:
                p = sorted(set(programlar[kimlik]))
                sonuc[k["name"]] = [[b, s, t] for b, s, t in p]
                eslesen += 1
        print(f"{adres}: {len(kimlikIsim)} kanal, {eslesen} eşleşme")
    eksik = [f'{k["no"]} {k["name"]}' for k in kanallar if k["name"] not in sonuc]
    print(f"Toplam {len(sonuc)} kanalın akışı var. Akışı olmayanlar ({len(eksik)}):", ", ".join(eksik))
    if not sonuc:
        sys.exit("Hiç akış bulunamadı; eski dosya korunuyor.")
    json.dump({"zaman": int(simdi), "akis": sonuc}, open("epg.json", "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))


main()
