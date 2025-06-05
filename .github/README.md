## Overview

This is a Entur's fork of the [GitHub OpenTripPlanner project](https://github.com/opentripplanner/OpenTripPlanner). 
This repository only contains a minimum set of changes for the Entur deployment at Entur. Most
of the Entur specific content is just OTP and deployment configuration. We use the code from
the parent `dev-2.x` branch as is.

## Entur OTP continuous integration and deployment 

Here is an introduction to the Entur CI and CD process. For details see the [release script](/script/custom-release.py) and the 
[release documentation readme](/script/Readme.md). The release script is part of the upstream project, so the doc is generic,
not Entur specific.

- The `main` branch contains all [tagged releases](https://github.com/opentripplanner/OpenTripPlanner/tags), 
  like `v2.8.0-entur-100`. All releases are "stand-alone" synthetic releases. Nothing in the `main` 
  branch is kept or makes it into the next release, unless it is a hotfix release. Usually the
  upstream `dev-2.x` is used as a base for a release. The `main` branch is reset before a new
  release is made. A continuous line of releases is created by merging in the previous release 
  with an _empty_ merge. Nothing from the previous release makes it into the release, but a 
  reference to it is kept in the git ancestor tree. 
 
 > **Note!** Do not commit "permanent changes" to the `main` branch, the next release will reset 
 >           the main branch and your commit will be lost.
  
- The `main_config` branch contains the Entur specific config. This branch is merged into the 
   `main` branch before a release is made. All Entur specific files are stored in the `/entur`
   folder. The [custom release extension script](/script/custom-release-extension) will move it to
   the right place before the release is made. The Entur specific GitHub workflows files are in the
   [/.github/workflows](/.github/workflows) folder, not in the _entur_ folder. The reason for this 
   is that the GitHub Action Bot does not have rights to move or change these files for security 
   reasons. GitHub action workflow scripts from the upstream project are deleted. Keeping the Entur
   config in one place is done to keep our stuff separate from the upstream project and avoid merge
   conflicts.  

> **Note!** To make changes to the config, checkout the `main_config` branch. Change the config, 
>           commit and push. The changes will make it into the next release (build nightly) or you 
>           may trigger a new build manually by running the [Release new OTP version](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml)
>           workflow. 


## Links
- [OTP Build Application](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml) (GitHub Workflow)
- [OTP Build Docker image](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-b-docker-build.yml) (GitHub Workflow)
- OTP Debug UI [DEV](https://otp2debug.dev.entur.org/) [STAGING](https://otp2debug.staging.entur.org/) [PROD](https://otp2debug.entur.org/)
- OTP Built-in GraphQL Client [DEV](https://otp2debug.dev.entur.org/graphiql?flavor=transmodel) [STAGING](https://otp2debug.staging.entur.org/graphiql?flavor=transmodel) [PROD](https://otp2debug.entur.org/graphiql?flavor=transmodel)
- [OTP GraphQL API](https://api.staging.entur.io/graphql-explorer/journey-planner-v3) (Shamash)
- [OTP Planning request performance in Grafana](https://grafana.entur.org/d/X1pi-Jxnz/otp-apis-performance-operations?orgId=1)
