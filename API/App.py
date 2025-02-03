import os
from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.security import generate_password_hash, check_password_hash
import pyodbc
import jwt
import datetime

app = Flask(__name__)
CORS(app)
app.config['SECRET_KEY'] = '$YOUR_KEY'

def get_db_connection():
    conn_str = (
        r'DRIVER={ODBC Driver 17 for SQL Server};'
        r'SERVER=DESKTOP-OOE808M;'
        r'DATABASE=user_auth;'
        r'UID=$USERNAME;' 
        r'PWD=$PASS;' 
    )
    return pyodbc.connect(conn_str)

def create_user(username, password):
    password_hash = generate_password_hash(password)
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute(
                "INSERT INTO Users (username, password_hash) VALUES (?, ?)", 
                (username, password_hash)
            )
            conn.commit()
            return True
        except pyodbc.IntegrityError:
            return False

def validate_user(username, password):
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT password_hash FROM Users WHERE username = ?", (username,))
        result = cursor.fetchone()
        
        if result:
            return check_password_hash(result[0], password)
        return False

def generate_token(username):
    payload = {
        'username': username,
        'exp': datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=24)
    }
    return jwt.encode(payload, app.config['SECRET_KEY'], algorithm='HS256')

@app.route('/api/auth/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    if not username or not password:
        return jsonify({'success': False, 'message': 'Username and password required'}), 400
    
    if create_user(username, password):
        return jsonify({'success': True, 'message': 'User registered successfully'}), 201
    else:
        return jsonify({'success': False, 'message': 'Username already exists'}), 409

@app.route('/api/auth/login', methods=['POST'])
def login():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    
    if validate_user(username, password):
        token = generate_token(username)
        return jsonify({'success': True, 'token': token}), 200
    else:
        return jsonify({'success': False, 'message': 'Invalid credentials'}), 401
    
@app.route('/get_data', methods=['GET'])
def get_data():
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute(
                "SELECT pr.*,u.username, dp.description FROM purchase_request pr JOIN Users u ON pr.userID=u.id JOIN department dp ON pr.department=dp.departmentID")
            rows = cursor.fetchall()
            prCol = [col[0] for col in cursor.description]
            purchaseColumns = [dict(zip (prCol, row)) for row in rows]
            return jsonify(purchaseColumns), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500

@app.route('/get_department', methods=['GET'])       
def get_departmentName():
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT description FROM department")
            rows = cursor.fetchall()
            departmentNames = [row.description for row in rows]
            return jsonify(departmentNames), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 405
               
@app.route('/get_master', methods=['GET'])
def get_master():
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT itemID, name, type, unit, standard_amt, price, currency, measuring_unit FROM items_master")
            rows = cursor.fetchall()
            col_names = [col[0] for col in cursor.description]
            masterColumns = [dict(zip (col_names, row)) for row in rows]
            print(masterColumns)
            return jsonify(masterColumns), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500
        
@app.route('/get_matType', methods=['GET'])       
def get_typeID():
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT typeID FROM matrType")
            rows = cursor.fetchall()
            itemTypes = [row.typeID for row in rows]
            return jsonify(itemTypes), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500
        
@app.route('/generate_itemID', methods=['POST'])
def generate_itemID():
    data = request.get_json()
    itemTypeID = data.get("typeID")

    if not itemTypeID:
        return jsonify({"error": "Missing typeID"}), 400

    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            print(itemTypeID)
            cursor.execute("SELECT timeRequested FROM matrType WHERE typeID=?", (itemTypeID,))
            results = cursor.fetchone()

            if results is None:
                return jsonify({"error": "TypeID not found"}), 404

            current_count = results[0] if results[0] is not None else 0

            itemID = f"{itemTypeID}{str(current_count).zfill(4)}"

            print(itemID)

            return jsonify({
                "itemID": itemID,
                "counter": current_count
            }), 201

        except pyodbc.Error as e:
            conn.rollback()
            print(f"Database error: {e}")
            return jsonify({'error': 'Error generating itemID'}), 500
        
@app.route('/update_itemID', methods=['POST'])
def update_itemID():
    data = request.get_json()
    itemTypeID = data.get("typeID")

    if not itemTypeID:
        return jsonify({"error": "Missing typeID"}), 400

    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT timeRequested FROM matrType WHERE typeID=?", (itemTypeID,))
            results = cursor.fetchone()

            if results is None:
                return jsonify({"error": "TypeID not found"}), 404

            current_count = results[0] if results[0] is not None else 0
            new_count = current_count+1

            itemID = f"{itemTypeID}{str(new_count).zfill(4)}"

            cursor.execute("UPDATE matrType SET timeRequested=? WHERE typeID=?", (new_count, itemTypeID,))
            conn.commit()

            return jsonify({
                "itemID": itemID,
                "counter": new_count
            }), 200

        except pyodbc.Error as e:
            conn.rollback()
            print(f"Database error: {e}")
            return jsonify({'error': 'Error updating itemID'}), 500
        
@app.route('/get_master/get_unit', methods=['GET'])       
def get_itemUnit():
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT unitName FROM unitType")
            rows = cursor.fetchall()
            unitTypes = [row.unitName for row in rows]
            return jsonify(unitTypes), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500
        
@app.route('/get_master/get_currency', methods=['GET'])
def get_currencies():
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT currency FROM currenciesTable")
            rows = cursor.fetchall()
            currencyTypes = [row.currency for row in rows]
            return jsonify(currencyTypes), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500
        
@app.route('/get_master/get_name', methods=['GET'])
def get_itemName():
    typeID = request.args.get("typeID")
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT name FROM items_master WHERE type=?",(typeID,))
            rows = cursor.fetchall()
            itemNames = [row.name for row in rows]
            return jsonify(itemNames), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500
        
@app.route('/get_master/get_itemInfo', methods=['GET'])
def get_itemInfo():
    typeID = request.args.get("typeID")
    name = request.args.get("itemName")
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT * FROM items_master WHERE type=? INTERSECT SELECT * FROM items_master WHERE name=?",(typeID,name,))
            rows = cursor.fetchall()
            itemInfo = [row[0] for row in cursor.description]
            masterInfo=[dict(zip (itemInfo, row)) for row in rows]
            print(masterInfo)
            response = jsonify(masterInfo)
            response.headers['Connection'] = 'close'
            return response, 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500
        
@app.route('/get_master/get_measure', methods=['GET'])
def get_itemMeasure():
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute("SELECT measurementName FROM measuringUnit")
            rows = cursor.fetchall()
            measureTypes = [row.measurementName for row in rows]
            return jsonify(measureTypes), 200
        except pyodbc.Error as e:
            print(f"Database query error: {e}")
            return jsonify({'error': 'Error fetching data'}), 500
        
@app.route('/update_masterTable', methods=['POST'])
def update_master():
    data = request.get_json()

    itemId = data.get('itemId')
    name = data.get('name')
    itemType = data.get('itemType')
    unit= data.get('unit')
    standardAmt = data.get('standardAmt')
    price = data.get('price')
    currency = data.get('currency')
    measureUnit = data.get('measureUnit')
    
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            print(itemId)
            cursor.execute('UPDATE items_master SET name=?, type=?, unit=?, standard_amt=?, price=?, currency=?, measuring_unit=? WHERE itemID=?',
                           (name,itemType, unit, standardAmt, price, currency, measureUnit, itemId,))
            print("SQL executed with values:", (itemId,itemType,name,unit,standardAmt,price,currency,measureUnit,))
            conn.commit()
            return jsonify({'success':True, 'message':'Update success'}),200
        except pyodbc.Error as e:
            print("Database error:", str(e))
            conn.rollback()
            return jsonify({'error':'Error updating data'}),500
        
        
@app.route('/entry_masterTable', methods=['POST'])
def entry_master():
    data = request.get_json()

    itemId = data.get('itemId')
    name = data.get('name')
    itemType = data.get('itemType')
    unit= data.get('unit')
    standardAmt = data.get('standardAmt')
    price = data.get('price')
    currency = data.get('currency')
    measureUnit = data.get('measureUnit')
    
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute('INSERT INTO items_master(itemID, name, type, unit, standard_amt, price, currency, measuring_unit) VALUES (?,?,?,?,?,?,?,?)',
                           (itemId,name,itemType,unit,standardAmt,price,currency,measureUnit,))
            print("SQL executed with values:", (itemId,itemType,name,unit,standardAmt,price,currency,measureUnit,))
            conn.commit()
            return jsonify({'success':True, 'message':'Insert success'}),200
        except pyodbc.Error as e:
            print("Database error:", str(e))
            conn.rollback()
            return jsonify({'error':'Error inserting data'}),500

@app.route('/master_deleteRow', methods=['POST'])
def delete_row():
    data = request.get_json()
    itemId = data.get('itemId')
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute('DELETE FROM items_master WHERE itemId=?',(itemId,))
            conn.commit()
            return jsonify({'success':True, 'message':'Delete success', 'error':None}),200
        except pyodbc.Error as e:
            print("Database error:", str(e))
            conn.rollback()
            return jsonify({'success':False, 'message':None, 'error':'Error deleting data'}),500
        
@app.route('/entry_purchaseRequest', methods=['POST'])
def entry_purchase():
    data = request.get_json()

    itemID = data.get('itemID')
    department = data.get('department')
    period = data.get('period')
    userID= data.get('userID')
    lot = data.get('lot')
    quantity = data.get('quantity')
    
    with get_db_connection() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute('INSERT INTO purchaseRequest(itemID, department, periodRequest, userID, lot, quantity) VALUES (?,?,?,?,?,?)',
                           (itemID,department,period,userID,lot,quantity,))
            print("SQL executed with values:", (itemID,department,period,userID,lot,quantity))
            conn.commit()
            return jsonify({'success':True, 'message':'Insert success'}),200
        except pyodbc.Error as e:
            print("Database error:", str(e))
            conn.rollback()
            return jsonify({'error':'Error inserting data'}),500
        
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)