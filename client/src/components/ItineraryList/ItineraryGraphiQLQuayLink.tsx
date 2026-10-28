import { quayQueryAsString } from '../../static/query/quayQuery.tsx';
import { Maybe } from '../../gql/graphql.ts';
import { getConfig } from '../../util/config.ts';

export function ItineraryGraphiQLQuayLink({
  legId,
  legName,
}: {
  legId: string | undefined;
  legName: Maybe<string> | undefined;
}) {
  const graphiQLUrl = getConfig().graphiqlUrl;
  if (!graphiQLUrl) {
    return <>{legName}</>;
  }

  const queryID = { id: legId };
  const formattedQuery = encodeURIComponent(quayQueryAsString);
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
