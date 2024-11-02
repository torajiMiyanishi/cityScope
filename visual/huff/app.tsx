import React from 'react';
import {createRoot} from 'react-dom/client';
import DeckGL from '@deck.gl/react';
import {ScatterplotLayer} from '@deck.gl/layers';
import {Map} from 'react-map-gl/maplibre';

// サンプルデータ: 訪問先IDに基づくデータ
const DATA = [
    {longitude: 139.7, latitude: 35.7, demand: 100, destinationId: 1},
    {longitude: 139.8, latitude: 35.7, demand: 80, destinationId: 2},
    {longitude: 139.6, latitude: 35.8, demand: 60, destinationId: 3}
];

const INITIAL_VIEW_STATE = {
    longitude: 139.6917,
    latitude: 35.6895,
    zoom: 10,
    pitch: 0,
    bearing: 0
};

// 訪問先IDごとの色のマッピング
const destinationColorMapping = {
    1: [255, 0, 0],    // 赤
    2: [0, 255, 0],    // 緑
    3: [0, 0, 255]     // 青
};

export default function App() {
    const layers = [
        new ScatterplotLayer({
            id: 'scatterplot',
            data: DATA,
            getPosition: d => [d.longitude, d.latitude],
            getRadius: d => d.demand * 10, // 需要に基づいて半径を調整
            getFillColor: d => destinationColorMapping[d.destinationId] || [255, 255, 255],
            opacity: 0.8,
            radiusScale: 1,
            radiusMinPixels: 1,
            radiusMaxPixels: 100,
            // pickable: true
        })
    ];

    return (
        <DeckGL
            initialViewState={INITIAL_VIEW_STATE}
            controller={true}
            layers={layers}
        >
            <Map reuseMaps mapStyle="https://basemaps.cartocdn.com/gl/dark-matter-nolabels-gl-style/style.json" />
        </DeckGL>
    );
}

export function renderToDOM(container) {
    createRoot(container).render(<App />);
}
