import React, { useState, useEffect, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { DeckGL } from '@deck.gl/react';
import { ScatterplotLayer } from '@deck.gl/layers';
import { Map } from 'react-map-gl/maplibre';
import 'maplibre-gl/dist/maplibre-gl.css';

const MAP_STYLE = 'https://basemaps.cartocdn.com/gl/dark-matter-nolabels-gl-style/style.json';
const LOCATIONS_DATA_URL = 'http://localhost:3001/fetch_locations';
const COLORS_JSON_URL = './colors.json';

export default function App() {
    const [scatterData, setScatterData] = useState([]);
    const [viewState, setViewState] = useState({
        latitude: 35.41958561950065,
        longitude: 139.59388295472456,
        zoom: 14,
        pitch: 0,
        bearing: 0,
    });
    const [colorPattern, setColorPattern] = useState([]);
    const [minTimestamp, setMinTimestamp] = useState<number | null>(null);
    const [maxTimestamp, setMaxTimestamp] = useState<number | null>(null);
    const [currentTimestamp, setCurrentTimestamp] = useState<number | null>(null);

    useEffect(() => {
        // JSONから色パターンを読み込む
        fetch(COLORS_JSON_URL)
            .then(response => response.json())
            .then(data => setColorPattern(data))
            .catch(error => console.error('Error loading color pattern:', error));
    }, []);

    useEffect(() => {
        const fetchData = () => {
            fetch(LOCATIONS_DATA_URL)
                .then(response => response.json())
                .then(data => {
                    const features = data.features;
                    const coordinatesList = data.features.map(feature => feature.geometry.coordinates);

                    // 色のマッピング
                    const agentColorMap = {};
                    let colorIndex = 0;
                    features.forEach(feature => {
                        const name = feature.properties.person_name;
                        if (!(name in agentColorMap)) {
                            agentColorMap[name] = colorPattern[colorIndex % colorPattern.length];
                            colorIndex++;
                        }
                    });

                    // フォーマットされたデータの生成
                    const formattedData = data.features.map(feature => ({
                        timestamp: feature.properties.timestamp,
                        position: feature.geometry.coordinates,
                        color: hexToRgb(agentColorMap[feature.properties.person_name]),
                        size: 20,
                    }));
                    setScatterData(formattedData);

                    // timestampの最小値・最大値
                    const timestamps = features.map(feature => feature.properties.timestamp);
                    setMinTimestamp(Math.min(...timestamps));
                    setMaxTimestamp(Math.max(...timestamps));
                })
                .catch(error => console.error('Error fetching location data:', error));
        };

        fetchData();
        const fetchInterval = setInterval(fetchData, 100);

        // クリーンアップ
        return () => clearInterval(fetchInterval);
    });

    useEffect(() => {
        const animateInterval = setInterval(() => {
            if (currentTimestamp == null || currentTimestamp >= maxTimestamp) {
                setCurrentTimestamp(minTimestamp);
            } else {
                setCurrentTimestamp(t => t + 60 * 1000);
            }
        },100);
        // クリーンアップ
        return () => clearInterval(animateInterval);
    });

    const filteredData = scatterData.filter(d => d.timestamp === currentTimestamp);

    const layers = [
        new ScatterplotLayer({
            id: 'scatterplot-layer',
            data: filteredData,
            pickable: true,
            getPosition: d => d.position,
            getFillColor: d => d.color,
            getRadius: d => d.size,
            opacity: 0.8,
        })
    ];

    return (
        <div>
            <DeckGL
                initialViewState={viewState}
                controller={true}
                layers={layers}
                style={{ position: 'absolute', width: '100%', height: '100%' }}
            >
                <Map mapStyle={MAP_STYLE} />
            </DeckGL>
            <div style={{
                position: 'absolute',
                top: '10px',
                right: '10px',
                padding: '10px',
                backgroundColor: 'rgba(255, 255, 255, 0.8)',
                borderRadius: '8px',
                zIndex: 1
            }}>
                <p>CURRENT TIME: {convertUnixTimestamp(currentTimestamp)}</p>
                <p>MIN TIMESTAMP: {convertUnixTimestamp(minTimestamp)}</p>
                <p>MAX TIMESTAMP: {convertUnixTimestamp(maxTimestamp)}</p>
            </div>
        </div>
    );
}

export function renderToDOM(container) {
    createRoot(container).render(<App />);
}

function convertUnixTimestamp(timestamp: number | null): string {
    if (timestamp === null) return 'N/A';
    const date = new Date(timestamp);
    return date.toLocaleString("ja-JP", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    });
}

function hexToRgb(hex: string | undefined): [number, number, number] {
    if (!hex) return [0, 0, 0];
    hex = hex.replace('#', '');
    return [
        parseInt(hex.substring(0, 2), 16),
        parseInt(hex.substring(2, 4), 16),
        parseInt(hex.substring(4, 6), 16),
    ];
}
