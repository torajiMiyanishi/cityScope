import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { Map } from 'react-map-gl/maplibre';
import { DeckGL } from '@deck.gl/react';
import { GeoJsonLayer } from '@deck.gl/layers';
import { AmbientLight, _SunLight as SunLight } from '@deck.gl/core';
import * as turf from '@turf/turf';
import './styles.css';

const DATA_URL = 'http://localhost:3001/geojson';
const MAP_STYLE = 'https://basemaps.cartocdn.com/gl/dark-matter-nolabels-gl-style/style.json';

const ambientLight = new AmbientLight({ color: [255, 255, 255], intensity: 1.0 });
const dirLight = new SunLight({
    timestamp: Date.UTC(2019, 7, 1, 12),
    color: [255, 255, 255],
    intensity: 1.0,
    _shadow: true
});

const colorMap = {
    0: [0, 0, 255, 50],
    1: [255, 0, 0, 200],
    2: [255, 0, 0, 200]
};

export default function App() {
    const [initialViewState, setInitialViewState] = useState({
        latitude: 0,
        longitude: 0,
        zoom: 13,
        maxZoom: 16,
        pitch: 0,
        bearing: 0,
    });

    const [data, setData] = useState(null);

    useEffect(() => {
        const fetchData = () => {
            fetch(DATA_URL)
                .then(response => response.json())
                .then(geojson => {
                    setData(geojson);
                    if (geojson.features && geojson.features.length > 0) {
                        const bbox = turf.bbox(geojson);
                        const center = turf.center(turf.bboxPolygon(bbox)).geometry.coordinates;
                        setInitialViewState({
                            latitude: center[1],
                            longitude: center[0],
                            zoom: 14,
                            pitch: 0,
                            bearing: 0
                        });
                    }
                })
                .catch(error => console.error("Error fetching data:", error));
        };

        fetchData();
        const interval = setInterval(fetchData, 100);
        return () => clearInterval(interval);
    }, []);

    const layers = [
        new GeoJsonLayer({
            id: 'geojson',
            data,
            opacity: 0.8,
            stroked: false,
            filled: true,
            extruded: true,
            wireframe: true,
            getElevation: f => 10,
            getFillColor: f => colorMap[f.properties.label] || [128, 128, 128, 100],
            getLineColor: [255, 255, 255],
            pickable: true,
        })
    ];

    return (
        <div style={{ position: 'relative', width: '100%', height: '100vh' }}>
            <DeckGL
                layers={layers}
                initialViewState={initialViewState}
                controller={true}
                style={{ position: 'absolute', top: '0', left: '0', width: '100%', height: '100%' }}
            >
                <Map reuseMaps mapStyle={MAP_STYLE} />
            </DeckGL>
        </div>
    );
}

export function renderToDOM(container) {
    createRoot(container).render(<App />);
}
