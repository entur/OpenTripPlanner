import { useCallback, useEffect, useState } from 'react';
import type { MapRef } from 'react-map-gl/maplibre';
import type { LayerSpecification } from 'maplibre-gl';

interface TraceSummary {
  id: string;
  timestamp: string;
  description: string;
  eventCount: number;
  maxSeq: number;
}

const TRACE_API_PATH = '/otp/debug/astar-trace';

export function getTraceApiUrl(path: string): string {
  const styleUrl = import.meta.env.VITE_DEBUG_STYLE_URL as string;
  try {
    const base = new URL(styleUrl);
    return `${base.origin}${TRACE_API_PATH}${path}`;
  } catch {
    return `${window.location.origin}${TRACE_API_PATH}${path}`;
  }
}

export function reloadTraceSource(mapRef: MapRef) {
  const map = mapRef.getMap();
  const source = map.getSource('astarTrace');
  if (!source) return;

  // Remove all trace layers, remove the source, re-add both.
  // This is the only reliable way to force a tile reload.
  const style = map.getStyle();
  if (!style) return;
  const traceLayers = style.layers.filter((l): l is LayerSpecification & { source: string } => 'source' in l && l.source === 'astarTrace');
  const layerConfigs = traceLayers.map((l) => ({ ...l }));
  const sourceConfig = { ...style.sources['astarTrace'] };

  for (const l of traceLayers) {
    map.removeLayer(l.id);
  }
  map.removeSource('astarTrace');
  map.addSource('astarTrace', sourceConfig);
  for (const l of layerConfigs) {
    map.addLayer(l);
  }
}

export function TraceSelector({
  activeTraceId,
  onSelectTrace,
}: {
  activeTraceId: string | null;
  onSelectTrace: (traceId: string | null) => void;
}) {
  const [traces, setTraces] = useState<TraceSummary[]>([]);

  const fetchTraces = useCallback(async () => {
    try {
      const response = await fetch(getTraceApiUrl('/'));
      if (!response.ok) return;
      const data: TraceSummary[] = await response.json();
      setTraces(data);
    } catch {
      // endpoint not available
    }
  }, []);

  useEffect(() => {
    fetchTraces();
  }, [fetchTraces]);

  // Refresh list when active trace changes externally (auto-load from search)
  useEffect(() => {
    fetchTraces();
  }, [activeTraceId, fetchTraces]);

  if (traces.length === 0) {
    return null;
  }

  return (
    <div style={{ padding: '0 10px 10px', borderBottom: '1px solid #ddd', marginBottom: '10px' }}>
      <h4 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
        A* Trace
        <button
          onClick={fetchTraces}
          style={{
            background: 'none',
            border: '1px solid #ccc',
            borderRadius: '3px',
            cursor: 'pointer',
            fontSize: '12px',
            padding: '2px 6px',
          }}
          title="Refresh trace list"
        >
          &#x21bb;
        </button>
      </h4>
      <select
        value={activeTraceId ?? ''}
        onChange={(e) => onSelectTrace(e.target.value || null)}
        style={{ width: '100%', marginBottom: '4px' }}
      >
        <option value="">— select trace —</option>
        {traces.map((t) => (
          <option key={t.id} value={t.id}>
            {t.description} ({t.eventCount} events)
          </option>
        ))}
      </select>
    </div>
  );
}
