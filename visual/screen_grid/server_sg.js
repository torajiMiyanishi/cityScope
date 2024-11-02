const express = require('express');
const Database = require('better-sqlite3');
const cors = require('cors');
const bodyParser = require('body-parser'); // POSTリクエストのボディを解析するために追加
const app = express();
const PORT = 3001;

app.use(cors());
app.use(bodyParser.json()); // JSONデータの解析

let db;
let db_poi;
try {
    db = new Database('./MARKER_POSITION.db');
    db_poi = new Database('./POI.db')
} catch (error) {
    console.error("データベースの接続に失敗しました:", error);
    process.exit(1);
}

// 駒の位置をreadする
app.get('/fetch_positions', (req, res) => {
    let rows;
    try {
        rows = db.prepare('SELECT x_rel, y_rel FROM positions').all();
    } catch (error) {
        console.error("データの取得に失敗しました:", error);
        return res.status(500).json({ error: "Failed to fetch data" });
    }

    const data = rows.map(row => ({
        x_rel: row.x_rel,
        y_rel: row.y_rel
    }));

    res.json(data);
});

// ブラウザ画面上の地図範囲をinsertする
app.post('/insert_map_bounds', (req, res) => {
    const { id, top_left_lat, top_left_lng, top_right_lat, top_right_lng, bottom_left_lat, bottom_left_lng, bottom_right_lat, bottom_right_lng } = req.body;

    try {
        // idが0のレコードが存在するか確認
        const existingRecord = db.prepare(`SELECT 1 FROM map_bounds WHERE id = 0`).get();

        // レコードが存在すれば削除
        if (existingRecord) {
            db.prepare(`DELETE FROM map_bounds WHERE id = 0`).run();
        }

        // 新しいレコードを挿入
        const stmt = db.prepare(`
            INSERT INTO map_bounds (
                id, top_left_lat, top_left_lng, top_right_lat, top_right_lng,
                bottom_left_lat, bottom_left_lng, bottom_right_lat, bottom_right_lng
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        `);
        stmt.run(id, top_left_lat, top_left_lng, top_right_lat, top_right_lng, bottom_left_lat, bottom_left_lng, bottom_right_lat, bottom_right_lng);

        res.status(201).json({ message: "Position inserted successfully" });
    } catch (error) {
        console.error("データの挿入または更新に失敗しました:", error);
        res.status(500).json({ error: "Failed to insert or update data" });
    }
});

app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});

// map_bounds テーブルのデータ取得エンドポイント
app.get('/fetch_map_bounds', (req, res) => {
    try {
        // map_bounds テーブルの id=0 のレコードを取得
        const row = db.prepare(`
            SELECT id, top_left_lat, top_left_lng, top_right_lat, top_right_lng,
                   bottom_left_lat, bottom_left_lng, bottom_right_lat, bottom_right_lng
            FROM map_bounds
            WHERE id = 0
        `).get();

        // レコードが存在しない場合
        if (!row) {
            return res.status(404).json({ error: "No data found for id = 0" });
        }

        // レコードが存在する場合は JSON で返す
        res.json({
            id: row.id,
            top_left_lat: row.top_left_lat,
            top_left_lng: row.top_left_lng,
            top_right_lat: row.top_right_lat,
            top_right_lng: row.top_right_lng,
            bottom_left_lat: row.bottom_left_lat,
            bottom_left_lng: row.bottom_left_lng,
            bottom_right_lat: row.bottom_right_lat,
            bottom_right_lng: row.bottom_right_lng
        });
    } catch (error) {
        console.error("データの取得に失敗しました:", error);
        res.status(500).json({ error: "Failed to fetch data from map_bounds" });
    }
});

// poi テーブルのデータ取得エンドポイント
app.get('/fetch_poi', (req, res) => {
    let rows;
    try {
        // map_bounds テーブルの id=0 のレコードを取得
        rows = db_poi.prepare(`
            SELECT uuid, lat, lon, accessibility
            FROM poi
        `).all();

        // レコードが存在しない場合
        if (!rows) {
            return res.status(404).json({ error: "No data found for id = 0" });
        }

        // レコードが存在する場合は JSON で返す
        const data = rows.map(row => ({
            uuid: row.uuid,
            lat: row.lat,
            lon: row.lon,
            accessibility: row.accessibility
        }));

        res.json(data);
    } catch (error) {
        console.error("データの取得に失敗しました:", error);
        res.status(500).json({ error: "Failed to fetch data from map_bounds" });
    }
});

// アクセシビリティをinsertする
app.post('/update_acc', (req, res) => {
    const { uuid, accessibility } = req.body;

    try {
        // uuidが一致するレコードが存在するか確認
        const existingRecord = db_poi.prepare(`SELECT 1 FROM poi WHERE uuid = ?`).get(uuid);

        if (existingRecord) {
            // レコードが存在すれば更新
            const stmt = db_poi.prepare(`
                UPDATE poi
                SET accessibility = ?
                WHERE uuid = ?
            `);
            stmt.run(accessibility, uuid);

            res.status(200).json({ message: "Accessibility updated successfully" });
        } else {
            // レコードが存在しない場合はエラーレスポンス
            res.status(404).json({ error: "Record not found" });
        }
    } catch (error) {
        console.error("データの更新に失敗しました:", error);
        res.status(500).json({ error: "Failed to update accessibility" });
    }
});

// 駒の地理座標を上書きする
app.post('/update_block_latlon', (req, res) => {
    const { lat, lon } = req.body;

    try {
        // レコードがあるかチェック
        const existingRecord = db.prepare(`SELECT 1 FROM block_latlon`).get();

        // レコードが存在すれば削除
        if (existingRecord) {
            db.prepare(`DELETE FROM block_latlon`).run();
        }

        // 新しいレコードを挿入
        const stmt = db.prepare(`
            INSERT INTO block_latlon (
                lat, lon
            ) VALUES (?, ?)
        `);
        stmt.run(lat, lon);

        res.status(201).json({ message: "Position inserted successfully" });
    } catch (error) {
        console.error("データの挿入または更新に失敗しました:", error);
        res.status(500).json({ error: "Failed to insert or update data" });
    }
});

// poi テーブルのデータ取得エンドポイント
app.get('/fetch_block_latlon', (req, res) => {
    let rows;
    try {
        // map_bounds テーブルの id=0 のレコードを取得
        rows = db.prepare(`
            SELECT lat, lon
            FROM block_latlon
        `).all();

        // レコードが存在しない場合
        if (!rows) {
            return res.status(404).json({ error: "No data found for id = 0" });
        }

        // レコードが存在する場合は JSON で返す
        const data = rows.map(row => ({
            lat: row.lat,
            lon: row.lon,
        }));

        res.json(data);
    } catch (error) {
        console.error("データの取得に失敗しました:", error);
        res.status(500).json({ error: "Failed to fetch data from block_latlon" });
    }
});


