import React, {useState, useEffect} from 'react';
import {createRoot} from 'react-dom/client';
import {Map} from 'react-map-gl/maplibre';
import {DeckGL} from '@deck.gl/react';
import {GeoJsonLayer, PolygonLayer} from '@deck.gl/layers';
import {LightingEffect, AmbientLight, _SunLight as SunLight} from '@deck.gl/core';
import {scaleThreshold} from 'd3-scale';
import * as d3 from 'd3';
import * as turf from '@turf/turf';
import type {Color, Position, PickingInfo, MapViewState} from '@deck.gl/core';
import type {Feature, Geometry} from 'geojson';
import {OrthographicView} from '@deck.gl/core';

const DATA_URL = 'tile_data.json';
const MAP_STYLE = 'https://basemaps.cartocdn.com/gl/dark-matter-nolabels-gl-style/style.json';

const ambientLight = new AmbientLight({ color: [255, 255, 255], intensity: 1.0 });
const dirLight = new SunLight({
    timestamp: Date.UTC(2019, 7, 1, 22),
    color: [255, 255, 255],
    intensity: 1.0,
    _shadow: true
});

function getTooltip({object}: PickingInfo<Feature<Geometry, {mesh_code: string}>>) {
    return (
        object && {
            html: `\
  <div><b>Mesh Code</b></div>
  <div>${object.properties.mesh_code}</div>
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
        const lightingEffect = new LightingEffect({ambientLight, dirLight});
        lightingEffect.shadowColor = [0, 0, 0, 0.5];
        return [lightingEffect];
    });

    useEffect(() => {
        fetch(DATA_URL)
            .then(response => response.json())
            .then(geojson => {
                setData(geojson);
                console.log(geojson);
                const bbox = turf.bbox(geojson);
                const center = turf.center(turf.bboxPolygon(bbox)).geometry.coordinates;
                setInitialViewState({
                    latitude: center[1],
                    longitude: center[0],
                    zoom: 14,
                    pitch: 0,
                    bearing: 0
                });
            });
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
            getFillColor: f => [0, 128, 255, 100],
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
