import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getConfig, loadRuntimeConfig, rewriteDebugTileRequest } from './config.ts';

function mockConfigFile(body: unknown, ok = true) {
  const fetchMock = vi.fn().mockResolvedValue({ ok, json: async () => body });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('loadRuntimeConfig', () => {
  beforeEach(async () => {
    mockConfigFile({}, false);
    await loadRuntimeConfig();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('overrides the build-time values with the known string values from config.json', async () => {
    const defaults = { ...getConfig() };
    mockConfigFile({ apiUrl: '/gateway/graphql', graphiqlUrl: '', debugTilesPathPrefix: '/gateway', unknown: 'x' });
    await loadRuntimeConfig();
    expect(getConfig()).toEqual({
      ...defaults,
      apiUrl: '/gateway/graphql',
      graphiqlUrl: '',
      debugTilesPathPrefix: '/gateway',
    });
  });

  it('resolves config.json relative to the page, not the build base', async () => {
    const fetchMock = mockConfigFile({});
    await loadRuntimeConfig();
    expect(fetchMock.mock.calls[0][0].toString()).toBe(new URL('config.json', document.baseURI).toString());
  });

  it('keeps the defaults when the file is missing', async () => {
    const defaults = { ...getConfig() };
    mockConfigFile({ apiUrl: '/gateway/graphql' }, false);
    await loadRuntimeConfig();
    expect(getConfig()).toEqual(defaults);
  });

  it('keeps the defaults when the response is not JSON or the request fails', async () => {
    const defaults = { ...getConfig() };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => JSON.parse('<html>') }));
    await loadRuntimeConfig();
    expect(getConfig()).toEqual(defaults);

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')));
    await loadRuntimeConfig();
    expect(getConfig()).toEqual(defaults);
  });

  it('ignores values that are not strings', async () => {
    const defaults = { ...getConfig() };
    mockConfigFile({ apiUrl: 42, debugStyleUrl: null });
    await loadRuntimeConfig();
    expect(getConfig()).toEqual(defaults);
  });
});

describe('rewriteDebugTileRequest', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function withPrefix(prefix: string | undefined) {
    mockConfigFile(prefix === undefined ? {} : { debugTilesPathPrefix: prefix });
    await loadRuntimeConfig();
  }

  it('does nothing without a prefix', async () => {
    await withPrefix(undefined);
    expect(rewriteDebugTileRequest('https://backend.example/otp/debug/vectortiles/edges/1/2/3.pbf')).toBeUndefined();
  });

  it('moves debug tile requests, whatever host the server put in the URL, to the page origin', async () => {
    await withPrefix('/gateway/debug');
    expect(rewriteDebugTileRequest('https://backend.example/otp/debug/vectortiles/a,b/1/2/3.pbf?x=1')).toEqual({
      url: `${window.location.origin}/gateway/debug/otp/debug/vectortiles/a,b/1/2/3.pbf?x=1`,
    });
  });

  it('leaves other requests alone, including ones that are already prefixed', async () => {
    await withPrefix('/gateway/debug');
    expect(rewriteDebugTileRequest('https://a.tile.openstreetmap.org/1/2/3.png')).toBeUndefined();
    expect(rewriteDebugTileRequest('/gateway/debug/otp/debug/vectortiles/style.json')).toBeUndefined();
  });
});
