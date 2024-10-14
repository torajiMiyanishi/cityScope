import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { Map } from 'react-map-gl/maplibre';
import { DeckGL } from '@deck.gl/react';
import { GeoJsonLayer } from '@deck.gl/layers';
import { LightingEffect, AmbientLight, _SunLight as SunLight } from '@deck.gl/core';
import * as turf from '@turf/turf';
import type { Color, Position, PickingInfo, MapViewState } from '@deck.gl/core';
import type { Feature, Geometry } from 'geojson';
import './styles.css';

const DATA_URL = 'http://localhost:3001/geojson';  // APIエンドポイント
const MAP_STYLE = 'https://basemaps.cartocdn.com/gl/dark-matter-nolabels-gl-style/style.json';

const ambientLight = new AmbientLight({ color: [255, 255, 255], intensity: 1.0 });
const dirLight = new SunLight({
    timestamp: Date.UTC(2019, 7, 1, 12),
    color: [255, 255, 255],
    intensity: 1.0,
    _shadow: true
});
// 色のマップを定義する
const colorMap = {
    0: [0, 0, 255, 50],  // ラベル0の色
    1: [255, 0, 0, 200],  // ラベル1の色
    2: [255, 0, 0, 200],   // ラベル2の色
    // 必要に応じて他のラベルに対しても色を追加
};

function getTooltip({ object }: PickingInfo<Feature<Geometry, { info: string }>>) {
    return (
        object && {
            html: `\
    <div class="tooltip">
      <div><b>Rel. Coord. (${object.properties.x_rel},${object.properties.y_rel})</b></div>
      <div><b>Bldg. Label ${object.properties.label}</b></div>
    </div>\
  `
        }
    );
}

export default function App() {
    const [initialViewState, setInitialViewState] = useState<MapViewState>({
        latitude: 0,
        longitude: 0,
        zoom: 13, // OrthographicViewのために調整
        maxZoom: 16,
        pitch: 0, // 真上からの視点
        bearing: 0,
    });

    const [data, setData] = useState(null);
    const [effects] = useState(() => {
        const lightingEffect = new LightingEffect({ ambientLight, dirLight });
        lightingEffect.shadowColor = [0, 0, 0, 0.5];
        return [lightingEffect];
    });

    useEffect(() => {
        // データ取得用の関数
        const fetchData = () => {
            fetch(DATA_URL)
                .then(response => response.json())
                .then(geojson => {
                    console.log("GeoJSON data:", geojson);
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

        // 初回ロード時のデータ取得
        fetchData();

        // 10秒ごとにデータを再取得
        const interval = setInterval(fetchData, 100);

        // クリーンアップ
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
            getFillColor: f => {
                const label = f.properties.label;
                return colorMap[label] || [128, 128, 128, 100]; // デフォルトは灰色
            },
            getLineColor: [255, 255, 255],
            pickable: true,
        })
    ];

    return (
        <DeckGL
            layers={layers}
            effects={effects}
            initialViewState={initialViewState}
            controller={true}
            getTooltip={getTooltip}
        >
            <Map reuseMaps mapStyle={MAP_STYLE} />
        </DeckGL>
    );
}

export function renderToDOM(container: HTMLDivElement) {
    createRoot(container).render(<App />);
}
