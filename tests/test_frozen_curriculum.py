"""Exercise the real SQLite/FTS builder against the maintained practical curriculum."""
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
from tools.build_leo_frozen_brain import create_database, insert_record, seed_records

ROOT = Path(__file__).resolve().parents[1]

class FrozenCurriculumTest(unittest.TestCase):
    def test_seed_integrity_and_practical_retrieval(self):
        seed = ROOT / 'brain/leo_seed_knowledge.jsonl'
        items = [json.loads(line) for line in seed.read_text(encoding='utf-8').splitlines() if line.strip()]
        self.assertEqual(len(items), len({item['url'] for item in items}))
        self.assertTrue(all(len(item['text']) >= 80 for item in items))
        with tempfile.TemporaryDirectory() as tmp:
            db = create_database(Path(tmp) / 'brain.sqlite')
            try:
                for record in seed_records(seed):
                    insert_record(db, record)
                db.commit()
                self.assertEqual(len(items), db.execute('SELECT count(*) FROM articles').fetchone()[0])
                cases = {
                    '"depurar" "error"': 'Depurar errores de código',
                    '"gallo" "pinto"': 'Gallo pinto preparación',
                    '"promedio" "eficiencia"': 'Promedio ponderado de eficiencia',
                    '"actualizar" "offline"': 'Actualizar conocimiento offline',
                    '"tarea" "interrumpida"': 'Continuar tarea interrumpida',
                    '"fuentes" "contradicciones"': 'Detectar contradicciones entre fuentes',
                    '"corrutinas" "kotlin"': 'Cancelación de corrutinas en Kotlin',
                    '"cocibolca" "ometepe"': 'Geografía y lagos de Nicaragua',
                }
                for query, expected in cases.items():
                    with self.subTest(query=query):
                        titles = [r[0] for r in db.execute('SELECT title FROM articles_fts WHERE articles_fts MATCH ?', (query,))]
                        self.assertIn(expected, titles)
                self.assertEqual('ok', db.execute('PRAGMA integrity_check').fetchone()[0])
            finally:
                db.close()

if __name__ == '__main__':
    unittest.main()
