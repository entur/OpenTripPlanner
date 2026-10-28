/**
 * Client configuration.
 *
 * The defaults come from the build-time `VITE_*` variables. Since the client is served from a CDN
 * and built only once, a deployment can override them at runtime by serving a `config.json`
 * next to the page that loads the client, e.g. when the client is hosted behind an API gateway
 * under a different path than the one baked into the build.
 */
export type ClientConfig = {
  /** GraphQL endpoint. A path without host is resolved against the page origin. */
  apiUrl?: string;
  /** URL of the MapLibre style used for the debug layers. */
  debugStyleUrl?: string;
  /** GraphiQL URL for the "open in GraphiQL" links. Empty or missing disables them. */
  graphiqlUrl?: string;
  /**
   * Path prefix put in front of vector tile requests (`/otp/debug/vectortiles/...`), replacing
   * the host the server put in the tile URLs. Needed when a proxy exposes the server under a
   * prefix, because the server only knows its own host and root path.
   */
  debugTilesPathPrefix?: string;
};

const CONFIG_FILE = 'config.json';
const LOAD_TIMEOUT_MS = 2000;
const DEBUG_TILES_PATH = '/otp/debug/vectortiles/';

const CONFIG_KEYS: (keyof ClientConfig)[] = ['apiUrl', 'debugStyleUrl', 'graphiqlUrl', 'debugTilesPathPrefix'];

function buildTimeConfig(): ClientConfig {
  return {
    apiUrl: import.meta.env.VITE_API_URL,
    debugStyleUrl: import.meta.env.VITE_DEBUG_STYLE_URL,
    graphiqlUrl: import.meta.env.VITE_GRAPHIQL_URL,
  };
}

let config: ClientConfig = buildTimeConfig();

export const getConfig = (): ClientConfig => config;

/** Keep only the known keys with string values, so a bad config file cannot break the client. */
function parseOverrides(json: unknown): ClientConfig {
  const overrides: ClientConfig = {};
  if (typeof json === 'object' && json !== null) {
    const record = json as Record<string, unknown>;
    for (const key of CONFIG_KEYS) {
      if (typeof record[key] === 'string') {
        overrides[key] = record[key];
      }
    }
  }
  return overrides;
}

/**
 * Fetch `config.json` relative to the page (not to the build's base URL, which points to the CDN)
 * and apply it on top of the build-time defaults. Never throws: a missing, slow or invalid file
 * leaves the defaults in place.
 */
export async function loadRuntimeConfig(): Promise<void> {
  config = buildTimeConfig();
  try {
    const response = await fetch(new URL(CONFIG_FILE, document.baseURI), {
      cache: 'no-store',
      signal: AbortSignal.timeout(LOAD_TIMEOUT_MS),
    });
    if (response.ok) {
      config = { ...config, ...parseOverrides(await response.json()) };
    }
  } catch (_) {
    // no usable runtime config, e.g. the dev server answers with index.html: keep the defaults
  }
}

/**
 * MapLibre `transformRequest` hook: send the debug vector tile requests through the configured
 * path prefix on the page origin.
 */
export function rewriteDebugTileRequest(url: string): { url: string } | undefined {
  const prefix = getConfig().debugTilesPathPrefix;
  if (!prefix) {
    return undefined;
  }
  try {
    const parsed = new URL(url, window.location.href);
    if (parsed.pathname.startsWith(DEBUG_TILES_PATH)) {
      return { url: `${window.location.origin}${prefix}${parsed.pathname}${parsed.search}` };
    }
  } catch (_) {
    // not a parsable URL, leave it to MapLibre
  }
  return undefined;
}
