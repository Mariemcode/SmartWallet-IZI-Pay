"""
SmartWallet — OCR Service (Computer Vision Module)
===================================================
Fonctionnalités :
  1. Scan de facture : extrait fournisseur, montant, échéance, référence
  2. Détective de factures : compare vs historique pour détecter les anomalies
  3. Impact solde : calcule solde_après + % budget consommé
"""

import re
import io
import base64
import logging
from datetime import datetime, timedelta
from typing import Optional, Dict, Any, List, Tuple

from PIL import Image, ImageFilter, ImageEnhance, ImageOps
import pytesseract

log = logging.getLogger("smartwallet-ocr")

# ════════════════════════════════════════════════════════════════
# PATTERNS REGEX — Fournisseurs
# ════════════════════════════════════════════════════════════════

FOURNISSEUR_PATTERNS: List[Tuple[str, str, List[str]]] = [
    ("STEG", "STEG", [
        "steg",
        "société tunisienne",
        "societe tunisienne",
        "soctété tunisienne",           # ✅ v4 : variante OCR courante (é→t)
        "société tunisienne de l'électricité",
        "société tunisienne de l electricite",
        "electricite et du gaz",
        "électricité et du gaz",
        "electricité et du gaz",        # ✅ v4 : variante OCR mixte
        "élecreicité et du gaz",        # ✅ v4 : variante OCR "tr→re"
        "electricite",
        "électricité",
        "énergie électrique",
        "steg.com.tn",
        "webmaster@steg",
        "@steg.com",
        "basse tension",
        "haute pression",
        "bulletin de versement ccp",
        "bulletin de vérsement ccp",    # ✅ v4 : variante OCR accent erroné
        "n.compteur",                   # ✅ v4 : propre à STEG
        "n°dépannage",
        "redevances fixes",             # ✅ v4 : spécifique facture STEG
        "total électricité",
        "total consommation et services",
    ]),
    ("SONEDE", "SONEDE", [
        "sonede",
        "société nationale",
        "eau potable",
        "distribution d'eau",
        "consommation d'eau",
    ]),
    ("TOPNET", "TOPNET", [
        "topnet", "top net", "topnet.tn"
    ]),
    ("BEE", "BeeConnect", [
        "beeconnect", "bee connect", "hexabyte",
        # ⚠️ v4 : retiré "bee" tout seul car match trop de faux positifs STEG
    ]),
    ("TT", "Tunisie Telecom", [
        "tunisie telecom", "tt mobile", "elissa"
        # ⚠️ v4 : retiré "telecom" seul car apparaît dans ISP concurrents
    ]),
    ("OOREDOO", "Ooredoo", [
        "ooredoo", "wataniya"
    ]),
]


# ════════════════════════════════════════════════════════════════
# PREPROCESSING IMAGE
# ════════════════════════════════════════════════════════════════

def _preprocess_image(img: Image.Image) -> Image.Image:
    """
    Pipeline de préprocessing pour maximiser la qualité OCR.
    Optimisé pour les factures STEG avec fond coloré et texte bilingue.

    v4.1 : préprocessing moins agressif pour préserver le texte rouge
    de la case "Montant à payer".
    """
    img = img.convert("L")
    w, h = img.size

    # Upscale si trop petite
    if w < 1200:
        scale = 1200 / w
        img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)

    # ✅ v4.1 : contraste modéré (2.2 était trop agressif et écrasait le rouge)
    img = ImageEnhance.Contrast(img).enhance(1.8)
    img = ImageEnhance.Sharpness(img).enhance(1.3)
    img = img.filter(ImageFilter.SHARPEN)

    # ✅ v4.1 : seuil plus haut (150) pour garder les pixels rouges clairs
    # (le texte rouge "Montant à payer" devient gris moyen après conversion L)
    img = img.point(lambda p: 255 if p > 150 else 0)
    return img


def _preprocess_image_agressif(img: Image.Image) -> Image.Image:
    """
    ✅ v4.1 : version alternative avec contraste élevé pour le texte noir.
    Utilisée en seconde passe si la première échoue sur le montant.
    """
    img = img.convert("L")
    w, h = img.size

    if w < 1200:
        scale = 1200 / w
        img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)

    img = ImageEnhance.Contrast(img).enhance(2.5)
    img = ImageEnhance.Sharpness(img).enhance(1.5)
    img = img.filter(ImageFilter.SHARPEN)
    img = img.point(lambda p: 255 if p > 120 else 0)
    return img


def _preprocess_image_rouge(img_orig: Image.Image) -> Image.Image:
    """
    ✅ v4.1 : extrait spécifiquement le canal rouge de l'image originale.

    La case "MONTANT À PAYER" et le libellé "MONTANT TOTAL" sont en ROUGE sur
    la facture STEG. En isolant le canal rouge et en inversant, on obtient
    du texte noir sur fond blanc = parfait pour Tesseract.
    """
    # Garder l'image en RGB (ne pas convertir en L)
    if img_orig.mode != "RGB":
        img_orig = img_orig.convert("RGB")

    w, h = img_orig.size
    if w < 1200:
        scale = 1200 / w
        img_orig = img_orig.resize((int(w * scale), int(h * scale)), Image.LANCZOS)

    # Extraire le canal rouge uniquement
    r, g, b = img_orig.split()

    # Le texte rouge a : R élevé (>180), G et B faibles (<120)
    # On crée un masque : pixel rouge = noir, tout le reste = blanc
    import numpy as np
    r_arr = np.array(r)
    g_arr = np.array(g)
    b_arr = np.array(b)

    # Pixel = rouge SI (R-G > 50 ET R-B > 50 ET R > 150)
    mask_rouge = (r_arr.astype(int) - g_arr.astype(int) > 40) & \
                 (r_arr.astype(int) - b_arr.astype(int) > 40) & \
                 (r_arr > 130)

    # Noir où c'est rouge, blanc ailleurs
    result = np.where(mask_rouge, 0, 255).astype(np.uint8)

    img = Image.fromarray(result, mode='L')
    img = img.filter(ImageFilter.SHARPEN)
    return img


