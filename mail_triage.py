#!/usr/bin/env python3
"""
mail_triage.py - score every sender in 10+ years of Gmail from "critical" to
"trash", so you can move services to your new (Tuta) address in a targeted way.

Pure algorithms, no AI. Reads ONLY headers + Gmail flags/labels (never bodies).
Mailbox is opened read-only. Nothing leaves your computer.

Install once:   pip install openpyxl        (without it you get CSV files)

Read straight from Gmail (needs an app password, see myaccount.google.com/apppasswords):
    python3 mail_triage.py imap --user you@gmail.com
Or from a Google Takeout .mbox (fully offline):
    python3 mail_triage.py mbox "All mail Including Spam and Trash.mbox" --me you@gmail.com

Options:
    --me ADDR          extra own addresses/aliases (repeatable)
    --passwords FILE   Google Password Manager CSV export (only url+username read)
    --out FILE         output workbook (default mail_triage.xlsx)
    --cache FILE       sqlite header cache (default mail_cache.sqlite). Re-runs
                       only fetch NEW mail, so tweak and re-run freely.
    --report-only      skip reading mail, rebuild the report from the cache

Workbook tabs:
    Summary       totals, tiers, mail per year (chart), categories
    Services      every sender domain, ~35 metrics, score, tier, suggested action
    Change email  your worklist: services where you have an account, by priority
    Unsubscribe   newsletters/promos with their unsubscribe link
    Dormant       accounts you haven't heard from in 3+ years (delete or ignore)
    People        real people you corresponded with (tell them your new address)
    Timeline      heat-map: mails per year for the top services
    How scored    the scoring rules, so you can judge/tune them
"""

import argparse
import csv
import datetime as dt
import email
import email.policy
import getpass
import imaplib
import mailbox
import math
import re
import sqlite3
import statistics
import sys
import urllib.parse
from collections import Counter, defaultdict
from email.header import decode_header, make_header
from email.utils import getaddresses, parseaddr, parsedate_to_datetime

try:
    import openpyxl
    from openpyxl.chart import BarChart, Reference
    from openpyxl.formatting.rule import ColorScaleRule, DataBarRule
    from openpyxl.styles import Alignment, Font, PatternFill
    from openpyxl.utils import get_column_letter
except ImportError:
    openpyxl = None

TODAY = dt.date.today()

# =============================================================== knowledge base

SIGNALS = {
    "security": ["password", "wachtwoord", "2fa", "two-factor", "tweestaps",
                 "verification code", "verificatiecode", "inlogcode", "login code",
                 "security alert", "beveiligingsmelding", "beveiliging", "new sign-in",
                 "nieuwe aanmelding", "new login", "new device", "nieuw apparaat",
                 "reset", "one-time", "eenmalige", "sign-in attempt", "unusual"],
    "welcome": ["welcome", "welkom", "verify", "verifieer", "confirm your",
                "bevestig", "activate", "activeer", "registration", "registratie",
                "account created", "account aangemaakt", "sign up", "signup",
                "aanmelding", "your account", "je account", "uw account"],
    "txn": ["order", "bestelling", "receipt", "ontvangstbewijs", "invoice",
            "factuur", "payment", "betaling", "shipped", "verzonden", "bezorg",
            "delivery", "levering", "booking", "boeking", "reservering",
            "reservation", "ticket", "€", "eur ", "tracking", "track & trace",
            "pakket", "package", "refund", "terugbetaling", "retour"],
    "billing": ["subscription", "abonnement", "renew", "verleng", "incasso",
                "direct debit", "machtiging", "billing", "your bill", "jaaroverzicht",
                "maandoverzicht", "statement", "afschrift", "premie", "termijn",
                "nota", "rekening", "contract", "polis", "aanslag"],
    "marketing": ["sale", "korting", "% off", "deal", "aanbieding", "newsletter",
                  "nieuwsbrief", "offer", "actie", "gratis", "black friday",
                  "cyber monday", "last chance", "laatste kans", "new arrivals",
                  "nieuw binnen", "exclusive", "exclusief", "promo", "weekly",
                  "wekelijks", "digest", "don't miss", "mis het niet", "save ",
                  "bespaar", "tips", "inspiratie", "inspiration", "webinar"],
}

