const express = require('express');
const Database = require('better-sqlite3');
const cors = require('cors');
const wkt = require('wkt');
const app = express();
const PORT = 3001;

app.use(cors());

let db;
try {
    db = new Database('./CityData.db');
} catch (error) {
    process.exit(1);
}

app.get('/fetch_tiles', (req, res) => {

    let rows;
    try {
        rows = db.prepare('SELECT * FROM tile').all();
    } catch (error) {
        return res.status(500).json({ error: "Failed to fetch data" });
    }


    const geojson = {
        type: 'FeatureCollection',
        features: rows.map(row => {
            if (!row.geometry) {
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
                return null;
            }
        }).filter(feature => feature !== null)
    };

    res.json(geojson);
});

app.get('/fetch_locations', (req, res) => {

    let rows;
    try {
        rows = db.prepare('SELECT timestamp, latitude, longitude, person_name, gender, age FROM agent_locations').all();
    } catch (error) {
        return res.status(500).json({ error: "Failed to fetch data" });
    }

    const geojson = {
        type: 'FeatureCollection',
        features: rows.map(row => ({
            type: 'Feature',
            geometry: {
                type: 'Point',
                coordinates: [row.longitude, row.latitude]
            },
            properties: {
                timestamp: row.timestamp,
                person_name: row.person_name,
                gender: row.gender,
                age: row.age
            }
        }))
    };
    res.json(geojson);
});
app.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});
