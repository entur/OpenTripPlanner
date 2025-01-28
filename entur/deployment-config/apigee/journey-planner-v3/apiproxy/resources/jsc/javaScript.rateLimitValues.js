var etClientName = context.getVariable("request.header.et-client-name")
var etClientType = context.getVariable("request.header.et-client-type")


if (queryTypeIsTrip(etClientType)) {
  var nsb = { quota: "10000", spikeArrest: "1000ps" };
  var vyItineraryHighPriority = { quota: "14000", spikeArrest: "800ps" };
  var vyItineraryMediumPriority = { quota: "5000", spikeArrest: "600ps" };
  var ruter = { quota: "15000", spikeArrest: "1000ps" };
  var entur = { quota: "4000", spikeArrest: "400ps" };
  var kolumbus = { quota: "1500", spikeArrest: "150ps" };
  var atbBff = { quota: "2000", spikeArrest: "200ps" };
  var skyss = { quota: "3000", spikeArrest: "150ps" };
  var sjNord = { quota: "4000", spikeArrest: "200ps" };
  var nfkBff = { quota: "1000", spikeArrest: "300ps" };
  var framBff = { quota: "1000", spikeArrest: "300ps" };
  var others = { quota: "500", spikeArrest: "150ps" };
  var noIdUser = { quota: "30", spikeArrest: "2ps" };
  var queryType = 'trip';
} else {
  var nsb = { quota: "15000", spikeArrest: "800ps" };
  var vyItineraryHighPriority = { quota: "8000", spikeArrest: "600ps" };
  var vyItineraryMediumPriority = { quota: "8000", spikeArrest: "600ps" };
  var ruter = { quota: "15000", spikeArrest: "1500ps" };
  var entur = { quota: "5000", spikeArrest: "500ps" };
  var kolumbus = { quota: "3000", spikeArrest: "300ps" };
  var atbBff = { quota: "8000", spikeArrest: "500ps" };
  var skyss = { quota: "3000", spikeArrest: "200ps" };
  var sjNord = { quota: "2000", spikeArrest: "200ps" };
  var nfkBff = { quota: "2000", spikeArrest: "400ps" };
  var framBff = { quota: "2000", spikeArrest: "400ps" };
  var others = { quota: "1000", spikeArrest: "200ps" };
  var noIdUser = { quota: "60", spikeArrest: "20ps" };
  var queryType = 'notTrip';
}


if (etClientName) { // if client identified
  limitValues = allowedQuota(etClientName, nsb, vyItineraryHighPriority, vyItineraryMediumPriority, ruter, skyss, entur, kolumbus, atbBff, sjNord, nfkBff, framBff, others);
  quotaAllowed = limitValues.quotaAllowed;
  spikeArrestAllowed = limitValues.spikeArrestAllowed;
} else { // if not identified -> rate on ip
  etClientName = "unkown-" + context.getVariable("client.ip")
  quotaAllowed = noIdUser.quota;
  spikeArrestAllowed = noIdUser.spikeArrest;
  // Add et-client-name with ip
  context.setVariable("request.header.Et-Client-Name", etClientName);
}

// Set quota and spike identifier
var identifier = etClientName + '-' + queryType;
context.setVariable("quota.client.identifier", identifier);
context.setVariable("spikeArrest.client.identifier", identifier);

// et quota and spike limits
context.setVariable("quota.client.allowed", quotaAllowed);
context.setVariable("spikeArrest.client.allowed", spikeArrestAllowed);


// Check if client type is trip
function queryTypeIsTrip(etClientType) {
  if (etClientType == "trip") {
    return true;
  } else {
    return false;
  }
}

// Get quota and spikearrest values
function allowedQuota(etClientName, nsb, vyItineraryHighPriority, vyItineraryMediumPriority, ruter, skyss, entur, kolumbus, atbBff, sjNord,nfkBff, framBff, others) {
  if (etClientName.includes("nsb")) {  // if nsb
    quotaAllowed = nsb.quota;
    spikeArrestAllowed = nsb.spikeArrest;
  } else if (etClientName.includes("vy-itinerary-high-priority")) { // if vy-itinerary-high-priorit
    quotaAllowed = vyItineraryHighPriority.quota;
    spikeArrestAllowed = vyItineraryHighPriority.spikeArrest;
  } else if (etClientName.includes("vy-itinerary-medium-priority")) { // if vy-itinerary-medium-priorit
    quotaAllowed = vyItineraryMediumPriority.quota;
    spikeArrestAllowed = vyItineraryMediumPriority.spikeArrest;
  } else if (etClientName.includes("ruter")) { // if ruter
    quotaAllowed = ruter.quota;
    spikeArrestAllowed = ruter.spikeArrest;
  } else if (etClientName.includes("skyss")) { // if skyss 
    quotaAllowed = skyss.quota;
    spikeArrestAllowed = skyss.spikeArrest;
  } else if (etClientName.includes("entur")) { // if entur 
    quotaAllowed = entur.quota;
    spikeArrestAllowed = entur.spikeArrest;
  } else if (etClientName.includes("kolumbus-reisevenn")) { // if kolumbus-reisevenn
    quotaAllowed = kolumbus.quota;
    spikeArrestAllowed = kolumbus.spikeArrest;
  } else if (etClientName.includes("atb-bff")) { // if atb
    quotaAllowed = atbBff.quota;
    spikeArrestAllowed = atbBff.spikeArrest;
  }  else if (etClientName.includes("sj-nord")) { // if sj-nord
     quotaAllowed = sjNord.quota;
     spikeArrestAllowed = sjNord.spikeArrest;
  }  else if (etClientName.includes("nfk-bff")) { // if nfk-bff
    quotaAllowed = nfkBff.quota;
    spikeArrestAllowed = nfkBff.spikeArrest;
  }  else if (etClientName.includes("fram-nord")) { // if fram-nord
    quotaAllowed = framBff.quota;
    spikeArrestAllowed = framBff.spikeArrest;
  } else { // if other
     quotaAllowed = others.quota;
     spikeArrestAllowed = others.spikeArrest;
  }
  return {
    quotaAllowed: quotaAllowed,
    spikeArrestAllowed: spikeArrestAllowed
  };
}