# ════════════════════════════════════════════════════════════════
# EXTRACTION OCR
# ════════════════════════════════════════════════════════════════

def _run_ocr(img: Image.Image, psm: int = 6) -> str:
    """
    Lance Tesseract avec config optimisée pour factures bilingues.

    v4.1 : PSM configurable
      psm 6  = bloc texte uniforme (défaut)
      psm 11 = texte épars sans ordre (utile pour factures en grille)
      psm 4  = colonne simple
      psm 3  = automatique
    """
    config = f"--psm {psm} --oem 3"
    # ✅ Français en priorité (chaque libellé arabe est traduit en français sur la
    # facture) → beaucoup moins de bruit que fra+ara. Repli si la langue manque.
    for lang in ("fra", "fra+ara", None):
        try:
            if lang:
                return pytesseract.image_to_string(img, lang=lang, config=config).lower()
            return pytesseract.image_to_string(img, config=config).lower()
        except Exception:
            continue
    return ""


def _run_ocr_multi(img: Image.Image) -> str:
    """
    ✅ v4.1 : lance Tesseract avec plusieurs PSM et concatène les résultats.

    Différentes configurations capturent différentes zones :
      - psm 6  : blocs de texte (ligne de détail consommation)
      - psm 11 : texte épars (cases isolées comme "MONTANT À PAYER")
      - psm 4  : colonne simple (tableaux alignés)

    On concatène tous les textes → plus de chances de capter le bon montant.
    """
    textes = []
    for psm in [6, 11, 4]:
        try:
            t = _run_ocr(img, psm=psm)
            if t.strip():
                textes.append(f"\n=== PSM {psm} ===\n{t}")
        except Exception as e:
            log.warning(f"  ⚠️ PSM {psm} échoué : {e}")
    return "\n".join(textes)


def _decode_image(image_b64: str) -> Image.Image:
    """Décode une image base64 en PIL Image."""
    if "," in image_b64:
        image_b64 = image_b64.split(",", 1)[1]
    img_bytes = base64.b64decode(image_b64)
    return Image.open(io.BytesIO(img_bytes))


def _best_orientation(img: Image.Image) -> Image.Image:
    """
    Repli quand l'OSD échoue : teste les rotations 0/90/180/270 et garde celle
    qui maximise le nombre de groupes de chiffres lisibles. Indispensable pour
    les petits tickets (SONEDE) photographiés de travers, où l'OSD ne trouve pas
    assez de caractères pour décider.
    """
    best_img, best_score, best_ang = img, -1, 0
    for ang in (0, 270, 90, 180):
        im = img if ang == 0 else img.rotate(ang, expand=True)
        try:
            small = im.resize((int(im.width * 1.5), int(im.height * 1.5)), Image.LANCZOS)
            g = ImageOps.grayscale(small).point(lambda p: 255 if p > 140 else 0)
            txt = pytesseract.image_to_string(
                g, config="--psm 11 -c tessedit_char_whitelist=0123456789,. ")
            score = len(re.findall(r"\d{2,3}[,.]\d{3}", txt))
        except Exception:
            score = 0
        if score > best_score:
            best_img, best_score, best_ang = im, score, ang
    if best_ang:
        log.info(f"🔄 Orientation (repli) : rotation de {best_ang}° (score chiffres={best_score})")
    return best_img


def _auto_rotate(img: Image.Image) -> Image.Image:
    """
    Détecte l'orientation de l'image (OSD Tesseract) et la redresse avant l'OCR.
    Si l'OSD échoue (trop peu de caractères), bascule sur un repli qui choisit la
    meilleure rotation d'après le nombre de chiffres lisibles.
    """
    try:
        probe = img.convert("RGB")
        osd = pytesseract.image_to_osd(probe)
        m = re.search(r"Rotate:\s*(\d+)", osd)
        rot = int(m.group(1)) if m else 0
        if rot:
            img = img.rotate(-rot, expand=True)
            log.info(f"🔄 Orientation corrigée (OSD) : rotation de {rot}°")
    except Exception:
        img = _best_orientation(img)   # OSD indisponible → repli par score chiffres
    return img


def _ocr_digits(im: Image.Image, psm: int, whitelist: str = "0123456789,. ") -> str:
    return pytesseract.image_to_string(
        im, config=f"--psm {psm} -c tessedit_char_whitelist={whitelist} "
    ).strip()


def _vote_amounts(crop: Image.Image) -> "Counter":
    """Lit les montants 'XXX,XXX' dans une vignette via plusieurs seuillages/PSM (vote)."""
    from collections import Counter
    votes: "Counter[float]" = Counter()
    up = crop.resize((crop.width * 3, crop.height * 3), Image.LANCZOS)
    gray = ImageOps.grayscale(up)
    variants = [gray] + [gray.point(lambda p, T=T: 255 if p > T else 0) for T in (110, 140, 170)]
    for im in variants:
        for psm in (6, 7, 11):
            for a, dec in re.findall(r"\b(\d{2,3})[,.](\d{3})\b", _ocr_digits(im, psm)):
                val = float(f"{a}.{dec}")
                if 10 <= val <= 9999:
                    votes[val] += 1
    return votes


def _montant_zonal(img: Image.Image, label: str) -> Optional[float]:
    """
    OCR ZONAL — lecture ciblée du montant à payer pour les formats FIXES.
    On cible la zone du bordereau « Montant à payer » et on lit en mode CHIFFRES.
    Plusieurs seuillages/PSM « votent » ; on retient la valeur la plus fréquente.
    Pour SONEDE (petit ticket souvent photographié de travers), on teste aussi
    quelques rotations. Renvoie le montant en TND, ou None si non concluant.
    """
    from collections import Counter
    try:
        rgb = img.convert("RGB")
        if label == "STEG":
            W, H = rgb.size
            crop = rgb.crop((0, int(0.84 * H), int(0.50 * W), int(0.93 * H)))  # case rouge bas-gauche
            votes = _vote_amounts(crop)
        elif label == "SONEDE":
            # l'orientation est déjà corrigée en amont (_auto_rotate/_best_orientation) ;
            # on balaie le haut ET le bas du ticket (le total peut être de part ou d'autre).
            W, H = rgb.size
            votes = Counter()
            for (t, b) in [(0.55, 1.0), (0.0, 0.50)]:
                band = rgb.crop((0, int(t * H), W, int(b * H)))
                votes += _vote_amounts(band)
        else:
            return None
        if not votes:
            return None
        montant, n = votes.most_common(1)[0]
        return montant if n >= 2 else None
    except Exception as e:
        log.warning(f"  ⚠️ OCR zonal montant échoué : {e}")
        return None


