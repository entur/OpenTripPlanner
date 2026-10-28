import { getConfig } from './config.ts';

export const getApiUrl = () => {
  const endpoint = getConfig().apiUrl ?? '';
  try {
    // the URL constructor will throw exception if endpoint is not a valid URL,
    // e.g. if it is a relative path
    new URL(endpoint);
    return endpoint;
  } catch (_) {
    return `${window.location.origin}${endpoint}`;
  }
};
