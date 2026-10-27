path = 'C:/Users/MARIO LUNA/Desktop/EDDY/app/src/main/java/com/niko/assistant/brain/NikoSemanticActionResolver.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Replace any Unicode replacement char or broken accents in regex
text = text.replace('despu[e\ufffds]s', 'despu[eé]s')
text = text.replace('despu[e\ufffd]s', 'despu[eé]s')
text = text.replace('busc[a\ufffd]', 'busc[aá]')
text = text.replace('naveg[a\ufffd]', 'naveg[aá]')
text = text.replace('toc[a\ufffd]', 'toc[aá]')
text = text.replace('presion[a\ufffd]', 'presion[aá]')
text = text.replace('seleccion[a\ufffd]', 'seleccion[aá]')
text = text.replace('escrib[i\ufffd]', 'escrib[ií]')
text = text.replace('desliz[a\ufffd]', 'desliz[aá]')
text = text.replace('sub[i\ufffd]', 'sub[ií]')
text = text.replace('baj[a\ufffd]', 'baj[aá]')
text = text.replace('entr[a\ufffd]', 'entr[aá]')
text = text.replace('and[a\ufffd]', 'and[aá]')
text = text.replace('navegaci\ufffdn', 'navegación')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print('Fixed NikoSemanticActionResolver.kt')