def _reference_zonal(img: Image.Image, label: str) -> Optional[str]:
    """
    Lecture zonale de la référence client pour les formats fixes.
    STEG : zone « Référence » en haut à gauche, format « 23269 122 0 »
    (5 chiffres + 3 chiffres + 1 chiffre). Évite de prendre le n° de compteur.
    """
    from collections import Counter
    if label != "STEG":
        return None
    try:
        rgb = img.convert("RGB"); W, H = rgb.size
        votes: "Counter[str]" = Counter()
        for top in (0.215, 0.225, 0.235):
            crop = rgb.crop((int(0.10 * W), int(top * H), int(0.42 * W), int((top + 0.028) * H)))
            up = crop.resize((crop.width * 4, crop.height * 4), Image.LANCZOS)
            gray = ImageOps.grayscale(up)
            for T in (120, 150):
                txt = _ocr_digits(gray.point(lambda p, T=T: 255 if p > T else 0), 7, "0123456789 ")
                digits = re.sub(r"\D", "", txt)
                if len(digits) == 9:                       # 5+3+1
                    votes[digits] += 1
        if not votes:
            return None
        d, n = votes.most_common(1)[0]
        if n >= 2:
            return f"{d[:5]} {d[5:8]} {d[8]}"              # « 23269 122 0 »
        return None
    except Exception as e:
        log.warning(f"  ⚠️ OCR zonal référence échoué : {e}")
        return None


def _date_echeance_fr(text: str) -> Optional[str]:
    """
    Cherche la date d'échéance réelle = celle qui suit « payer avant ».
    Fonctionne bien quand l'OCR est en français. Renvoie JJ/MM/AAAA ou None.
    """
    t = text.lower()
    m = re.search(r"payer\s+avant[^0-9]{0,15}(20\d{2})[.\-/](\d{2})[.\-/](\d{2})", t)
    if not m:
        m = re.search(r"avant\s+le[^0-9]{0,15}(20\d{2})[.\-/](\d{2})[.\-/](\d{2})", t)
    if m:
        y, mo, d = m.group(1), m.group(2), m.group(3)
        if 1 <= int(mo) <= 12 and 1 <= int(d) <= 31:
            return f"{d}/{mo}/{y}"
    return None


def _strip_bidi(text: str) -> str:
    """Supprime les caractères bidi/RTL invisibles qui parasitent l'OCR."""
    return re.sub(
        r'[\u200e\u200f\u202a\u202b\u202c\u202d\u202e\u200b\u200c\u200d\ufeff]',
        ' ', text
    )


# ════════════════════════════════════════════════════════════════
# PARSERS — Fournisseur, Montant, Date, Référence
# ════════════════════════════════════════════════════════════════

def _extract_fournisseur(text: str) -> Tuple[Optional[str], Optional[str], float]:
    """Retourne (label, nom_affiche, confiance 0-1)."""
    best_label, best_nom, best_score = None, None, 0.0
    for label, nom, keywords in FOURNISSEUR_PATTERNS:
        hits = sum(1 for kw in keywords if kw in text)
        score = hits / len(keywords)
        if score > best_score:
            best_label, best_nom, best_score = label, nom, score
    return best_label, best_nom, best_score


def _parse_montant_raw(raw: str) -> Optional[float]:
    """Convertit "129,000" / "129.000" → float (notation tunisienne millimes)."""
    raw = raw.strip().replace(" ", "").replace("\xa0", "")
    m = re.fullmatch(r'(\d{1,4})[,.](\d{3})', raw)
    if m:
        return float(f"{m.group(1)}.{m.group(2)}")
    m = re.fullmatch(r'\d+', raw)
    if m:
        return float(raw)
    return None


