import { bikeRentalStationQueryAsString } from '../../static/query/bikeRentalStationQuery.tsx';
import { Maybe } from '../../gql/graphql.ts';
import { getConfig } from '../../util/config.ts';

export function ItineraryGraphiQLBikeRentalStationLink({
  stationId,
  legName,
}: {
  stationId: string | undefined;
  legName: Maybe<string> | undefined;
}) {
  const graphiQLUrl = getConfig().graphiqlUrl;
  if (!graphiQLUrl) {
    return <>{legName}</>;
  }

  const queryID = { id: stationId };
  const formattedQuery = encodeURIComponent(bikeRentalStationQueryAsString);
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
