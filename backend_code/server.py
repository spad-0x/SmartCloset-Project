from flask import Flask, jsonify, request
import sqlite3
import os
import base64
import time

app = Flask(__name__)

# --- CONFIGURAZIONE ---
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_NAME = os.path.join(BASE_DIR, "smartcloset.db")
UPLOAD_FOLDER = os.path.join(BASE_DIR, 'static/uploads')
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# --- DB INIT ---
if not os.path.exists(DB_NAME):
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

@app.route('/')
def home():
    return "SmartCloset Server (Lite) is Running!"

@app.route('/clothes', methods=['POST'])
def add_cloth():
    data = request.json
    if not data or 'image_base64' not in data:
        return jsonify({"error": "Dati mancanti"}), 400

    try:
        # L'immagine arriva GIA' RITAGLIATA e in PNG dall'app Android
        # La decodifichiamo e salviamo direttamente come file binario.
        # Poiché l'app invia i byte di un PNG, salvare con estensione .png è corretto.

        filename = f"img_{int(time.time())}.png"
        filepath = os.path.join(UPLOAD_FOLDER, filename)

        img_data = base64.b64decode(data['image_base64'])

        with open(filepath, 'wb') as f:
            f.write(img_data)

        # COSTRUZIONE URL
        # Usa os.environ.get('USERNAME') per essere dinamico, con fallback manuale
        username = os.environ.get('USERNAME')
        if not username:
            username = "spad0x" # Metti qui il tuo username esatto per sicurezza

        image_url = f"https://{username}.pythonanywhere.com/static/uploads/{filename}"

        # SALVATAGGIO DB
        conn = sqlite3.connect(DB_NAME)
        cursor = conn.cursor()
        cursor.execute('''
            INSERT INTO clothes (user_id, image_url, category, color, season)
            VALUES (?, ?, ?, ?, ?)
        ''', (data['user_id'], image_url, data.get('category', 'Uncategorized'), data.get('color', ''), data.get('season', 'All')))
        conn.commit()
        conn.close()

        return jsonify({"message": "Vestito salvato", "url": image_url}), 201

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/clothes', methods=['GET'])
def get_clothes():
    user_id = request.args.get('user_id')
    if not user_id: return jsonify({"error": "User ID mancante"}), 400

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
            "image_url": row["image_url"],
            "category": row["category"],
            "season": row["season"]
        })
    return jsonify(results), 200

# --- NUOVO ENDPOINT PER CANCELLARE ---
@app.route('/clothes', methods=['DELETE'])
def delete_cloth():
    user_id = request.args.get('user_id')
    image_url = request.args.get('image_url')

    if not user_id or not image_url:
        return jsonify({"error": "Parametri mancanti"}), 400

    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    # 1. Trova il vestito nel DB usando l'URL
    cursor.execute('SELECT * FROM clothes WHERE image_url = ? AND user_id = ?', (image_url, user_id))
    cloth = cursor.fetchone()

    if not cloth:
        conn.close()
        return jsonify({"error": "Vestito non trovato"}), 404

    try:
        # Estrai il nome del file dall'URL per cancellarlo fisicamente
        # Prende l'ultima parte dell'URL (es. img_12345.png)
        filename = image_url.split('/')[-1]
        filepath = os.path.join(UPLOAD_FOLDER, filename)

        if os.path.exists(filepath):
            os.remove(filepath)
            print(f"File rimosso: {filename}")
    except Exception as e:
        print(f"Errore cancellazione file: {e}")

    # 2. Cancella dal DB usando l'URL
    cursor.execute('DELETE FROM clothes WHERE image_url = ? AND user_id = ?', (image_url, user_id))
    conn.commit()
    conn.close()

    return jsonify({"message": "Cancellato con successo"}), 200