def _extract_montant(text: str, fournisseur_label: Optional[str] = None) -> Tuple[Optional[float], str]:
    """
    Extrait le montant de la facture.

    Retourne (montant, niveau_confiance) où niveau_confiance ∈ {"haute", "moyenne", "faible"}

    ORDRE DE PRIORITÉ v4.1 :
    ══════════════════════════════════════════════════════════
    P0 — Bulletin de versement CCP            → confiance HAUTE
    P1 — المبلغ المطلوب / Montant à payer     → confiance HAUTE
    P1bis — MONTANT TOTAL                      → confiance HAUTE
    P2 — Net à payer / Total facture          → confiance HAUTE
    P3 — Valeur → libellé (inversé)           → confiance MOYENNE
    P4 — Montant suivi de TND/DT              → confiance MOYENNE
    P5 — Fallback intelligent                  → confiance FAIBLE
    ══════════════════════════════════════════════════════════
    """
    text = _strip_bidi(text)

    # ── P0 : Bulletin de versement CCP ──────────────────────────────────────
    # ✅ v4 : tolère bruit OCR entre montant et ref CCP ("l", espaces, lettres)
    # Ex: "90,000 0000063232691220" / "90,000  00007 l e01220" / "90,000 000097772791220"
    patterns_p0 = [
        # Montant puis ref CCP standard (0000006...)
        r'(\d{1,4}[,.]\d{3})\s{1,10}0{3,}[\s\w]{0,3}6?[\d\s]{8,20}',
        # Montant dans section "bulletin de versement" (cherche dans un rayon)
        r'montant\s*[\n\s]{1,20}(\d{1,4}[,.]\d{3})',
        # Ligne "Code ... Montant X,XXX ... Reference ..."
        r'code\s+montant\s+(\d{1,4}[,.]\d{3})',
    ]
    for pat in patterns_p0:
        match = re.search(pat, text, re.IGNORECASE | re.DOTALL)
        if match:
            val = _parse_montant_raw(match.group(1))
            if val and 5 < val < 9999:
                log.info(f"  ✅ [P0-bulletin_versement] : {val} TND")
                return round(val, 3), "haute"

    # ── P1 : المبلغ المطلوب / Montant à payer (PRIORITÉ MAXIMALE) ──────────
    patterns_p1 = [
        # Arabe : المبلغ المطلوب suivi du montant
        r'المبلغ\s*المطلوب[^\d]{0,30}(\d{1,4}[,.]\d{3})',
        r'(\d{1,4}[,.]\d{3})\s{0,10}المبلغ\s*المطلوب',
        r'(\d{1,4}[,.]\d{3})\s{0,10}المطلوب',
        r'المطلوب\s{0,10}(\d{1,4}[,.]\d{3})',
        # Français accent normal
        r'montant\s+[aà]\s+payer\s*[:\-=|]{0,3}\s*(\d{1,4}[,.]\d{3})',
        r'(\d{1,4}[,.]\d{3})\s*[|:\-]?\s*montant\s+[aà]\s+payer',
        # Sans accent (OCR perd l'accent)
        r'montant\s+a\s+payer\s*[:\-=|]{0,3}\s*(\d{1,4}[,.]\d{3})',
        r'(\d{1,4}[,.]\d{3})\s*[|:\-]?\s*montant\s+a\s+payer',
        # ✅ v4 : bruit OCR entre valeur et libellé (ex: "90,000 nan vayer")
        r'(\d{1,4}[,.]\d{3})\s+\w{0,10}\s+vayer',
        r'(\d{1,4}[,.]\d{3})\s+\w{0,10}\s+payer',
        # ✅ v4 : "90,000 prière de payer avant" — cas Run 1 facture STEG
        r'(\d{1,4}[,.]\d{3})\s+pri[eè]?re?\s+de\s+payer',
        r'(\d{1,4}[,.]\d{3})\s+pri.re\s+de\s+payer',
        # Libellé (16) et (19) sur facture STEG
        r'\(\s*19\s*\)[^\d]{0,40}(\d{1,4}[,.]\d{3})',
        r'\(\s*16\s*\)[^\d]{0,40}(\d{1,4}[,.]\d{3})',
    ]
    for pat in patterns_p1:
        match = re.search(pat, text, re.IGNORECASE)
        if match:
            raw = match.group(1)
            val = _parse_montant_raw(raw)
            if val and 5 < val < 9999:
                log.info(f"  ✅ [P1-montant_a_payer] : {val} TND")
                return round(val, 3), "haute"

    # ── P1bis : MONTANT TOTAL libellé (16) rouge STEG ──────────────────────
    patterns_p1bis = [
        r'montant\s+total\s*[:\-=|]{0,3}\s*(\d{1,4}[,.]\d{3})',
        r'(\d{1,4}[,.]\d{3})\s*[|:\-]?\s*montant\s+total',
        # Arabe : المبلغ الجملي
        r'المبلغ\s*الجملي[^\d]{0,30}(\d{1,4}[,.]\d{3})',
        r'(\d{1,4}[,.]\d{3})[^\d]{0,10}المبلغ\s*الجملي',
        # ✅ v4 : variantes OCR "tortal", "roral", "jotal", "totel"
        r'montant\s+[tjr][oa]?r?[ta]l\s*[:\-=|]{0,3}\s*(\d{1,4}[,.]\d{3})',
        r'(\d{1,4}[,.]\d{3})\s+montant\s+[tjr][oa]?r?[ta]l',
        # Juste avant "roral/tortal" (cas Run 2 : "90,774 montant tortal")
        r'(\d{1,4}[,.]\d{3})\s+montant\s+\w{4,6}',
    ]
    for pat in patterns_p1bis:
        match = re.search(pat, text, re.IGNORECASE)
        if match:
            val = _parse_montant_raw(match.group(1))
            if val and 5 < val < 9999:
                log.info(f"  ✅ [P1bis-montant_total] : {val} TND")
                return round(val, 3), "haute"

    # ── P2 : Libellé AVANT la valeur (français) ─────────────────────────────
    patterns_p2 = [
        r'net\s+[aà]\s+payer\s*[:\-=]?\s*([\d]{1,4}[,.][\d]{3})',
        r'total\s+facture\s*[:\-=]?\s*([\d]{1,4}[,.][\d]{3})',
        r'montant\s+simulé\s*[:\-=]?\s*([\d]{1,4}[,.][\d]{3})',
        r'المبلغ\s*التقديري\s*[:\-=]?\s*([\d]{1,4}[,.][\d]{3})',
        r'total\s+consommation\s+et\s+services\s*[:\-=]?\s*([\d]{1,4}[,.][\d]{3})',
    ]
    for pat in patterns_p2:
        match = re.search(pat, text, re.IGNORECASE)
        if match:
            val = _parse_montant_raw(match.group(1))
            if val and 5 < val < 9999:
                log.info(f"  ✅ [P2-label_avant] : {val} TND")
                return round(val, 3), "haute"

    # ── P3 : Valeur AVANT le libellé (ordre inversé OCR bilingue) ───────────
    patterns_p3 = [
        r'([\d]{1,4}[,.][\d]{3})\s*(?:net\s+[aà]\s+payer)',
        r'([\d]{1,4}[,.][\d]{3})\s*(?:total\s+facture)',
    ]
    for pat in patterns_p3:
        match = re.search(pat, text, re.IGNORECASE)
        if match:
            val = _parse_montant_raw(match.group(1))
            if val and 5 < val < 9999:
                log.info(f"  ✅ [P3-valeur_avant] : {val} TND")
                return round(val, 3), "moyenne"

    # ── P4 : Montant suivi de TND / DT / Dinars / دينار ─────────────────────
    pat_tnd = r'([\d]{1,4}[,.][\d]{3})\s*(?:TND|DT|Dinars?|دينار)'
    match = re.search(pat_tnd, text, re.IGNORECASE)
    if match:
        val = _parse_montant_raw(match.group(1))
        if val and 5 < val < 9999:
            log.info(f"  ✅ [P4-tnd_suffix] : {val} TND")
            return round(val, 3), "moyenne"

    # ── P5 : Fallback intelligent ────────────────────────────────────────────
    # ✅ v4 : pour STEG, le montant à payer est TOUJOURS le plus grand nombre
    # cohérent (sauf ref client 71.364.322 qui est > 1000)
    candidats_raw = re.findall(r'\b(\d{1,4})[,.](\d{3})\b', text)
    valeurs_valides = []
    for entier_str, dec_str in candidats_raw:
        entier = int(entier_str)
        val = float(f"{entier}.{dec_str}")
        # Exclure téléphones tunisiens
        est_telephone = any([
            70 <= entier <= 79,
            20 <= entier <= 29,
            50 <= entier <= 59,
            90 <= entier <= 99,
            ])
        # Exclure références internes courtes (2326, 6212, etc.)
        est_reference_interne = entier > 2000
        if not est_telephone and not est_reference_interne and 5 < val < 9999:
            valeurs_valides.append(val)

    if valeurs_valides:
        valeurs_valides.sort()
        # ✅ CORRECTION : pour STEG/SONEDE on NE renvoie PLUS le max de nombres
        # parasites (c'est ce qui produisait le faux « 486,024 »). Si le champ
        # « Montant à payer » n'a pas été reconnu (P0–P4), on préfère renvoyer
        # une confiance faible SANS montant imposé → l'application demandera à
        # l'utilisateur de confirmer/saisir le montant.
        if fournisseur_label in ("STEG", "SONEDE"):
            log.warning(
                f"  🚫 [P5] Champ « Montant à payer » non reconnu pour {fournisseur_label} "
                f"(candidats parasites={valeurs_valides}). Aucun montant imposé → confirmation manuelle."
            )
            return None, "faible"
        # Autres fournisseurs (facture simple) : médiane en confiance faible
        val = valeurs_valides[len(valeurs_valides) // 2]
        log.info(f"  ⚠️ [P5-fallback_mediane] : {val} TND parmi {valeurs_valides} (FAIBLE CONFIANCE)")
        return round(val, 3), "faible"

    log.warning("  ❌ Aucun montant valide trouvé")
    return None, "faible"


# ════════════════════════════════════════════════════════════════
# CORRECTION OCR — JOURS DE DATE
# ════════════════════════════════════════════════════════════════

def _corrige_jour_ocr(jour_str: str) -> Optional[int]:
    """Corrige les erreurs OCR courantes sur les jours : 0→6, 1→4."""
    try:
        d = int(jour_str)
        if 1 <= d <= 31:
            return d
        if 60 <= d <= 69:
            d_corrige = d - 60
            if 1 <= d_corrige <= 31:
                return d_corrige
        if 40 <= d <= 49:
            d_corrige = d - 30
            if 1 <= d_corrige <= 31:
                return d_corrige
        return None
    except (ValueError, TypeError):
        return None


# ════════════════════════════════════════════════════════════════
# EXTRACTION DATE
# ════════════════════════════════════════════════════════════════

def _extract_date(text: str) -> Optional[str]:
    """
    Extrait la date d'échéance et la normalise en DD/MM/YYYY.

    v4 : Ajout pattern compact avec bruit OCR autour ("20230605" collé).
    """
    text = _strip_bidi(text)

    # ── P0 : "Prière de payer avant le AAAA.MM.JJ" ──────────────────────────
    p_payer_avant = (
        r'(?:'
        r'prière\s+de\s+payer\s+avant\s+le'
        r'|pri.re\s+de\s+payer\s+avant'
        r'|الرجاء\s+الدفع\s+قبل'
        r'|payer\s+avant\s+le'
        r'|payer\s+avant'
        r')\s{0,5}(20\d{2})[.\-/](0[1-9]|1[0-2])[.\-/](\d{2})'
    )
    for match in re.finditer(p_payer_avant, text, re.IGNORECASE):
        y, m_val, d_raw = match.group(1), match.group(2), match.group(3)
        d = _corrige_jour_ocr(d_raw)
        if d:
            try:
                datetime(int(y), int(m_val), d)
                result = f"{d:02d}/{int(m_val):02d}/{y}"
                log.info(f"  ✅ [P0-payer_avant] : {result} (brut OCR={d_raw})")
                return result
            except ValueError:
                continue

    # ── P1 : Format ISO AAAA.MM.JJ (avec correction OCR) ───────────────────
    p_iso = r'\b(20\d{2})[.\-/](0[1-9]|1[0-2])[.\-/](\d{2})\b'
    dates_candidates = []
    for match in re.finditer(p_iso, text):
        y, m_val, d_raw = match.groups()
        d = _corrige_jour_ocr(d_raw)
        if d:
            try:
                dt = datetime(int(y), int(m_val), d)
                dates_candidates.append((dt, f"{d:02d}/{int(m_val):02d}/{y}", d_raw))
            except ValueError:
                continue

    if dates_candidates:
        now = datetime.now()
        futures = [(dt, fmt, raw) for dt, fmt, raw in dates_candidates
                   if dt >= now - timedelta(days=365 * 3)]
        if futures:
            futures.sort(key=lambda x: x[0])
            chosen = futures[0]
            log.info(f"  ✅ [P1-iso_souple] : {chosen[1]} (brut OCR={chosen[2]})")
            return chosen[1]
        dates_candidates.sort(key=lambda x: x[0], reverse=True)
        chosen = dates_candidates[0]
        log.info(f"  ✅ [P1-iso_recent] : {chosen[1]}")
        return chosen[1]

    # ── P2 : Dates compactes YYYYMMDD collées ───────────────────────────────
    # ✅ v4 : accepte aussi "29230605" (OCR confond 2→2 mais 0→9 en première pos du mois)
    # Pattern plus permissif : cherche 8 chiffres consécutifs avec année valide
    p_compact = r'\b(20\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\b'
    dates_futures = []
    for y, m_val, d in re.findall(p_compact, text):
        try:
            dt = datetime(int(y), int(m_val), int(d))
            if datetime.now() - timedelta(days=365*3) <= dt <= datetime.now() + timedelta(days=365*5):
                dates_futures.append((dt, f"{d}/{m_val}/{y}"))
        except ValueError:
            continue
    if dates_futures:
        dates_futures.sort(key=lambda x: x[0])
        log.info(f"  ✅ [P2-compact] : {dates_futures[0][1]}")
        return dates_futures[0][1]

    # ── P2bis : Date compacte avec bruit OCR ────────────────────────────────
    # ✅ v4 : "29230605" où OCR a confondu le 2ème chiffre
    # On cherche une séquence 8 chiffres dont positions 2-3-4 = mois et 5-6-7 = jour valides
    p_compact_souple = r'(?:2[09]\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\b'
    for match in re.finditer(p_compact_souple, text):
        full = match.group(0)
        # Forcer année 2023-2026 (confusion 29230 → 20230)
        y_corrected = '20' + full[2:4] if full[:2] == '29' else full[:4]
        m_val, d = match.group(1), match.group(2)
        try:
            dt = datetime(int(y_corrected), int(m_val), int(d))
            if datetime.now() - timedelta(days=365*3) <= dt <= datetime.now() + timedelta(days=365*5):
                result = f"{d}/{m_val}/{y_corrected}"
                log.info(f"  ✅ [P2bis-compact_corrige] : {result} (brut={full})")
                return result
        except ValueError:
            continue

    # ── P3 : Format JJ/MM/AAAA ──────────────────────────────────────────────
    p_fr = r'\b(0[1-9]|[12]\d|3[01])[/\-\.](0[1-9]|1[0-2])[/\-\.](20\d{2})\b'
    match = re.search(p_fr, text)
    if match:
        d, m_val, y = match.groups()
        try:
            datetime(int(y), int(m_val), int(d))
            log.info(f"  ✅ [P3-fr_format] : {d}/{m_val}/{y}")
            return f"{d}/{m_val}/{y}"
        except ValueError:
            pass

    log.warning("  ❌ Aucune date valide trouvée")
    return None


# ════════════════════════════════════════════════════════════════
# EXTRACTION RÉFÉRENCE
# ════════════════════════════════════════════════════════════════

def _extract_reference(text: str) -> Optional[str]:
    """
    Extrait la référence client ou numéro de facture.

    v4 : Référence STEG format "23269 122 0" en priorité absolue
    (rejette codes-barres poubelle type "100000100000001").
    """
    text = _strip_bidi(text)

    # ✅ PRIORITÉ STEG : la référence « 23269 122 0 » (5+3+1 chiffres) apparaît
    # plusieurs fois dans l'en-tête, contrairement au n° de compteur / aux index.
    # On retient donc le motif « \d5 \d3 \d » le PLUS fréquent du texte.
    from collections import Counter
    steg_refs = re.findall(r"\b(\d{5})\s+(\d{3})\s*[.\s]\s*(\d)\b", text)
    if steg_refs:
        counter = Counter(f"{a} {b} {c}" for a, b, c in steg_refs)
        best, n = counter.most_common(1)[0]
        digits = re.sub(r"\D", "", best)
        if not _is_junk_reference(digits):
            log.info(f"  ✅ Référence (motif STEG fréquent ×{n}) : {best}")
            return best

    patterns = [
        # ✅ v4 PRIORITÉ ABSOLUE : "Référence 23269 122 0" exact format STEG
        r'r[eé]f[eé]?r?[ai]?n?c?e?s?\s+(\d{5}\s+\d{3}\s+\d)',
        # Référence avec libellé classique
        r'r[eé]f[eé]rence\s*[:\-=]?\s*([\d][\d\s]{4,19}[\d])',
        r'ref\s*[:\-=]?\s*([\d][\d\s]{4,19}[\d])',
        # Numéro client / compteur
        r'n[o°\.]\s*client\s*[:\-=]?\s*([\d][\d\s]{4,19}[\d])',
        r'n[o°\.]\s*compteur\s*[:\-=]?\s*([\d][\d\s]{4,19}[\d])',
        r'n\.compteur\s*[:\-=]?\s*([\d][\d\s]{4,19}[\d])',
        r'facture\s*n[o°]\s*[:\-=]?\s*([\d][\d\s]{4,19}[\d])',
        # Référence bulletin CCP (17 chiffres, commence par 0000006)
        r'\b(0{4,}6[\d]{10,16})\b',
        # Fallback long nombre (code-barre) — MAIS rejeter les séquences répétitives
        r'\b(\d{12,20})\b',
    ]

    for pattern in patterns:
        for match in re.finditer(pattern, text, re.IGNORECASE):
            ref = re.sub(r'\s+', '', match.group(1))
            if 6 <= len(ref) <= 20:
                # ✅ v4 : rejeter les codes-barres poubelle (répétition 100000100000001)
                if _is_junk_reference(ref):
                    continue
                log.info(f"  ✅ Référence : {ref}")
                return ref

    log.warning("  ❌ Aucune référence trouvée")
    return None


def _is_junk_reference(ref: str) -> bool:
    """Détecte les références OCR poubelle : répétitions, trop de zéros."""
    # Trop de zéros consécutifs (> 6 sauf CCP légitime)
    if not ref.startswith('0000006') and re.search(r'0{7,}', ref):
        return True
    # Pattern répétitif type "100000100000001"
    if re.match(r'^(10+)+1?$', ref):
        return True
    # Plus de 80% de zéros
    if ref.count('0') / len(ref) > 0.8:
        return True
    return False


# ════════════════════════════════════════════════════════════════
# SCAN PRINCIPAL
# ════════════════════════════════════════════════════════════════

def scan_facture(image_b64: str) -> Dict[str, Any]:
    """Analyse une image de facture et retourne les champs extraits."""
    result: Dict[str, Any] = {
        "fournisseur_label":     None,
        "fournisseur_nom":       None,
        "montant":               None,
        "date_echeance":         None,
        "reference":             None,
        "confiance_fournisseur": 0.0,
        "confiance_globale":     "faible",
        "texte_brut":            "",
        "champs_manquants":      [],
        "succes":                False,
    }

    try:
        img_orig = _decode_image(image_b64)
        log.info(f"📸 Image décodée : {img_orig.size[0]}x{img_orig.size[1]} pixels")

        # ✅ Redressement automatique (corrige les factures tournées à 90°)
        img_orig = _auto_rotate(img_orig)

        img = _preprocess_image(img_orig.copy())
        log.info("🧹 Image prétraitée (passe standard)")

        # ✅ v4.1 : multi-passes Tesseract (psm 6 + 11 + 4)
        text = _run_ocr_multi(img)
        result["texte_brut"] = text[:3000]

        log.info("=" * 60)
        log.info("📝 TEXTE BRUT EXTRAIT PAR TESSERACT (multi-PSM) :")
        log.info("=" * 60)
        log.info(text[:2500])
        log.info("=" * 60)

        label, nom, conf_four = _extract_fournisseur(text)
        log.info(f"🔍 Fournisseur : label={label}, nom={nom}, confiance={conf_four:.3f}")
        result["fournisseur_label"]     = label
        result["fournisseur_nom"]       = nom
        result["confiance_fournisseur"] = round(conf_four, 3)

        # ✅ OCR ZONAL EN PRIORITÉ (formats fixes STEG/SONEDE) :
        # on lit le montant directement dans la case « Montant à payer », en mode
        # chiffres. C'est ce qui permet de lire le « 102,000 » au lieu de parasites.
        montant, conf_montant = None, "faible"
        if label in ("STEG", "SONEDE"):
            mz = _montant_zonal(img_orig, label)
            if mz is not None:
                montant, conf_montant = mz, "haute"
                log.info(f"💰 Montant ZONAL ({label}) : {montant} TND (haute confiance)")

        # Sinon, repli sur l'extraction depuis le texte global (multi-PSM)
        if montant is None:
            montant, conf_montant = _extract_montant(text, fournisseur_label=label)
            log.info(f"💰 Montant (texte) : {montant} (confiance={conf_montant})")

        # ✅ v4.1 : si confiance FAIBLE, tenter le canal rouge
        if conf_montant == "faible" and label in ("STEG", "SONEDE"):
            log.info(f"⚠️  Montant {montant} en confiance FAIBLE — passe canal rouge…")
            try:
                img_rouge = _preprocess_image_rouge(img_orig.copy())
                text_rouge = _run_ocr_multi(img_rouge)
                log.info("📝 TEXTE CANAL ROUGE :")
                log.info(text_rouge[:1500])

                montant_rouge, conf_rouge = _extract_montant(text_rouge, fournisseur_label=label)
                log.info(f"💰 Montant canal rouge : {montant_rouge} (confiance={conf_rouge})")

                # Si la passe rouge a trouvé en HAUTE confiance, on la prend
                if montant_rouge and conf_rouge == "haute":
                    log.info(f"  ✅ Canal rouge retenu (haute confiance) : {montant_rouge} TND")
                    montant = montant_rouge
                    conf_montant = "haute"
                    text = text + "\n\n" + text_rouge
                    result["texte_brut"] = text[:3000]
            except Exception as e:
                log.warning(f"  ⚠️ Passe canal rouge échouée : {e}")

        # ✅ v4.1 : GARDE-FOU CRITIQUE
        # Pour STEG/SONEDE, un montant en faible confiance ne doit PAS être renvoyé
        # car le fallback P5 se base sur des lignes de détail (taxes, arriérés) qui
        # ne sont PAS le montant à payer. Mieux vaut renvoyer null que 6.064 au lieu de 90.
        if conf_montant == "faible" and label in ("STEG", "SONEDE") and montant is not None and montant < 25:
            log.warning(
                f"  🚫 GARDE-FOU : montant {montant} trop petit pour {label} en confiance faible "
                f"— très probablement une ligne de détail. Refus du montant."
            )
            montant = None

        result["montant"] = montant
        result["confiance_montant"] = conf_montant

        # Date d'échéance : priorité à « payer avant le … » (OCR français),
        # sinon repli sur l'extraction de date générique.
        date_echeance = _date_echeance_fr(text) or _extract_date(text)
        log.info(f"📅 Date extraite : {date_echeance}")
        result["date_echeance"] = date_echeance

        # Référence : extraction ZONALE pour les formats fixes (évite de prendre
        # le numéro de compteur), sinon repli sur l'extraction depuis le texte.
        reference = _reference_zonal(img_orig, label) or _extract_reference(text)
        log.info(f"🔢 Référence extraite : {reference}")
        result["reference"] = reference

        manquants = []
        if not label:                   manquants.append("fournisseur")
        if not result["montant"]:       manquants.append("montant")
        if not result["date_echeance"]: manquants.append("date_echeance")
        if not result["reference"]:     manquants.append("reference")
        result["champs_manquants"] = manquants

        nb_extraits = 4 - len(manquants)

        if nb_extraits == 4 and conf_four >= 0.05:
            result["confiance_globale"] = "haute"
        elif nb_extraits >= 3:
            result["confiance_globale"] = "haute"
        elif nb_extraits >= 2:
            result["confiance_globale"] = "moyenne"
        else:
            result["confiance_globale"] = "faible"

        # ✅ CORRECTION : la confiance globale ne peut pas être « haute » si le
        # montant lui-même n'est pas fiable. On la plafonne par la confiance du
        # montant → cohérence des logs et déclenchement de la confirmation manuelle.
        if conf_montant != "haute" and result["confiance_globale"] == "haute":
            result["confiance_globale"] = "moyenne"
        if result["montant"] is None:
            result["confiance_globale"] = "faible"
        # indicateur explicite pour le front : faut-il demander à l'utilisateur ?
        result["confirmation_requise"] = (conf_montant != "haute") or (result["montant"] is None)

        result["succes"] = nb_extraits >= 2
        log.info(
            f"✅ OCR scan : {nom} | montant={result['montant']} TND "
            f"(conf_montant={conf_montant}) | date={result['date_echeance']} | "
            f"conf_globale={result['confiance_globale']} | "
            f"confirmation_requise={result['confirmation_requise']}"
        )

    except Exception as e:
        log.error(f"❌ OCR scan échoué : {e}", exc_info=True)
        result["erreur"] = str(e)

    return result


# ════════════════════════════════════════════════════════════════
# DÉTECTIVE DE FACTURES
# ════════════════════════════════════════════════════════════════

def detect_anomalie(
        label: str,
        montant_actuel: float,
        historique_montants: List[float],
        seuil_pct: float = 30.0,
) -> Dict[str, Any]:
    """Compare le montant scanné avec l'historique du client."""
    if not historique_montants or len(historique_montants) < 2:
        return {
            "anomalie":          False,
            "message":           "Historique insuffisant pour analyse",
            "severite":          None,
            "pourcentage_ecart": 0.0,
            "montant_moyen":     montant_actuel,
            "historique":        historique_montants or [],
            "causes_possibles":  [],
        }

    import statistics

    montant_moyen = statistics.mean(historique_montants)
    ecart_pct     = ((montant_actuel - montant_moyen) / montant_moyen * 100) if montant_moyen > 0 else 0.0
    mediane       = statistics.median(historique_montants)

    anomalie = ecart_pct > seuil_pct
    severite = None
    if anomalie:
        severite = "haute" if ecart_pct > 60 else "moyenne"

    causes  = _causes_possibles(label, ecart_pct, montant_actuel)
    message = (
        f"Votre facture {label} est de {montant_actuel:.3f} TND, "
        f"soit +{ecart_pct:.0f}% par rapport à votre moyenne de {montant_moyen:.0f} TND"
        if anomalie
        else f"Montant normal — votre moyenne est de {montant_moyen:.0f} TND"
    )

    return {
        "anomalie":           anomalie,
        "severite":           severite,
        "pourcentage_ecart":  round(ecart_pct, 1),
        "montant_moyen":      round(montant_moyen, 3),
        "mediane":            round(mediane, 3),
        "historique":         historique_montants,
        "causes_possibles":   causes,
        "message":             message,
    }


def _causes_possibles(label: str, ecart_pct: float, montant: float) -> List[str]:
    """Génère des causes probables selon le fournisseur et l'écart."""
    mois   = datetime.now().month
    causes = []

    if label == "STEG":
        if mois in [6, 7, 8]:
            causes.append("Climatisation intensive (période estivale)")
        elif mois in [12, 1, 2]:
            causes.append("Chauffage électrique en hiver")
        causes.append("Nouvel appareil électroménager à forte consommation")
        causes.append("Erreur de relevé du compteur STEG")
        if ecart_pct > 80:
            causes.append("Fuite électrique — vérifiez votre installation")
    elif label == "SONEDE":
        if mois in [5, 6, 7, 8]:
            causes.append("Arrosage jardin / piscine en été")
        causes.append("Fuite d'eau invisible (compteur qui tourne)")
        causes.append("Changement de tarif SONEDE")
        if ecart_pct > 100:
            causes.append("Erreur de lecture du compteur — contestez la facture")
    elif label in ["TOPNET", "BEE"]:
        causes.append("Changement d'offre ou de forfait internet")
        causes.append("Frais de dépassement de quota")
        causes.append("Équipement supplémentaire (décodeur, TV box)")
    elif label in ["TT", "OOREDOO"]:
        causes.append("Frais de dépassement forfait mobile")
        causes.append("Appels internationaux hors forfait")
        causes.append("Activation d'un service premium")
    else:
        causes.append("Vérifiez les détails de la facture")
        causes.append("Comparez avec votre contrat de service")

    return causes[:3]


# ════════════════════════════════════════════════════════════════
# CALCUL IMPACT SOLDE
# ════════════════════════════════════════════════════════════════

def calcul_impact_solde(
        solde_actuel: float,
        montant_facture: float,
        depenses_mois: float,
        budget_mensuel_estime: float,
) -> Dict[str, Any]:
    """Calcule l'impact d'un paiement sur le solde et le budget mensuel."""
    solde_apres    = solde_actuel - montant_facture
    depenses_apres = depenses_mois + montant_facture
    pct_budget     = (depenses_apres / budget_mensuel_estime * 100) if budget_mensuel_estime > 0 else 0

    if solde_apres < 0:
        niveau  = "critique"
        message = f"⚠️ Solde insuffisant ! Il vous manque {abs(solde_apres):.0f} TND"
    elif solde_apres < 30:
        niveau  = "critique"
        message = f"Solde très bas après paiement : {solde_apres:.3f} TND"
    elif pct_budget > 90:
        niveau  = "attention"
        message = f"Budget presque épuisé ({pct_budget:.0f}% utilisé)"
    elif pct_budget > 70:
        niveau  = "attention"
        message = f"Vous aurez utilisé {pct_budget:.0f}% de votre budget mensuel"
    else:
        niveau  = "ok"
        message = f"Vous aurez utilisé {pct_budget:.0f}% de votre budget mensuel"

    return {
        "solde_apres":           round(solde_apres, 3),
        "pct_budget_utilise":    round(pct_budget, 1),
        "niveau_alerte":         niveau,
        "message":               message,
        "depenses_totales_mois": round(depenses_apres, 3),
    }


# ════════════════════════════════════════════════════════════════
# TEST RAPIDE
# ════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import sys, base64, json

    logging.basicConfig(
        level=logging.INFO,
        format="%(levelname)-8s  %(message)s"
    )

    if len(sys.argv) < 2:
        print("Usage : python ocr_service.py <chemin_image>")
        sys.exit(1)

    path = sys.argv[1]
    with open(path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode()

    result = scan_facture(b64)
    print("\n" + "=" * 60)
    print("RÉSULTAT OCR")
    print("=" * 60)
    print(f"  Fournisseur  : {result['fournisseur_nom']}  (confiance {result['confiance_fournisseur']:.2%})")
    print(f"  Montant      : {result['montant']} TND  (confiance {result.get('confiance_montant', 'N/A')})")
    print(f"  Échéance     : {result['date_echeance']}")
    print(f"  Référence    : {result['reference']}")
    print(f"  Confiance    : {result['confiance_globale'].upper()}")
    print(f"  Manquants    : {result['champs_manquants'] or 'aucun'}")
    print("=" * 60)