package com.staninna.aufmachen.engine

// Keyword lists ported verbatim from mail_triage.py (English and Dutch).

val SIGNALS: Map<String, List<String>> = linkedMapOf(
    "security" to listOf(
        "password", "wachtwoord", "2fa", "two-factor", "tweestaps", "verification code", "verificatiecode",
        "inlogcode", "login code", "security alert", "beveiligingsmelding", "beveiliging", "new sign-in",
        "nieuwe aanmelding", "new login", "new device", "nieuw apparaat", "reset", "one-time", "eenmalige",
        "sign-in attempt", "unusual",
    ),
    "welcome" to listOf(
        "welcome", "welkom", "verify", "verifieer", "confirm your", "bevestig", "activate", "activeer",
        "registration", "registratie", "account created", "account aangemaakt", "sign up", "signup",
        "aanmelding", "your account", "je account", "uw account",
    ),
    "txn" to listOf(
        "order", "bestelling", "receipt", "ontvangstbewijs", "invoice", "factuur", "payment", "betaling",
        "shipped", "verzonden", "bezorg", "delivery", "levering", "booking", "boeking", "reservering",
        "reservation", "ticket", "€", "eur ", "tracking", "track & trace", "pakket", "package", "refund",
        "terugbetaling", "retour",
    ),
    "billing" to listOf(
        "subscription", "abonnement", "renew", "verleng", "incasso", "direct debit", "machtiging", "billing",
        "your bill", "jaaroverzicht", "maandoverzicht", "statement", "afschrift", "premie", "termijn",
        "nota", "rekening", "contract", "polis", "aanslag",
    ),
    "marketing" to listOf(
        "sale", "korting", "% off", "deal", "aanbieding", "newsletter", "nieuwsbrief", "offer", "actie",
        "gratis", "black friday", "cyber monday", "last chance", "laatste kans", "new arrivals",
        "nieuw binnen", "exclusive", "exclusief", "promo", "weekly", "wekelijks", "digest", "don't miss",
        "mis het niet", "save ", "bespaar", "tips", "inspiratie", "inspiration", "webinar",
    ),
)

class CategoryDef(val name: String, val points: Int, val keywords: List<String>)

val CATEGORIES: List<CategoryDef> = listOf(
    CategoryDef("Government", 40, listOf(
        "belastingdienst", "digid", "overheid", "gemeente", "svb.nl", "uwv", "duo.nl", "rdw", "cjib",
        "toeslagen", "mijnoverheid", "kvk", "rijksoverheid", "politie", "rechtspraak", "ind.nl", "cak.nl",
        "waterschap",
    )),
    CategoryDef("Finance", 40, listOf(
        "bank", "ing.nl", "ing.com", "rabobank", "abnamro", "bunq", "knab", "asnbank", "snsbank", "triodos",
        "regiobank", "revolut", "wise.com", "transferwise", "paypal", "klarna", "tikkie", "icscards",
        "americanexpress", "aexp", "mastercard", "visa.", "afterpay", "riverty", "adyen", "mollie",
        "creditcard", "bkr",
    )),
    CategoryDef("Crypto/Investing", 35, listOf(
        "coinbase", "bitvavo", "degiro", "binance", "kraken", "meesman", "brandnewday", "trading212",
        "etoro", "saxo", "flatex", "peaks", "bux", "ledger", "trezor",
    )),
    CategoryDef("Insurance/Health", 35, listOf(
        "verzeker", "insurance", "zilverenkruis", "vgz", "menzis", "cz.nl", "dsw", "ohra", "centraalbeheer",
        "nn.nl", "achmea", "interpolis", "unive", "anwb", "pensioen", "pension", "abp", "pfzw", "huisarts",
        "apotheek", "ziekenhuis", "tandarts", "zorg", "mijngezondheid", "thuisarts",
    )),
    CategoryDef("Utilities/Telecom", 30, listOf(
        "eneco", "vattenfall", "essent", "greenchoice", "budgetenergie", "energie", "vandebron", "frank",
        "vitens", "evides", "waternet", "brabantwater", "ziggo", "kpn", "odido", "t-mobile", "vodafone",
        "tele2", "simyo", "ben.nl", "lebara", "youfone", "delta", "caiway", "stedin", "liander", "enexis",
    )),
    CategoryDef("Identity/Tech", 30, listOf(
        "apple", "icloud", "microsoft", "live.com", "outlook", "amazon", "github", "gitlab", "dropbox",
        "1password", "bitwarden", "lastpass", "proton", "transip", "hostnet", "cloudflare", "namecheap",
        "godaddy", "strato", "mijndomein", "hetzner", "digitalocean", "steampowered", "playstation", "xbox",
        "nintendo", "epicgames", "facebook", "facebookmail", "instagram", "whatsapp", "twitter", "x.com",
        "discord", "reddit", "spotify", "netflix", "disney", "videoland", "openai", "anthropic", "adobe",
        "samsung", "garmin", "fitbit", "tesla",
    )),
    CategoryDef("Housing", 25, listOf(
        "woning", "huur", "verhuur", "vve", "makelaar", "funda", "hypotheek", "kadaster", "woonnet",
        "woningnet", "pararius",
    )),
    CategoryDef("Work/Education", 20, listOf(
        "salaris", "payroll", "loket", "nmbrs", "afas", "linkedin", "indeed", "school", "universiteit",
        "hogeschool", "university", "uva.nl", "tudelft", "studielink", "brightspace", "canvas", "moodle",
    )),
    CategoryDef("Travel/Mobility", 12, listOf(
        "ns.nl", "ov-chipkaart", "ovpay", "klm", "transavia", "booking.com", "airbnb", "uber", "bolt.eu",
        "shell", "parkmobile", "yellowbrick", "easypark", "ryanair", "easyjet", "flixbus", "q-park", "anwb",
    )),
    CategoryDef("Shopping", 6, listOf(
        "bol.com", "coolblue", "zalando", "marktplaats", "aliexpress", "mediamarkt", "ikea", "albert",
        "ah.nl", "jumbo", "hema", "wehkamp", "thuisbezorgd", "picnic", "vinted", "ebay", "etsy", "decathlon",
        "action.com", "temu", "shein", "lidl",
    )),
)

/** Keywords with a dot (not trailing) match whole domains, others match as substrings of domain or text. */
fun kwMatch(kw: String, domain: String, text: String): Boolean {
    if ('.' in kw && !kw.endsWith(".")) return domain == kw || domain.endsWith(".$kw")
    return kw in domain || kw in text
}

/** Returns (category name, base points); ("", 0) when nothing matches. */
fun categorize(domain: String, names: List<String>): Pair<String, Int> {
    val text = names.joinToString(" ").lowercase()
    for (c in CATEGORIES) if (c.keywords.any { kwMatch(it, domain, text) }) return c.name to c.points
    return "" to 0
}

fun signalsOf(subject: String): Set<String> {
    val low = subject.lowercase()
    return SIGNALS.filter { (_, words) -> words.any { it in low } }.keys
}
