var allowedQuota = "unknown"
var usedQuota = "unknown"
var availableQuota = "unknown"
var rangeQuota = "per-minute"
var expiryTimeQuota = "unkown"

allowedQuota = context.getVariable("ratelimit.quota.allowed.count");
usedQuota = context.getVariable("ratelimit.quota.used.count");
availableQuota = context.getVariable("ratelimit.quota.available.count");
expiryTimeQuota = new Date(context.getVariable("ratelimit.quota.expiry.time"));

context.setVariable("quota.allowed", allowedQuota);
context.setVariable("quota.used", usedQuota);
context.setVariable("quota.available", availableQuota);
context.setVariable("quota.range", rangeQuota);
context.setVariable("quota.expiryTime", expiryTimeQuota.toString());


var allowedSpikeArrest = "unkown"
var rangeSpikeArrest = "per-second"

allowedSpikeArrest = context.getVariable("ratelimit.spikeArrest.allowed.count");

context.setVariable("spikeArrest.allowed", allowedSpikeArrest);
context.setVariable("spikeArrest.range", rangeSpikeArrest);