# (category, base points, keywords). Keywords with a dot match whole domains,
# others match as substrings of the domain or sender name.
CATEGORIES = [
    ("Government", 40, ["belastingdienst", "digid", "overheid", "gemeente", "svb.nl",
                        "uwv", "duo.nl", "rdw", "cjib", "toeslagen", "mijnoverheid",
                        "kvk", "rijksoverheid", "politie", "rechtspraak", "ind.nl",
                        "cak.nl", "waterschap"]),
    ("Finance", 40, ["bank", "ing.nl", "ing.com", "rabobank", "abnamro", "bunq",
                     "knab", "asnbank", "snsbank", "triodos", "regiobank", "revolut",
                     "wise.com", "transferwise", "paypal", "klarna", "tikkie",
                     "icscards", "americanexpress", "aexp", "mastercard", "visa.",
                     "afterpay", "riverty", "adyen", "mollie", "creditcard", "bkr"]),
    ("Crypto/Investing", 35, ["coinbase", "bitvavo", "degiro", "binance", "kraken",
                              "meesman", "brandnewday", "trading212", "etoro",
                              "saxo", "flatex", "peaks", "bux", "ledger", "trezor"]),
    ("Insurance/Health", 35, ["verzeker", "insurance", "zilverenkruis", "vgz",
                              "menzis", "cz.nl", "dsw", "ohra", "centraalbeheer",
                              "nn.nl", "achmea", "interpolis", "unive", "anwb",
                              "pensioen", "pension", "abp", "pfzw", "huisarts",
                              "apotheek", "ziekenhuis", "tandarts", "zorg",
                              "mijngezondheid", "thuisarts"]),
    ("Utilities/Telecom", 30, ["eneco", "vattenfall", "essent", "greenchoice",
                               "budgetenergie", "energie", "vandebron", "frank",
                               "vitens", "evides", "waternet", "brabantwater",
                               "ziggo", "kpn", "odido", "t-mobile", "vodafone",
                               "tele2", "simyo", "ben.nl", "lebara", "youfone",
                               "delta", "caiway", "stedin", "liander", "enexis"]),
    ("Identity/Tech", 30, ["apple", "icloud", "microsoft", "live.com", "outlook",
                           "amazon", "github", "gitlab", "dropbox", "1password",
                           "bitwarden", "lastpass", "proton", "transip", "hostnet",
                           "cloudflare", "namecheap", "godaddy", "strato",
                           "mijndomein", "hetzner", "digitalocean", "steampowered",
                           "playstation", "xbox", "nintendo", "epicgames",
                           "facebook", "facebookmail", "instagram", "whatsapp",
                           "twitter", "x.com", "discord", "reddit", "spotify",
                           "netflix", "disney", "videoland", "openai", "anthropic",
                           "adobe", "samsung", "garmin", "fitbit", "tesla"]),
    ("Housing", 25, ["woning", "huur", "verhuur", "vve", "makelaar", "funda",
                     "hypotheek", "kadaster", "woonnet", "woningnet", "pararius"]),
    ("Work/Education", 20, ["salaris", "payroll", "loket", "nmbrs", "afas",
                            "linkedin", "indeed", "school", "universiteit",
                            "hogeschool", "university", "uva.nl", "tudelft",
                            "studielink", "brightspace", "canvas", "moodle"]),
    ("Travel/Mobility", 12, ["ns.nl", "ov-chipkaart", "ovpay", "klm", "transavia",
                             "booking.com", "airbnb", "uber", "bolt.eu", "shell",
                             "parkmobile", "yellowbrick", "easypark", "ryanair",
                             "easyjet", "flixbus", "q-park", "anwb"]),
    ("Shopping", 6, ["bol.com", "coolblue", "zalando", "marktplaats", "aliexpress",
                     "mediamarkt", "ikea", "albert", "ah.nl", "jumbo", "hema",
                     "wehkamp", "thuisbezorgd", "picnic", "vinted", "ebay", "etsy",
                     "decathlon", "action.com", "temu", "shein", "lidl"]),
]

FREEMAIL = {
    "gmail.com", "googlemail.com", "hotmail.com", "hotmail.nl", "outlook.com",
    "outlook.nl", "live.com", "live.nl", "msn.com", "yahoo.com", "yahoo.nl",
    "icloud.com", "me.com", "mac.com", "ziggo.nl", "kpnmail.nl", "planet.nl",
    "home.nl", "hetnet.nl", "xs4all.nl", "chello.nl", "casema.nl", "upcmail.nl",
    "protonmail.com", "proton.me", "pm.me", "tutanota.com", "tuta.io",
    "tutanota.de", "tuta.com", "gmx.com", "gmx.net", "gmx.de", "aol.com",
    "web.de", "mail.com", "zonnet.nl", "telfort.nl", "online.nl", "quicknet.nl",
}

MULTI_PART_TLDS = {
    "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "co.nz",
    "co.za", "com.br", "co.jp", "com.tr", "com.mx", "co.in", "com.sg",
}

NOREPLY_RE = re.compile(r"no-?reply|donotreply|do-not-reply|notification|mailer|"
                        r"newsletter|^news@|^info@|marketing|^service@|bounce|"
                        r"^alerts?@|^updates?@|^hello@|^team@|^support@", re.I)

# =============================================================== small helpers


def base_domain(host):
    host = host.lower().strip(". >")
    parts = host.split(".")
    if len(parts) >= 3 and ".".join(parts[-2:]) in MULTI_PART_TLDS:
        return ".".join(parts[-3:])
    return ".".join(parts[-2:]) if len(parts) >= 2 else host


def decode(value):
    if value is None:
        return ""
    try:
        return str(make_header(decode_header(str(value)))).strip()
    except Exception:
        return str(value).strip()


def parse_date(value):
    try:
        d = parsedate_to_datetime(value).date()
    except Exception:
        return None
    if d.year < 1995 or d > TODAY + dt.timedelta(days=2):
        return None
    return d


def kw_match(kw, domain, text):
    if "." in kw and not kw.endswith("."):
        return domain == kw or domain.endswith("." + kw)
    return kw in domain or kw in text


def categorize(domain, names):
    text = " ".join(names).lower()
    for cat, pts, kws in CATEGORIES:
        if any(kw_match(k, domain, text) for k in kws):
            return cat, pts
    return "", 0


def signals_of(subject):
    low = subject.lower()
    return {k for k, words in SIGNALS.items() if any(w in low for w in words)}


def unsub_link(value):
    links = re.findall(r"<([^>]+)>", value or "")
    for l in links:
        if l.lower().startswith("http"):
            return l
    return links[0] if links else ""


