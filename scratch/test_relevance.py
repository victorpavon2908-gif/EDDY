import unicodedata, re

def normalize(val):
    s = unicodedata.normalize('NFD', val.lower())
    s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    s = re.sub(r'[^a-z0-9 ]+', ' ', s)
    return re.sub(r'\s+', ' ', s).strip()

QUERY_FILLERS = set([
    'profundidad', 'profundo', 'detallado', 'detallada', 'detalladamente', 'exhaustivo', 'exhaustiva', 'fondo',
    'dame', 'explicame', 'ensename', 'ayudame', 'necesito', 'ayuda', 'acerca', 'tema', 'tal', 'cosa', 'un', 'una', 'en', 'al',
    'leo', 'que', 'ha', 'han', 'pasado', 'pasa', 'paso', 'esta', 'ahora', 'mismo',
    'ultimas', 'ultima', 'noticias', 'dime', 'decime', 'cuentame', 'contame', 'hablame', 'sobre', 'del', 'de', 'el',
    'la', 'los', 'las', 'con', 'por', 'favor', 'quiero', 'saber', 'busca', 'buscame', 'internet',
    'quien', 'quienes', 'cual', 'cuales', 'donde', 'cuando', 'fue', 'es', 'era'
])

STOP_WORDS = set([
    'que', 'como', 'para', 'por', 'con', 'una', 'uno', 'unos', 'unas', 'del', 'las', 'los', 'este', 'esta', 'esto', 'ese', 'esa',
    'desde', 'hasta', 'sobre', 'entre', 'sin', 'hay', 'fue', 'son', 'ser', 'sea', 'han', 'mas', 'muy', 'pero', 'sus', 'segun', 'tambien',
    'hoy', 'ahora', 'actualmente', 'pasado', 'noticias', 'reciente', 'ayer', 'the', 'and', 'for', 'with', 'from', 'this', 'that', 'are', 'was', 'were', 'has', 'have',
])

def meaningful_tokens(val):
    return set(t for t in normalize(val).split() if len(t) >= 2 and t not in STOP_WORDS and t not in QUERY_FILLERS)

def subject_query(query):
    text = normalize(query)
    text = re.sub(r'^(?:(?:oye|hey|hola)\s+)?leo\b[ ,:]*', '', text)
    text = re.sub(r'^por favor[ ,]+', '', text)
    m = re.search(r'^(?:(?:podes|puedes|podrias|podria|quiero que|necesito que)\s+)?(?:buscar|busca|buscame|busques|buscam|investiga|investigame|investigar|investigues|averigua|averiguame|averiguar|consulta|consultame|consultar|googlea)(?:\s+(?:en|por)\s+(?:internet|google|la web))?\s+(.+)$', text)
    requested = m.group(1) if m else query
    norm = normalize(requested)
    norm = re.sub(r'^(?:informacion(?:es)?|datos|algo)\s+(?:(?:sobre|acerca de|de|por)\s+)?', '', norm)
    tokens = [t for t in norm.split() if len(t) >= 2 and t not in QUERY_FILLERS]
    return ' '.join(tokens)

def relevance_score(title, snippet, terms):
    if not terms:
        return 0.0
    t_tokens = meaningful_tokens(title)
    b_tokens = meaningful_tokens(f"{title} {snippet}")
    t_overlap = len(t_tokens.intersection(terms))
    b_overlap = len(b_tokens.intersection(terms))
    coverage = b_overlap / len(terms)
    
    if b_overlap == 0 or (len(terms) == 2 and b_overlap < 2) or (len(terms) > 2 and b_overlap < 2 and t_overlap == 0):
        return 0.0
    return t_overlap * 2.5 + b_overlap * 1.5 + coverage * 2.0

# Test 1: Ruben Dario vs Ruben Martinez
terms1 = meaningful_tokens("Ruben Dario")
print("Ruben Dario terms:", terms1)
score_martinez = relevance_score("Informacion sobre Ruben Martinez", "Detalles", terms1)
print("Ruben Martinez score (should be 0.0):", score_martinez)
score_dario = relevance_score("Ruben Dario - Biografia", "Vida y obras de Ruben Dario", terms1)
print("Ruben Dario score (should be > 0):", score_dario)

# Test 2: Bitcoin
subj_btc = subject_query("Que ha pasado hoy con el Bitcoin?")
terms_btc = meaningful_tokens(subj_btc)
print("BTC terms:", terms_btc)
score_grammar = relevance_score("Cuando se usa ha, a o ah en espanol y por que", "Diferencias", terms_btc)
print("Grammar score (should be 0.0):", score_grammar)
score_btc = relevance_score("Bitcoin cae tras nuevas ventas", "Bitcoin y BTC retrocedieron hoy", terms_btc)
print("Bitcoin news score (should be > 0):", score_btc)
