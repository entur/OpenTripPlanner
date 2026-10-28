import { lineQueryAsString } from '../../static/query/lineQuery.tsx';
import { getConfig } from '../../util/config.ts';

export function ItineraryGraphiQLLineLink({ legId, legName }: { legId: string; legName: string }) {
  const graphiQLUrl = getConfig().graphiqlUrl;
  if (!graphiQLUrl) {
    return <>{legName}</>;
  }

  const queryID = { id: legId };
  const formattedQuery = encodeURIComponent(lineQueryAsString);
  const formattedQueryID = encodeURIComponent(JSON.stringify(queryID));

  return (
    <a
      href={graphiQLUrl + '&query=' + formattedQuery + '&variables=' + formattedQueryID}
      target={'_blank'}
      rel={'noreferrer'}
    >
      {legName}
    </a>
  );
}
