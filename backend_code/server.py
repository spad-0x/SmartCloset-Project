from flask import Flask, jsonify, request
import sqlite3
import os
import base64
import time

app = Flask(__name__)
DB_NAME = "smartcloset.db"
# Percorso assoluto dove salvare le immagini (sostituisci 'tuousername' se necessario, ma di solito flask lo trova da solo se usiamo percorsi relativi corretti o static folder)
UPLOAD_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'static/uploads')

# Assicuriamoci che la cartella esista
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# --- GESTIONE DATABASE ---
def init_db():
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS clothes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            image_url TEXT NOT NULL,
            category TEXT NOT NULL,
            color TEXT,
            season TEXT
        )
    ''')
    conn.commit()
    conn.close()

if not os.path.exists(DB_NAME):
    init_db()

# --- ENDPOINTS API ---

@app.route('/')
def home():
    return "SmartCloset Server (Local Storage) is Running!"

@app.route('/clothes', methods=['POST'])
def add_cloth():
    """
    Riceve un JSON.
    Payload atteso: {
        "user_id": "...",
        "image_base64": "stringa_lunghissima_base64...",
        "category": "...",
        ...
    }
    """
    data = request.json

    if not data or 'user_id' not in data:
        return jsonify({"error": "Dati mancanti"}), 400

    image_url = ""

    # 1. GESTIONE IMMAGINE: Da Base64 a File Locale
    if 'image_base64' in data and data['image_base64']:
        try:
            # Genera nome file unico usando il timestamp
            filename = f"img_{int(time.time())}.jpg"
            filepath = os.path.join(UPLOAD_FOLDER, filename)

            # Decodifica la stringa e salva il file
            img_data = base64.b64decode(data['image_base64'])
            with open(filepath, 'wb') as f:
                f.write(img_data)

            # Costruisci l'URL pubblico (PythonAnywhere serve automaticamente la cartella /static)
            # Sostituisci 'tuousername' con il tuo vero username di PythonAnywhere
            username = os.environ.get('USERNAME') # Prende l'username dal sistema
            image_url = f"https://{username}[.pythonanywhere.com/static/uploads/](https://.pythonanywhere.com/static/uploads/){filename}"

        except Exception as e:
            return jsonify({"error": f"Errore salvataggio immagine: {str(e)}"}), 500
    else:
        return jsonify({"error": "Immagine mancante"}), 400

    # 2. SALVATAGGIO SU DB
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    cursor.execute('''
        INSERT INTO clothes (user_id, image_url, category, color, season)
        VALUES (?, ?, ?, ?, ?)
    ''', (data['user_id'], image_url, data.get('category', 'Other'), data.get('color', ''), data.get('season', 'All')))

    conn.commit()
    conn.close()

    return jsonify({"message": "Vestito salvato", "url": image_url}), 201

@app.route('/clothes', methods=['GET'])
def get_clothes():
    user_id = request.args.get('user_id')
    if not user_id:
        return jsonify({"error": "User ID mancante"}), 400

    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    cursor.execute('SELECT * FROM clothes WHERE user_id = ?', (user_id,))
    rows = cursor.fetchall()
    conn.close()

    results = []
    for row in rows:
        results.append({
            "id": row["id"],
            "image_url": row["image_url"], # Sarà l'URL di PythonAnywhere
            "category": row["category"],
            "season": row["season"]
        })

    return jsonify(results), 200