def lg(x, cap):
    """0..1 log-scaled: lg(cap)=1"""
    return min(1.0, math.log1p(max(x, 0)) / math.log1p(cap))


# =============================================================== cache

SCHEMA = """
CREATE TABLE IF NOT EXISTS msgs(
  src TEXT, uid INTEGER, date TEXT, from_addr TEXT, from_name TEXT,
  to_addrs TEXT, subject TEXT, unsub TEXT, bulk INT, attach INT,
  seen INT, flagged INT, important INT, sent INT, category TEXT,
  PRIMARY KEY(src, uid));
CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT);
"""


def open_cache(path):
    db = sqlite3.connect(path)
    db.executescript(SCHEMA)
    return db


def record_from_headers(msg):
    name, addr = parseaddr(decode(msg.get("From")))
    to = [a.lower() for _, a in getaddresses(
        [decode(msg.get("To", "")), decode(msg.get("Cc", ""))]) if "@" in a]
    prec = (msg.get("Precedence") or "").lower()
    ctype = (msg.get("Content-Type") or "").lower()
    d = parse_date(msg.get("Date"))
    return {
        "date": d.isoformat() if d else None,
        "from_addr": addr.lower(),
        "from_name": name[:120],
        "to_addrs": ",".join(to)[:2000],
        "subject": decode(msg.get("Subject"))[:300],
        "unsub": unsub_link(msg.get("List-Unsubscribe")),
        "bulk": int(prec in ("bulk", "list", "junk") or bool(msg.get("List-Id"))),
        "attach": int("multipart/mixed" in ctype),
    }


def insert(db, src, uid, rec, seen, flagged, important, sent, category):
    db.execute(
        "INSERT OR REPLACE INTO msgs VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        (src, uid, rec["date"], rec["from_addr"], rec["from_name"], rec["to_addrs"],
         rec["subject"], rec["unsub"], rec["bulk"], rec["attach"],
         int(seen), int(flagged), int(important), int(sent), category))


# =============================================================== readers

HDRS = "FROM TO CC SUBJECT DATE LIST-UNSUBSCRIBE LIST-ID PRECEDENCE CONTENT-TYPE"


def find_all_mail(imap):
    typ, data = imap.list()
    for raw in data or []:
        line = raw.decode(errors="replace")
        if "\\All" in line:
            m = re.search(r'"([^"]+)"\s*$', line) or re.search(r"(\S+)\s*$", line)
            return m.group(1)
    return "INBOX"


def parse_fetch(resp):
    """Yield (uid, flags_bytes, labels_bytes, header_bytes) from a FETCH reply."""
    for i, part in enumerate(resp):
        if not isinstance(part, tuple):
            continue
        meta = part[0]
        if i + 1 < len(resp) and isinstance(resp[i + 1], bytes):
            meta += resp[i + 1]
        m = re.search(rb"UID (\d+)", meta)
        if not m:
            continue
        f = re.search(rb"FLAGS \(([^)]*)\)", meta)
        l = re.search(rb"X-GM-LABELS \((.*?)\)(?: [A-Z]|\)|$)", meta)
        yield int(m.group(1)), (f.group(1) if f else b""), (l.group(1) if l else b""), part[1]


def gm_search(imap, query):
    typ, data = imap.uid("SEARCH", "X-GM-RAW", f'"{query}"')
    if typ != "OK" or not data or not data[0]:
        return set()
    return {int(x) for x in data[0].split()}


def read_imap(db, user, batch=500):
    pw = getpass.getpass(f"App password for {user} (input hidden): ")
    print("Connecting to imap.gmail.com ...")
    imap = imaplib.IMAP4_SSL("imap.gmail.com")
    imap.login(user, pw.replace(" ", ""))
    folder = find_all_mail(imap)
    imap.select(f'"{folder}"', readonly=True)  # read-only: nothing is changed
    uv = (imap.response("UIDVALIDITY")[1] or [b"?"])[0]
    uv = uv.decode() if isinstance(uv, bytes) else str(uv)
    old = db.execute("SELECT v FROM meta WHERE k='uidvalidity'").fetchone()
    if old and old[0] != uv:
        print("Mailbox changed since last run, rebuilding cache")
        db.execute("DELETE FROM msgs WHERE src='imap'")
    db.execute("INSERT OR REPLACE INTO meta VALUES('uidvalidity', ?)", (uv,))

    typ, data = imap.uid("SEARCH", "ALL")
    all_uids = [int(x) for x in data[0].split()]
    have = {r[0] for r in db.execute("SELECT uid FROM msgs WHERE src='imap'")}
    todo = [u for u in all_uids if u not in have]
    print(f"Folder {folder}: {len(all_uids)} messages, {len(todo)} new to read")

    for i in range(0, len(todo), batch):
        chunk = ",".join(map(str, todo[i:i + batch]))
        typ, resp = imap.uid("FETCH", chunk,
                             f"(UID FLAGS X-GM-LABELS BODY.PEEK[HEADER.FIELDS ({HDRS})])")
        for uid, flags, labels, hdr in parse_fetch(resp or []):
            msg = email.message_from_bytes(hdr, policy=email.policy.compat32)
            rec = record_from_headers(msg)
            insert(db, "imap", uid, rec,
                   seen=b"\\Seen" in flags, flagged=b"\\Flagged" in flags,
                   important=b"\\Important" in labels, sent=b"\\Sent" in labels,
                   category="")
        db.commit()
        done = min(i + batch, len(todo))
        print(f"\r  headers {done}/{len(todo)} ({done * 100 // max(len(todo), 1)}%)",
              end="", flush=True)
    print()

    # Gmail's own categories + attachments + refresh read/starred state for all
    print("Reading Gmail categories, attachments, read/starred state ...")
    db.execute("UPDATE msgs SET category='primary' WHERE src='imap'")
    for cat in ("promotions", "social", "updates", "forums"):
        uids = gm_search(imap, f"category:{cat}")
        db.executemany("UPDATE msgs SET category=? WHERE src='imap' AND uid=?",
                       [(cat, u) for u in uids])
        print(f"  {cat:11} {len(uids)}")
    for col, q in (("attach", "has:attachment"), ("seen", "is:read"),
                   ("flagged", "is:starred"), ("important", "is:important")):
        uids = gm_search(imap, q)
        if uids:
            db.execute(f"UPDATE msgs SET {col}=0 WHERE src='imap'")
            db.executemany(f"UPDATE msgs SET {col}=1 WHERE src='imap' AND uid=?",
                           [(u,) for u in uids])
    db.commit()
    imap.logout()


