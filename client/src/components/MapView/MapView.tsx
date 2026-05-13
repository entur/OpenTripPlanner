import {
  LngLat,
  Map,
  MapEvent,
  MapGeoJSONFeature,
  MapMouseEvent,
  NavigationControl,
  MapRef,
} from 'react-map-gl/maplibre';
import maplibregl, { VectorTileSource } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { TripPattern, TripQuery, TripQueryVariables } from '../../gql/graphql.ts';
import { NavigationMarkers } from './NavigationMarkers.tsx';
import { LegLines } from './LegLines.tsx';
import { useMapDoubleClick } from './useMapDoubleClick.ts';
import { useState, useCallback, useEffect, useRef } from 'react';
import { ContextMenuPopup } from './ContextMenuPopup.tsx';
import { GeometryPropertyPopup } from './GeometryPropertyPopup.tsx';
import RightMenu from './RightMenu.tsx';
import { getTraceApiUrl, reloadTraceSource } from './TraceSelector.tsx';
import { findSelectedDebugLayers } from '../../util/map.ts';
import { FeatureSelectPopup } from './FeatureSelectPopup.tsx';

const styleUrl = import.meta.env.VITE_DEBUG_STYLE_URL;

type PopupData = { coordinates: LngLat; feature: MapGeoJSONFeature };
type FeatureSelectData = { coordinates: LngLat; features: MapGeoJSONFeature[] };

export function MapView({
  tripQueryVariables,
  setTripQueryVariables,
  tripQueryResult,
  selectedTripPatternIndexes,
  loading,
}: {
  tripQueryVariables: TripQueryVariables;
  setTripQueryVariables: (variables: TripQueryVariables) => void;
  tripQueryResult: TripQuery | null;
  selectedTripPatternIndexes: number[];
  loading: boolean;
}) {
  const [activeTraceId, setActiveTraceId] = useState<string | null>(null);
  const mapRef = useRef<MapRef>(null);

  // Auto-load latest trace after each search completes
  const prevResultRef = useRef(tripQueryResult);
  useEffect(() => {
    if (!tripQueryResult || tripQueryResult === prevResultRef.current) return;
    prevResultRef.current = tripQueryResult;

    // Delay to let the backend store the trace
    const timerId = setTimeout(async () => {
      try {
        const response = await fetch(getTraceApiUrl('/'));
        if (!response.ok) return;
        const traces = await response.json();
        if (traces.length > 0) {
          const latestId = traces[0].id;
          await fetch(getTraceApiUrl(`/${latestId}/activate`), { method: 'POST' });
          setActiveTraceId(latestId);
          if (mapRef.current) {
            reloadTraceSource(mapRef.current);
          }
        }
      } catch {
        // trace endpoint not available
      }
    }, 500);
    return () => clearTimeout(timerId);
  }, [tripQueryResult]);

  const onSelectTrace = useCallback(
    async (traceId: string | null) => {
      if (!traceId) {
        setActiveTraceId(null);
        return;
      }
      try {
        await fetch(getTraceApiUrl(`/${traceId}/activate`), { method: 'POST' });
        setActiveTraceId(traceId);
        if (mapRef.current) {
          reloadTraceSource(mapRef.current);
        }
      } catch {
        // activation failed
      }
    },
    [],
  );

  const onMapDoubleClick = useMapDoubleClick({ tripQueryVariables, setTripQueryVariables });
  const [showContextPopup, setShowContextPopup] = useState<LngLat | null>(null);
  const [showPropsPopup, setShowPropsPopup] = useState<PopupData | null>(null);
  const [showFeatureSelectPopup, setShowFeatureSelectPopup] = useState<FeatureSelectData | null>(null);
  const [interactiveLayerIds, setInteractiveLayerIds] = useState<string[]>([]);
  const [cursor, setCursor] = useState<string>('auto');
  const onMouseEnter = useCallback(() => setCursor('pointer'), []);
  const onMouseLeave = useCallback(() => setCursor('auto'), []);
  const showFeaturePropPopup = (
    e: MapMouseEvent & {
      features?: MapGeoJSONFeature[] | undefined;
    },
  ) => {
    if (e.features) {
      if (e.features.length == 1) {
        const feature = e.features[0];
        setShowPropsPopup({ coordinates: e.lngLat, feature: feature });
      }
      if (e.features.length > 1) {
        setShowFeatureSelectPopup({ coordinates: e.lngLat, features: e.features });
      }
    }
  };
  const panToWorldEnvelopeIfRequired = (e: MapEvent) => {
    const map = e.target;
    if (map.getZoom() < 2) {
      const source = map.getSource('stops') as VectorTileSource;
      map.fitBounds(source.bounds, { animate: false });
    }
  };

  const onLoad = (e: MapEvent) => {
    const map = e.target;
    map.addControl(new maplibregl.AttributionControl(), 'bottom-left');
  };

  function handleMapLoad(e: MapEvent) {
    panToWorldEnvelopeIfRequired(e);
    const selected = findSelectedDebugLayers(e.target);
    setInteractiveLayerIds(selected);
    onLoad(e);
  }

  return (
    <div className="map-container below-content">
      <Map
        attributionControl={false}
        // @ts-ignore
        mapLib={import('maplibre-gl')}
        // @ts-ignore
        mapStyle={styleUrl}
        onDblClick={onMapDoubleClick}
        onContextMenu={(e) => {
          setShowContextPopup(e.lngLat);
        }}
        interactiveLayerIds={interactiveLayerIds}
        cursor={cursor}
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onClick={showFeaturePropPopup}
        hash={true}
        touchPitch={false}
        dragRotate={false}
        onLoad={handleMapLoad}
        ref={mapRef}
      >
        <NavigationControl position="top-left" />
        <NavigationMarkers
          setCursor={setCursor}
          tripQueryVariables={tripQueryVariables}
          setTripQueryVariables={setTripQueryVariables}
          loading={loading}
        />

        <RightMenu
          position="top-right"
          setInteractiveLayerIds={setInteractiveLayerIds}
          mapRef={mapRef?.current}
          activeTraceId={activeTraceId}
          onSelectTrace={onSelectTrace}
        />
        {tripQueryResult?.trip.tripPatterns.length &&
          selectedTripPatternIndexes.map((index) => {
            const tripPattern = tripQueryResult.trip.tripPatterns[index];
            return tripPattern ? (
              <LegLines key={`trippattern-${index}`} tripPattern={tripPattern as TripPattern} />
            ) : null;
          })}
        {showContextPopup && (
          <ContextMenuPopup
            tripQueryVariables={tripQueryVariables}
            setTripQueryVariables={setTripQueryVariables}
            coordinates={showContextPopup}
            onClose={() => setShowContextPopup(null)}
          />
        )}
        {showPropsPopup?.feature?.properties && (
          <GeometryPropertyPopup
            coordinates={showPropsPopup?.coordinates}
            properties={showPropsPopup?.feature?.properties}
            onClose={() => setShowPropsPopup(null)}
          />
        )}
        {showFeatureSelectPopup && (
          <FeatureSelectPopup
            {...showFeatureSelectPopup}
            setShowPropsPopup={setShowPropsPopup}
            onClose={() => setShowFeatureSelectPopup(null)}
          />
        )}
      </Map>
    </div>
  );
}
