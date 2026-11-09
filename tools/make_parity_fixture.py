#!/usr/bin/env python3
"""Build the scoring parity fixture from mail_triage.py.

Generates deterministic synthetic header rows, runs the prototype's analyze() with a fixed
TODAY, and writes inputs + expected output to the given path (default:
app/build/generated/parity/parity/fixture.json). Gradle runs this before unit tests, and the
Kotlin ScoringParityTest feeds the same rows to Analyzer and compares.

    python3 -I tools/make_parity_fixture.py [OUT]
"""
import datetime as dt
import json
import pathlib
import random
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.dont_write_bytecode = True
import mail_triage as mt  # noqa: E402

TODAY = dt.date(2026, 11, 9)
mt.TODAY = TODAY
ME = "me.old@gmail.com"

rng = random.Random(42)

# (sender address, display name, subject pool, count, years back range, flags)
SUBJ = {
    "security": ["Your verification code", "New sign-in on your account", "Password reset request",
                 "Beveiligingsmelding: nieuwe aanmelding"],
    "welcome": ["Welcome to the service", "Verify your email", "Bevestig je account"],
    "txn": ["Your order #123 has shipped", "Factuur 2024-001", "Receipt for your payment",
            "Je bestelling is verzonden"],
    "billing": ["Your monthly statement", "Abonnement verlengd", "Your bill is ready", "Jaaroverzicht"],
    "marketing": ["Black Friday: 50% off", "Nieuwsbrief week 12", "Exclusive deal for you",
                  "Last chance: sale ends"],
    "plain": ["Hello", "Update about your thing", "Quick question", "Meeting notes"],
}

SERVICES = [
    # addr, name, mix(dict kind->weight), n, (min_days_ago, max_days_ago), newsletter, promo_share, read_p, imp_p, attach_p
    ("noreply@mail.ing.nl", "ING", {"security": 2, "billing": 5, "txn": 3}, 60, (3, 3600), 0.0, 0.0, 0.9, 0.7, 0.3),
    ("info@belastingdienst.nl", "Belastingdienst", {"billing": 3, "plain": 2}, 18, (20, 4000), 0.0, 0.0, 1.0, 0.9, 0.5),
    ("no-reply@accounts.google-like.co.uk", "Example UK", {"security": 3, "welcome": 1}, 9, (100, 900), 0.0, 0.0, 0.7, 0.2, 0.0),
    ("news@bol.com", "bol.com", {"marketing": 6, "txn": 2}, 220, (1, 1500), 0.9, 0.8, 0.2, 0.0, 0.0),
    ("orders@coolblue.nl", "Coolblue", {"txn": 5, "marketing": 1}, 25, (30, 2500), 0.2, 0.1, 0.8, 0.1, 0.2),
    ("newsletter@shopdeal.com", "ShopDeal", {"marketing": 9}, 400, (0, 700), 1.0, 1.0, 0.05, 0.0, 0.0),
    ("support@github.com", "GitHub", {"security": 4, "welcome": 1, "plain": 3}, 45, (2, 3000), 0.3, 0.0, 0.6, 0.3, 0.0),
    ("hello@oldforum.org", "Old Forum", {"welcome": 2, "plain": 3}, 12, (1500, 4200), 0.5, 0.0, 0.5, 0.0, 0.0),
    ("billing@ziggo.nl", "Ziggo", {"billing": 8}, 70, (5, 3000), 0.0, 0.0, 0.9, 0.5, 0.6),  # freemail domain: people path
    ("klantenservice@eneco.nl", "Eneco", {"billing": 6, "txn": 1}, 48, (10, 2200), 0.0, 0.0, 0.85, 0.4, 0.5),
    ("noreply@spotify.com", "Spotify", {"billing": 3, "marketing": 3, "security": 1}, 80, (3, 2800), 0.6, 0.4, 0.4, 0.1, 0.0),
    ("team@tinyapp.io", "Tiny App", {"welcome": 1, "plain": 1}, 3, (2000, 2100), 0.0, 0.0, 1.0, 0.0, 0.0),
    ("alerts@verzekeraar-vgz.nl", "VGZ", {"billing": 4, "plain": 2}, 30, (40, 3300), 0.0, 0.0, 0.9, 0.6, 0.4),
    ("deals@travelsite.com", "TravelSite", {"marketing": 5, "txn": 1}, 90, (200, 2000), 0.95, 0.7, 0.1, 0.0, 0.0),
    ("noreply@ns.nl", "NS", {"txn": 4, "plain": 1}, 35, (15, 2600), 0.1, 0.0, 0.7, 0.2, 0.1),
    ("mailer@randomblog.net", "Random Blog", {"plain": 1}, 26, (100, 1200), 0.8, 0.0, 0.1, 0.0, 0.0),
    ("service@paypal.nl", "PayPal", {"txn": 5, "security": 2}, 55, (6, 3500), 0.0, 0.0, 0.8, 0.5, 0.0),
    ("updates@facebookmail.com", "Facebook", {"security": 2, "plain": 4, "marketing": 2}, 150, (900, 4000), 0.5, 0.0, 0.2, 0.0, 0.0),
    ("noreply@amazon.co.uk", "Amazon.co.uk", {"txn": 6, "marketing": 2}, 40, (60, 3000), 0.2, 0.3, 0.6, 0.1, 0.0),
    ("hello@clockwork.example", "Clockwork Co", {"billing": 1}, 24, None, 0.0, 0.0, 1.0, 0.0, 1.0),  # monthly
]

