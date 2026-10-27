import re, unicodedata

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
])

STOP_WORDS = set([
    'que', 'como', 'para', 'por', 'con', 'una', 'uno', 'unos', 'unas', 'del', 'las', 'los', 'este', 'esta', 'esto', 'ese', 'esa',
    'desde', 'hasta', 'sobre', 'entre', 'sin', 'hay', 'fue', 'son', 'ser', 'sea', 'han', 'mas', 'muy', 'pero', 'sus', 'segun', 'tambien',
    'hoy', 'ahora', 'actualmente', 'pasado', 'noticias', 'reciente', 'ayer', 'the', 'and', 'for', 'with', 'from', 'this', 'that', 'are', 'was', 'were', 'has', 'have',
])

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

for q in [
    'busca en internet quien es el presidente de nicaragua',
    'quien fue ruben dario',
    'como esta el clima hoy',
    'que paso con la seleccion',
    'noticias de hoy',
    'quien gano el partido de futbol ayer',
    'cual es la capital de francia'
]:
    subj = subject_query(q)
    print(f'Q: {q}\n  -> Subj: "{subj}"')
