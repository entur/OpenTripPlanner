# Sørlandsbanen - The southern railroad in Norway 

**This sandbox module is only working in Norway**, in particular only in the south of Norway. The
feature flag to turn it *on* should only be enabled if you are routing using the norwegian data set. 

The railroad in southern Norway is very slow and does not go by the cost where most people live. It
is easily beaten by coaches in the area. Despite this, we need to include it in results where it is
relevant.

When the feature flag is enabled, two Raptor searches are performed. The first is the regular 
search - unmodified, as requested by the user. The second search is modified to include train 
results with Sørlandsbanen. This is achieved by setting a high COACH reluctance. We then take any 
rail results(if they exist) from the second search and add it two to the results from the first 
search. The new set of results will contain everything we found in the first search, plus the train 
results in the second results.

Note! This is a hack and the logic to enable this look at the origin and destination coordinates 
in addition to the feature flag.


## Contact Info

- Entur, Norway

## Changelog

- 2024-10-14: We have used this feature for som time, but now want it in the Sandbox so we do not 
              need to merge it everytime we create a new entur release.


### Configuration

This is turned _off_ by default. To turn it on enable the `Sorlandsbanen` feature.

```json
// otp-config.json
{
  "otpFeatures": {
    "Sorlandsbanen": true
  }
}
```