def read_mbox(db, path):
    print(f"Reading {path} ... (big Takeout files take a few minutes)")
    have = db.execute("SELECT COUNT(*) FROM msgs WHERE src='mbox'").fetchone()[0]
    if have:
        print(f"  cache already has {have} mbox messages, re-reading anyway")
        db.execute("DELETE FROM msgs WHERE src='mbox'")
    n = 0
    for msg in mailbox.mbox(path, create=False):
        labels = {x.strip().lower() for x in decode(msg.get("X-Gmail-Labels")).split(",")}
        cat = ""
        for c in ("promotions", "social", "updates", "forums"):
            if f"category {c}" in labels:
                cat = c
        if not cat and labels:
            cat = "primary"
        rec = record_from_headers(msg)
        insert(db, "mbox", n, rec,
               seen="unread" not in labels, flagged="starred" in labels,
               important="important" in labels, sent="sent" in labels, category=cat)
        n += 1
        if n % 2000 == 0:
            db.commit()
            print(f"\r  {n} messages", end="", flush=True)
    db.commit()
    print(f"\r  {n} messages")


def read_passwords(path):
    sites = defaultdict(set)
    with open(path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            url = row.get("url") or row.get("URL") or row.get("origin") or ""
            m = re.match(r"^(?:[a-z][a-z0-9+.-]*://)?([^/:?#]+)", url.strip(), re.I)
            if m and not url.startswith("android"):
                sites[base_domain(m.group(1))].add(row.get("username") or "")
    print(f"Saved-password sites: {len(sites)} (passwords ignored)")
    return sites


# =============================================================== analysis


def new_svc():
    return {"n": 0, "years": Counter(), "months": set(), "bill_months": set(),
            "dates": [], "seen": 0, "flagged": 0, "important": 0, "cat": Counter(),
            "unsub": 0, "unsub_link": "", "unsub_date": None, "bulk": 0,
            "attach": 0, "sig": Counter(), "examples": {}, "senders": Counter(),
            "names": Counter(), "noreply": 0, "you_sent": 0, "last12": 0}


def analyze(db, own, pw_sites):
    own = {a.lower() for a in own}
    svc = defaultdict(new_svc)
    ppl = defaultdict(lambda: {"recv": 0, "sent": 0, "name": "", "first": None,
                               "last": None, "unread": 0})
    per_year = Counter()
    per_year_kind = defaultdict(Counter)
    total = 0
    one_year_ago = TODAY - dt.timedelta(days=365)

    rows = db.execute("SELECT date, from_addr, from_name, to_addrs, subject, unsub, "
                      "bulk, attach, seen, flagged, important, sent, category FROM msgs")
    for (date, frm, name, to, subj, unsub, bulk, attach, seen, flagged,
         important, sent, cat) in rows:
        total += 1
        d = dt.date.fromisoformat(date) if date else None
        if d:
            per_year[d.year] += 1

        # ---- mail YOU sent: engagement signal for people and services
        if sent or frm in own:
            for a in filter(None, (to or "").split(",")):
                if a in own:
                    continue
                p = ppl[a]
                p["sent"] += 1
                if d:
                    p["first"] = min(filter(None, [p["first"], d]))
                    p["last"] = max(filter(None, [p["last"], d]))
                dom = a.split("@")[-1]
                if dom not in FREEMAIL:
                    svc[base_domain(dom)]["you_sent"] += 1
            if d:
                per_year_kind[d.year]["sent by you"] += 1
            continue
        if "@" not in frm:
            continue

        dom = frm.split("@", 1)[1]
        p = ppl[frm]
        p["recv"] += 1
        p["unread"] += 0 if seen else 1
        p["name"] = p["name"] or name
        if d:
            p["first"] = min(filter(None, [p["first"], d]))
            p["last"] = max(filter(None, [p["last"], d]))
        if dom in FREEMAIL:
            if d:
                per_year_kind[d.year]["people"] += 1
            continue

        s = svc[base_domain(dom)]
        s["n"] += 1
        s["senders"][frm] += 1
        if name:
            s["names"][name] += 1
        if NOREPLY_RE.search(frm):
            s["noreply"] += 1
        s["seen"] += seen
        s["flagged"] += flagged
        s["important"] += important
        s["attach"] += attach
        s["bulk"] += bulk
        s["cat"][cat or "?"] += 1
        if unsub:
            s["unsub"] += 1
            if not s["unsub_date"] or (d and d >= s["unsub_date"]):
                s["unsub_link"], s["unsub_date"] = unsub, d
        sig = signals_of(subj)
        for k in sig:
            s["sig"][k] += 1
            s["examples"].setdefault(k, subj[:90])
        if d:
            s["dates"].append(d)
            s["years"][d.year] += 1
            s["months"].add((d.year, d.month))
            if sig & {"billing", "txn"}:
                s["bill_months"].add((d.year, d.month))
            if d >= one_year_ago:
                s["last12"] += 1
            kind = "promotions" if cat == "promotions" else (
                "newsletters" if unsub else "services")
            per_year_kind[d.year][kind] += 1

    for dom in pw_sites:
        svc[dom]  # make sure saved-password sites appear even without mail

    services = [score_service(dom, s, dom in pw_sites, pw_sites.get(dom, set()))
                for dom, s in svc.items() if s["n"] or dom in pw_sites or s["you_sent"]]
    services.sort(key=lambda r: (-r["Score"], -r["Mails"]))
    for i, r in enumerate(services, 1):
        r["Rank"] = i

    people = []
    for addr, p in ppl.items():
        dom = addr.split("@")[-1]
        if p["sent"] == 0 and dom not in FREEMAIL:
            continue  # company senders live in Services
        if NOREPLY_RE.search(addr) and p["sent"] == 0:
            continue
        two_way = p["sent"] > 0 and p["recv"] > 0
        weight = p["sent"] * 3 + p["recv"] + (10 if two_way else 0)
        recent = p["last"] and (TODAY - p["last"]).days < 730
        people.append({
            "Contact": addr, "Name": p["name"], "You sent": p["sent"],
            "They sent": p["recv"], "Two-way": "yes" if two_way else "",
            "First": p["first"], "Last": p["last"],
            "Days since": (TODAY - p["last"]).days if p["last"] else None,
            "Tell new address?": "YES" if two_way and recent else (
                "maybe" if (two_way or p["sent"] >= 2) else "no"),
            "_w": weight + (20 if recent else 0),
        })
    people.sort(key=lambda r: -r["_w"])

    return {"services": services, "people": people, "total": total,
            "per_year": per_year, "per_year_kind": per_year_kind}


def score_service(dom, s, in_pw, pw_users):
    n = s["n"]
    names = [k for k, _ in s["names"].most_common(3)]
    cat, cat_pts = categorize(dom, names)
    dates = sorted(s["dates"])
    first, last = (dates[0], dates[-1]) if dates else (None, None)
    days_since = (TODAY - last).days if last else None
    span_years = ((last - first).days / 365.25) if dates else 0
    active_years = len(s["years"])
    rate = lambda x: (x / n) if n else 0.0
    read_r, imp_r = rate(s["seen"]), rate(s["important"])
    news_r = rate(max(s["unsub"], s["bulk"]))
    promo_r, upd_r = rate(s["cat"]["promotions"]), rate(s["cat"]["updates"])
    soc_r = rate(s["cat"]["social"])
    mkt_r = rate(s["sig"]["marketing"])

    # regularity: coefficient of variation of gaps between mails (low = like clockwork)
    cv = None
    if len(dates) >= 4:
        gaps = [(b - a).days for a, b in zip(dates, dates[1:])]
        mu = statistics.mean(gaps)
        if mu > 0:
            cv = statistics.pstdev(gaps) / mu
    bill_months = len(s["bill_months"])

    has_account = bool(s["sig"]["security"] or s["sig"]["welcome"] or
                       s["sig"]["txn"] or s["sig"]["billing"] or in_pw or s["you_sent"])

    # ------------------------------------------------------------ score
    pts = {}
    pts["category"] = cat_pts
    pts["security mails"] = 15 * lg(s["sig"]["security"], 5)
    pts["welcome/verify"] = 6 * lg(s["sig"]["welcome"], 3)
    pts["orders/invoices"] = 10 * lg(s["sig"]["txn"], 20)
    pts["billing"] = 10 * lg(s["sig"]["billing"], 12)
    pts["recurring bills"] = 10 if bill_months >= 6 and days_since is not None \
        and days_since < 400 else (4 if bill_months >= 3 else 0)
    pts["clockwork"] = 5 if cv is not None and cv < 0.6 and n >= 6 else 0
    pts["saved password"] = 10 if in_pw else 0
    pts["you emailed them"] = 15 * lg(s["you_sent"], 10)
    pts["you read it"] = 8 * read_r if n >= 3 else 0
    pts["Gmail important"] = 10 * imp_r
    pts["starred"] = 6 * lg(s["flagged"], 3)
    pts["attachments"] = 4 * lg(s["attach"], 10)
    if days_since is None:
        pts["recency"] = 0
    elif days_since < 365:
        pts["recency"] = 10
    elif days_since < 3 * 365:
        pts["recency"] = 4
    elif days_since > 5 * 365:
        pts["recency"] = -6
    else:
        pts["recency"] = 0
    pts["newsletter"] = -18 * news_r
    pts["promotions tab"] = -15 * promo_r
    pts["marketing subjects"] = -10 * mkt_r
    pts["ignored (unread)"] = -12 * (1 - read_r) if n >= 20 else 0
    pts["spammy volume"] = -6 if s["last12"] > 150 and news_r > 0.5 else 0
    score = max(0, min(100, round(sum(pts.values()))))

    if score >= 65:
        tier = "1 Critical"
    elif score >= 45:
        tier = "2 Important"
    elif score >= 28:
        tier = "3 Account"
    elif score >= 12:
        tier = "4 Low"
    else:
        tier = "5 Trash"

    dormant = days_since is not None and days_since > 3 * 365
    if tier == "1 Critical":
        action = "Change email FIRST + check 2FA/recovery"
    elif has_account and dormant:
        action = "Dormant: delete account (or ignore)"
    elif has_account and tier in ("2 Important", "3 Account"):
        action = "Change email"
    elif news_r > 0.5 or promo_r > 0.5:
        action = "Unsubscribe"
    elif has_account:
        action = "Change email (low prio) or delete account"
    else:
        action = "Ignore"

    top_pts = sorted(((k, v) for k, v in pts.items() if abs(v) >= 3),
                     key=lambda kv: -abs(kv[1]))[:4]
    why = ", ".join(f"{k} {v:+.0f}" for k, v in top_pts)
    example = (s["examples"].get("security") or s["examples"].get("billing") or
               s["examples"].get("txn") or s["examples"].get("welcome") or
               s["examples"].get("marketing") or "")

    return {
        "Rank": 0, "Tier": tier, "Score": score, "Action": action, "Service": dom,
        "Category": cat, "Sender name": "; ".join(names)[:80], "Mails": n,
        "Last 12m": s["last12"], "Per year": round(n / max(span_years, 1), 1),
        "First": first, "Last": last, "Days since": days_since,
        "Active years": active_years, "Read %": read_r, "Important %": imp_r,
        "Starred": s["flagged"], "You sent": s["you_sent"],
        "Security": s["sig"]["security"], "Welcome": s["sig"]["welcome"],
        "Orders/inv": s["sig"]["txn"], "Billing": s["sig"]["billing"],
        "Bill months": bill_months, "Marketing": s["sig"]["marketing"],
        "Newsletter %": news_r, "Promo %": promo_r, "Updates %": upd_r,
        "Social %": soc_r, "Attachments": s["attach"],
        "Regularity": round(cv, 2) if cv is not None else None,
        "Senders": len(s["senders"]), "Saved pw": "yes" if in_pw else "",
        "Login": "; ".join(sorted(u for u in pw_users if u))[:60],
        "Why": why, "Example subject": example,
        "Gmail": "https://mail.google.com/mail/u/0/#search/" +
                 urllib.parse.quote(f"from:{dom}"),
        "Unsubscribe link": s["unsub_link"],
        "Status": "", "_years": s["years"],
        "_has_account": has_account, "_dormant": dormant,
    }


# =============================================================== output

TIER_FILL = {"1 Critical": "FFC7CE", "2 Important": "FFE0B2", "3 Account": "FFF2CC",
             "4 Low": "DDEBF7", "5 Trash": "EDEDED"}
PCT_COLS = {"Read %", "Important %", "Newsletter %", "Promo %", "Updates %", "Social %"}
WIDTH = {"Service": 26, "Action": 34, "Sender name": 24, "Why": 46,
         "Example subject": 44, "Gmail": 10, "Unsubscribe link": 14, "Contact": 32,
         "Name": 24, "Tell new address?": 16, "Tier": 11, "Category": 15,
         "Status": 12, "Login": 22}


def visible(row):
    return {k: v for k, v in row.items() if not k.startswith("_")}


def write_sheet(wb, title, rows, cols=None, tier_col=True):
    ws = wb.create_sheet(title)
    if not rows:
        ws.append(["(nothing here)"])
        return ws
    cols = cols or list(visible(rows[0]).keys())
    ws.append(cols)
    for r in rows:
        ws.append([r.get(c) for c in cols])
    hdr_fill = PatternFill("solid", fgColor="1F3864")
    for c in ws[1]:
        c.font = Font(bold=True, color="FFFFFF")
        c.fill = hdr_fill
        c.alignment = Alignment(wrap_text=True, vertical="center")
    ws.row_dimensions[1].height = 32
    for i, name in enumerate(cols, 1):
        L = get_column_letter(i)
        ws.column_dimensions[L].width = WIDTH.get(name, 10)
        if name in PCT_COLS:
            for cell in ws[L][1:]:
                cell.number_format = "0%"
        if name in ("First", "Last"):
            for cell in ws[L][1:]:
                cell.number_format = "yyyy-mm-dd"
        if name in ("Gmail", "Unsubscribe link"):
            for cell in ws[L][1:]:
                if cell.value:
                    cell.hyperlink = cell.value
                    cell.value = "open" if name == "Gmail" else "unsubscribe"
                    cell.font = Font(color="0563C1", underline="single")
        if name == "Score":
            ws.conditional_formatting.add(
                f"{L}2:{L}{ws.max_row}",
                DataBarRule(start_type="num", start_value=0, end_type="num",
                            end_value=100, color="5B9BD5"))
        if name in ("Read %", "Newsletter %", "Promo %"):
            good, bad = ("F8696B", "63BE7B") if name == "Read %" else ("63BE7B", "F8696B")
            ws.conditional_formatting.add(
                f"{L}2:{L}{ws.max_row}",
                ColorScaleRule(start_type="num", start_value=0, start_color=good,
                               end_type="num", end_value=1, end_color=bad))
    if tier_col and "Tier" in cols:
        ti = cols.index("Tier") + 1
        for row in ws.iter_rows(min_row=2):
            fill = TIER_FILL.get(row[ti - 1].value)
            if fill:
                row[ti - 1].fill = PatternFill("solid", fgColor=fill)
    freeze = cols.index("Service") + 2 if "Service" in cols else 2
    ws.freeze_panes = ws.cell(row=2, column=min(freeze, len(cols)))
    ws.auto_filter.ref = ws.dimensions
    return ws


def write_xlsx(res, out):
    wb = openpyxl.Workbook()
    sm = wb.active
    sm.title = "Summary"
    S = res["services"]
    big = Font(bold=True, size=14)
    sm["A1"] = "Gmail triage report"
    sm["A1"].font = Font(bold=True, size=18)
    sm["A2"] = f"Generated {TODAY.isoformat()} - pure heuristics, headers only"
    stats = [
        ("Total messages scanned", res["total"]),
        ("Sender domains (services)", len(S)),
        ("Services where you likely have an account",
         sum(1 for r in S if r["_has_account"])),
        ("Dormant accounts (3+ yrs quiet)",
         sum(1 for r in S if r["_has_account"] and r["_dormant"])),
        ("Unsubscribe candidates", sum(1 for r in S if r["Action"] == "Unsubscribe")),
        ("People to tell your new address",
         sum(1 for p in res["people"] if p["Tell new address?"] == "YES")),
    ]
    row = 4
    for k, v in stats:
        sm.cell(row=row, column=1, value=k)
        sm.cell(row=row, column=2, value=v).font = Font(bold=True)
        row += 1

    row += 1
    sm.cell(row=row, column=1, value="Tiers").font = big
    row += 1
    sm.append(["Tier", "Services", "Mails", "Meaning"])
    meaning = {"1 Critical": "money, government, identity: do these first",
               "2 Important": "real accounts you use",
               "3 Account": "accounts worth moving when you have time",
               "4 Low": "rarely matters - move, delete or ignore",
               "5 Trash": "newsletters, promos, noise"}
    for t in TIER_FILL:
        rs = [r for r in S if r["Tier"] == t]
        sm.append([t, len(rs), sum(r["Mails"] for r in rs), meaning[t]])
        sm.cell(row=sm.max_row, column=1).fill = PatternFill("solid", fgColor=TIER_FILL[t])

    sm.append([])
    sm.append(["Mail per year"])
    sm.cell(row=sm.max_row, column=1).font = big
    kinds = ["services", "newsletters", "promotions", "people", "sent by you"]
    sm.append(["Year"] + kinds + ["Total"])
    y0 = sm.max_row
    for y in sorted(res["per_year"]):
        k = res["per_year_kind"][y]
        sm.append([str(y)] + [k[x] for x in kinds] + [res["per_year"][y]])
    y1 = sm.max_row
    if y1 > y0:
        ch = BarChart()
        ch.type, ch.grouping, ch.overlap = "col", "stacked", 100
        ch.title, ch.height, ch.width = "Mail per year by kind", 9, 22
        ch.add_data(Reference(sm, min_col=2, max_col=1 + len(kinds),
                              min_row=y0, max_row=y1), titles_from_data=True)
        ch.set_categories(Reference(sm, min_col=1, min_row=y0 + 1, max_row=y1))
        sm.add_chart(ch, "H4")

    sm.append([])
    sm.append(["Categories"])
    sm.cell(row=sm.max_row, column=1).font = big
    sm.append(["Category", "Services", "Mails"])
    cats = Counter()
    catm = Counter()
    for r in S:
        cats[r["Category"] or "(other)"] += 1
        catm[r["Category"] or "(other)"] += r["Mails"]
    for c, n in cats.most_common():
        sm.append([c, n, catm[c]])
    sm.column_dimensions["A"].width = 44
    sm.column_dimensions["D"].width = 44

    write_sheet(wb, "Services", [visible(r) for r in S])

    work = [r for r in S if r["_has_account"] and not r["_dormant"]
            and r["Tier"] != "5 Trash"]
    write_sheet(wb, "Change email", work, cols=[
        "Rank", "Tier", "Score", "Action", "Service", "Category", "Sender name",
        "Mails", "Last", "Security", "Billing", "Saved pw", "Login", "Why",
        "Gmail", "Status"])

    uns = sorted([r for r in S if r["Action"] == "Unsubscribe" or
                  (r["Newsletter %"] > 0.5 and r["Unsubscribe link"])],
                 key=lambda r: -r["Last 12m"])
    write_sheet(wb, "Unsubscribe", uns, cols=[
        "Service", "Sender name", "Last 12m", "Mails", "Read %", "Newsletter %",
        "Promo %", "Last", "Unsubscribe link", "Gmail", "Status"], tier_col=False)

    dor = sorted([r for r in S if r["_has_account"] and r["_dormant"]],
                 key=lambda r: -r["Score"])
    write_sheet(wb, "Dormant", dor, cols=[
        "Tier", "Score", "Service", "Category", "Sender name", "Mails", "First",
        "Last", "Security", "Orders/inv", "Saved pw", "Example subject", "Gmail",
        "Status"])

    write_sheet(wb, "People", [visible(p) for p in res["people"]], tier_col=False)

    # timeline heat-map
    top = S[:200]
    years = sorted({y for r in top for y in r["_years"]})
    tl = wb.create_sheet("Timeline")
    tl.append(["Tier", "Service"] + [str(y) for y in years])
    for r in top:
        tl.append([r["Tier"], r["Service"]] + [r["_years"].get(y) or None for y in years])
    for c in tl[1]:
        c.font = Font(bold=True)
    tl.column_dimensions["B"].width = 28
    if years:
        rng = f"C2:{get_column_letter(2 + len(years))}{tl.max_row}"
        tl.conditional_formatting.add(rng, ColorScaleRule(
            start_type="min", start_color="FFFFFF", mid_type="percentile",
            mid_value=50, mid_color="9BC2E6", end_type="max", end_color="1F4E78"))
    for row in tl.iter_rows(min_row=2):
        f = TIER_FILL.get(row[0].value)
        if f:
            row[0].fill = PatternFill("solid", fgColor=f)
    tl.freeze_panes = "C2"

    hs = wb.create_sheet("How scored")
    rules = [
        ("Score 0-100 = sum of these points (see 'Why' column per service)", ""),
        ("Category (Government/Finance 40, Crypto/Insurance 35, Utilities/Tech 30, "
         "Housing 25, Work 20, Travel 12, Shopping 6)", "+0..40"),
        ("Security mails (password, 2FA, new login, codes)", "+0..15"),
        ("Welcome / verify / account created", "+0..6"),
        ("Orders, invoices, receipts, deliveries", "+0..10"),
        ("Billing / subscription / statements / premie", "+0..10"),
        ("Recurring bills: 6+ months with a bill, still active", "+10 (3+ months: +4)"),
        ("Clockwork: regular intervals (gap CV < 0.6)", "+5"),
        ("Site is in your saved passwords", "+10"),
        ("You emailed them", "+0..15"),
        ("You actually read their mail (read %)", "+0..8"),
        ("Gmail marked it Important", "+0..10"),
        ("You starred some", "+0..6"),
        ("Has attachments (PDF invoices etc.)", "+0..4"),
        ("Recency: <1y +10, <3y +4, >5y -6", "-6..+10"),
        ("Newsletter share (List-Unsubscribe / bulk)", "-0..18"),
        ("Promotions tab share", "-0..15"),
        ("Marketing words in subjects", "-0..10"),
        ("Mostly unread (20+ mails)", "-0..12"),
        ("Very high volume newsletter", "-6"),
        ("", ""),
        ("Tiers: 65+ Critical, 45+ Important, 28+ Account, 12+ Low, else Trash", ""),
        ("Tune: edit CATEGORIES / SIGNALS / score_service() and run with "
         "--report-only (no re-download)", ""),
    ]
    for r in rules:
        hs.append(list(r))
    hs.column_dimensions["A"].width = 90
    hs.column_dimensions["B"].width = 22
    hs["A1"].font = Font(bold=True)

    wb.save(out)


def write_csvs(res, out):
    base = re.sub(r"\.xlsx$", "", out)
    for name, rows in (("services", res["services"]), ("people", res["people"])):
        rows = [visible(r) for r in rows]
        path = f"{base}_{name}.csv"
        with open(path, "w", newline="", encoding="utf-8") as f:
            if rows:
                w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
                w.writeheader()
                w.writerows(rows)
        print(f"Wrote {path}")


# =============================================================== main


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="mode", required=True)
    pi = sub.add_parser("imap", help="read headers straight from Gmail")
    pi.add_argument("--user", required=True, help="your Gmail address")
    pm = sub.add_parser("mbox", help="read a Google Takeout .mbox file")
    pm.add_argument("path")
    for p in (pi, pm):
        p.add_argument("--me", action="append", default=[],
                       help="your own address/alias (repeatable)")
        p.add_argument("--passwords", help="Google Password Manager CSV export")
        p.add_argument("--out", default="mail_triage.xlsx")
        p.add_argument("--cache", default="mail_cache.sqlite")
        p.add_argument("--report-only", action="store_true",
                       help="don't read mail, rebuild report from cache")
    a = ap.parse_args()

    own = list(a.me) + ([a.user] if a.mode == "imap" else [])
    db = open_cache(a.cache)
    if not a.report_only:
        try:
            if a.mode == "imap":
                read_imap(db, a.user)
            else:
                read_mbox(db, a.path)
        except imaplib.IMAP4.error as e:
            sys.exit(f"Gmail login/IMAP error: {e}\nUse an app password "
                     "(myaccount.google.com/apppasswords), not your normal password.")

    if not own:
        # guess own address: the most common sender of mail labelled Sent
        r = db.execute("SELECT from_addr, COUNT(*) c FROM msgs WHERE sent=1 "
                       "GROUP BY from_addr ORDER BY c DESC LIMIT 1").fetchone()
        if r:
            own = [r[0]]
            print(f"Assuming your address is {r[0]} (use --me to set)")

    pw = read_passwords(a.passwords) if a.passwords else {}
    print("Scoring ...")
    res = analyze(db, own, pw)

    if openpyxl:
        write_xlsx(res, a.out)
        print(f"Wrote {a.out}")
    else:
        print("openpyxl not installed (pip install openpyxl) - writing CSV instead")
        write_csvs(res, a.out)

    tiers = Counter(r["Tier"] for r in res["services"])
    print(f"\n{res['total']} messages, {len(res['services'])} services")
    for t in TIER_FILL:
        print(f"  {t:12} {tiers[t]}")
    print("\nTop 15 to move first:")
    for r in res["services"][:15]:
        print(f"  {r['Score']:>3}  {r['Service']:28} {r['Mails']:>6} mails  {r['Why'][:60]}")


if __name__ == "__main__":
    main()
