const express = require('express');
const Database = require('better-sqlite3');
const cors = require('cors');
const wkt = require('wkt');
const app = express();
const PORT = 3001;

app.use(cors());

let db;
try {
    db = new Database('./tile.db');
    // console.log("Connected to the SQLite database.");
} catch (error) {
    // console.error("Failed to connect to the database:", error.message);
    process.exit(1);  // Exit code 1 to indicate failure
}

app.get('/geojson', (req, res) => {
    // console.log("Fetching data from SQLite database...");

    let rows;
    try {
        rows = db.prepare('SELECT * FROM tile').all();
    } catch (error) {
        // console.error("Failed to fetch data from database:", error.message);
        return res.status(500).json({ error: "Failed to fetch data" });
    }

    // console.log("Data fetched from SQLite:", rows);

    const geojson = {
        type: 'FeatureCollection',
        features: rows.map(row => {
            if (!row.geometry) {
                // console.error("Row with missing geometry:", row);
                return null;
            }

            try {
                const geometry = wkt.parse(row.geometry);
                return {
                    type: 'Feature',
                    geometry: geometry,
                    properties: {
                        zoom: row.zoom,
                        x: row.x,
                        y: row.y,
                        x_rel: row.x_rel,
                        y_rel: row.y_rel,
                        label: row.label,
                    }
                };
            } catch (e) {
                // console.error("Error parsing WKT:", e, row);
                return null;
            }
        }).filter(feature => feature !== null)
    };

    // console.log("Generated GeoJSON:", geojson);
    res.json(geojson);
});

app.listen(PORT, (error) => {
    if (error) {
        // console.error("Failed to start the server:", error.message);
        process.exit(1);
    }
    // console.log(`Server is running on http://localhost:${PORT}`);
});
