import { authorityQueryAsString } from '../../static/query/authorityQuery.tsx';
import { Maybe } from '../../gql/graphql.ts';
import { getConfig } from '../../util/config.ts';

export function ItineraryGraphiQLAuthorityLink({
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
  const formattedQuery = encodeURIComponent(authorityQueryAsString);
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