PEOPLE = [
    ("friend.one@gmail.com", "Friend One", 30, 25, (5, 3000)),
    ("old.pal@hotmail.com", "Old Pal", 5, 3, (1200, 3000)),
    ("aunt@ziggo.nl", "Aunt", 0, 12, (10, 2000)),
    ("colleague@somecorp.com", "Colleague", 8, 10, (30, 600)),
    ("noreply@gmail.com", "Noreply Gmail", 0, 5, (10, 100)),
    ("pen.friend@outlook.com", "", 3, 0, (100, 300)),
]


def day(ago):
    return TODAY - dt.timedelta(days=ago)


def pick(mix):
    kinds = list(mix)
    return rng.choices(kinds, weights=[mix[k] for k in kinds])[0]


rows = []
uid = 0


def add(**kw):
    global uid
    uid += 1
    base = dict(src="fx", uid=uid, date=None, from_addr="", from_name="", to_addrs=ME, subject="",
                unsub="", bulk=0, attach=0, seen=0, flagged=0, important=0, sent=0, category="primary")
    base.update(kw)
    rows.append(base)


for addr, name, mix, n, rng_days, news, promo, read_p, imp_p, att_p in SERVICES:
    for i in range(n):
        if rng_days is None:
            ago = 15 + 30 * i  # strict monthly cadence
        else:
            ago = rng.randint(*rng_days)
        kind = pick(mix)
        subj = rng.choice(SUBJ[kind])
        is_news = rng.random() < news
        unsub = f"<mailto:unsub@{addr.split('@')[1]}>, <https://{addr.split('@')[1]}/u/{i}>" if is_news else ""
        add(date=day(ago).isoformat(), from_addr=addr, from_name=name, subject=subj,
            unsub=mt.unsub_link(unsub) if unsub else "", bulk=int(is_news and rng.random() < 0.5),
            attach=int(rng.random() < att_p), seen=int(rng.random() < read_p),
            flagged=int(rng.random() < 0.03), important=int(rng.random() < imp_p),
            category="promotions" if rng.random() < promo else rng.choice(["primary", "updates", "social"]))
    # some with unusable dates
    add(date=None, from_addr=addr, from_name=name, subject="No date here")

# mail you sent to services
for addr in ["support@github.com", "klantenservice@eneco.nl", "info@belastingdienst.nl"]:
    for i in range(rng.randint(1, 6)):
        add(date=day(rng.randint(10, 2000)).isoformat(), from_addr=ME, from_name="Me",
            to_addrs=addr, subject="Re: question", sent=1, seen=1)

for addr, name, you, they, (lo, hi) in PEOPLE:
    for _ in range(they):
        add(date=day(rng.randint(lo, hi)).isoformat(), from_addr=addr, from_name=name,
            subject=rng.choice(SUBJ["plain"]), seen=int(rng.random() < 0.8))
    for _ in range(you):
        add(date=day(rng.randint(lo, hi)).isoformat(), from_addr=ME, from_name="Me", to_addrs=addr,
            subject="Hi", sent=1, seen=1)

# own address recognised without the Sent flag
add(date=day(50).isoformat(), from_addr=ME, from_name="Me", to_addrs="friend.one@gmail.com,cc.person@yahoo.com",
    subject="dinner", sent=0, seen=1)
# a sender without @
add(date=day(10).isoformat(), from_addr="MAILER-DAEMON", from_name="", subject="Undelivered")

db = sqlite3.connect(":memory:")
db.executescript(mt.SCHEMA)
for r in rows:
    db.execute("INSERT INTO msgs VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               (r["src"], r["uid"], r["date"], r["from_addr"], r["from_name"], r["to_addrs"], r["subject"],
                r["unsub"], r["bulk"], r["attach"], r["seen"], r["flagged"], r["important"], r["sent"],
                r["category"]))
res = mt.analyze(db, [ME], {})


def ser(v):
    if isinstance(v, dt.date):
        return v.isoformat()
    return v


services = []
for s in res["services"]:
    services.append({k: ser(v) for k, v in s.items() if not k.startswith("_") or k in ("_has_account", "_dormant")}
                    | {"_years": {str(y): c for y, c in s["_years"].items()}})
people = [{k: ser(v) for k, v in p.items()} for p in res["people"]]

out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/build/generated/parity/parity/fixture.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps({"today": TODAY.isoformat(), "own": [ME], "rows": rows, "total": res["total"],
                           "services": services, "people": people}, separators=(",", ":"), ensure_ascii=False))
print(f"wrote {out}: {len(rows)} rows, {len(services)} services, {len(people)} people")
