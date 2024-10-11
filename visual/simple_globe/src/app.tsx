import React from 'react';
import { createRoot } from 'react-dom/client';
import DeckGL from '@deck.gl/react';
import { _GlobeView as GlobeView } from '@deck.gl/core';
import { ScatterplotLayer } from '@deck.gl/layers';

const INITIAL_VIEW_STATE = {
    longitude: 0,
    latitude: 0,
    zoom: 0,
    minZoom: 0,
    maxZoom: 5,
    pitch: 0,
    bearing: 0
};

// サンプルデータ（緯度、経度、サイズ）
const data = [
    { position: [-74.006, 40.7128], size: 100 }, // New York
    { position: [139.6917, 35.6895], size: 100 }, // Tokyo
    { position: [2.3522, 48.8566], size: 100 }, // Paris
    { position: [151.2093, -33.8688], size: 100 } // Sydney
];

// 散布図レイヤー
const scatterplotLayer = new ScatterplotLayer({
    id: 'scatterplot-layer',
    data,
    pickable: true,
    opacity: 0.8,
    stroked: true,
    filled: true,
    radiusScale: 10,
    radiusMinPixels: 1,
    radiusMaxPixels: 100,
    getPosition: d => d.position,
    getRadius: d => d.size,
    getFillColor: [255, 140, 0],
    getLineColor: [0, 0, 0]
});

function App() {
    return (
        <DeckGL
            views={new GlobeView()}
            initialViewState={INITIAL_VIEW_STATE}
            controller={true}
            layers={[scatterplotLayer]} // ScatterplotLayerを追加
        />
    );
}

const container = document.getElementById('root');
const root = createRoot(container!);
root.render(<App />);
