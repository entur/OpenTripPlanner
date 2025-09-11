<h1 align="center">
  <img src="/doc/user/images/otp-logo.svg" width="120" /><br>
  Open Trip Planner (OTP) - Entur Fork
</h1>
<p align="center">
  <a href="https://otp2debug.dev.entur.org/">  
    <img src="http://otp2debug.dev.entur.org/otp/version-badge.svg?label=DEV&color=limegreen&l=0" alt="OTP Version DEV">
  </a> &nbsp;
  <a href="https://otp2debug.staging.entur.org/">
    <img src="http://otp2debug.staging.entur.org/otp/version-badge.svg?label=TEST&color=orange&l=0" alt="OTP Version TEST"/>
  </a> &nbsp;
  <a href="https://otp2debug.entur.org/">
    <img src="http://otp2debug.entur.org/otp/version-badge.svg?label=PROD&color=crimson" alt="OTP Version PROD"/>
  </a>
</p>
<p align="center">
  <a href="https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml"><img src="https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml/badge.svg" alt="Release new OTP version 🛠️"/></a> &nbsp;
  <a href="https://github.com/entur/OpenTripPlanner/actions/workflows/entur-b-docker-build.yml"><img src="https://github.com/entur/OpenTripPlanner/actions/workflows/entur-b-docker-build.yml/badge.svg" alt="Build and push docker image 🎁"/></a>
</p>
<p align="center">
  <b>
    OTP Debug ・ 
    <a href="https://otp2debug.dev.entur.org/">DEV</a> ・
    <a href="https://otp2debug.staging.entur.org/">TEST</a> ・
    <a href="https://otp2debug.entur.org/">PROD</a>
  </b>
</p>
<p align="center">
  <b>
    OTP GraphQL Explorer ・ 
    <a href="https://otp2debug.dev.entur.org/graphiql?flavor=transmodel">DEV</a> ・
    <a href="https://otp2debug.staging.entur.org/graphiql?flavor=transmodel">TEST</a> ・
    <a href="https://otp2debug.entur.org/graphiql?flavor=transmodel">PROD</a>
  </b>
</p>
<p align="center">
  <b><a href="https://api.staging.entur.io/graphql-explorer/journey-planner-v3">Entur GraphiQL (Shamash)</a></b>
</p>

This is a Entur's fork of the [GitHub OpenTripPlanner project](https://github.com/opentripplanner/OpenTripPlanner).
This repository only contains a minimum set of changes for the Entur deployment at Entur. Most
of the Entur specific content is just continuous integration and deployment configuration. We use
the code from the `dev-2.x` branch in the upstream repository as is.

> ✏️ &nbsp;**Tip!**  Edit this file in the **_main_config_** branch.
 

# Entur OTP continuous integration and deployment overview

Here is an introduction to the Entur CI and CD process. For details see the [release script](/script/custom-release.py)
and the [release documentation readme](/script/CUSTOM_RELEASE_README.md). The release script is part
of the upstream project, so the doc is generic, not Entur specific.

## The Entur Release branch

The `main` branch contains all [tagged releases](https://github.com/opentripplanner/OpenTripPlanner/tags), 
like `v2.8.0-entur-100`. All releases are "stand-alone" synthetic releases. Nothing in the `main` 
branch is kept or makes it into the next release, unless it is a hotfix release. Usually the 
upstream `dev-2.x` is used as a base for a release. The `main` branch is reset before a new release
is made. A continuous line of releases is created by merging in the previous release with an 
_empty_ merge. Nothing from the previous release makes it into the release, but a reference to it
is kept in the git ancestor tree. 
 
 > **Note!** Do not commit "permanent changes" to the `main` branch, the next release will reset 
 >           the main branch and your commit will be lost.


## The Entur configuration  
  
The `main_config` branch contains the Entur specific config. This branch is merged into the `main`
branch before a release is made. All Entur specific files are stored in the `/entur` folder. The
[custom release extension script](/script/custom-release-extension) will move it to the right place
before the release is made. The Entur specific GitHub workflows files are in the 
[/.github/workflows](/.github/workflows) folder, not in the _entur_ folder. The reason for this is
that the GitHub Action Bot does not have rights to move or change these files for security reasons.
GitHub action workflow scripts from the upstream project are deleted. Keeping the Entur config in
one place is done to keep our stuff separate from the upstream project and avoid merge conflicts.

> **Note!** To make changes to the config, checkout the `main_config` branch. Change the config, 
>           commit and push. The changes will make it into the next release (build nightly) or you 
>           may trigger a new build manually by running the [Release new OTP version](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml)
>           workflow. 


## Pending Pull Requests

PRs labeled with [`Entur Test`](https://github.com/opentripplanner/OpenTripPlanner/pulls?q=is%3Aopen+is%3Apr+label%3A%22Entur+Test%22)
in the OpenTripPlanner GitHub repo is merged into the release by the release script. This allows us
to test features at Entur before the PR is accepted and merged. To include/exclude a PR from the 
next release add/remove the `Entur Test` label.

## How-to make a release and make hotfixes

How to release and make hot fixes is described in the [release documentation readme](/script/CUSTOM_RELEASE_README.md).
At Entur we have set up a [GitHub Workflow](https://github.com/entur/OpenTripPlanner/actions) to 
build a new release every night. The
<a href="https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml"><img src="https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml/badge.svg" alt="Release new OTP version 🛠️"/></a>
can also be triggered mnually to start the OTP CI/DI pipline. 


### Build new releases on your local mashine

If you need change the release base commit or make a hotfix you can run the release script on your
local machine. After the new OTP application release is pushed to GitHub the OTP CI/CD pipline will
pick up the new release automatically and build a new docker image and deploy the new version to
the dev environment.

First you need to configure the git remote repositories:

    `otp` : https://github.com/opentripplanner/OpenTripPlanner.git
    `entur` : https://github.com/entur/OpenTripPlanner.git

Use `git remote rename origin entur` to sett the names.


# Release pipeline

## Overview

1. [GitHub Actions](https://github.com/entur/OpenTripPlanner/actions) is used to make the release 
   and and build the docker image. The configuration is included in the application release.
2. The next step is to roll out the docker image. The same image is used for both:
   a. OTP GraphBuilder 
   b. OTP JourneyPlanner 
3. There is also a data pipeline witch is triggered when new transit data is available.
4. Do a diff on the OTP docs/changelog.md and post relevant updates on Slack.

> Tip! Use the [changelog-diff.py](script/changelog-diff.py) script to compare the changelog 
>      for two OTP versions.

GraphBuilder is used to build the _streetGraph_(baseGraph) and the _transitGraph_. The 
_streetGraph_ is build nightly from new OpenStreetMap data, while the _transitGraph_ read in the
_streetGraph_ and add transit data from NeTEx files on top of it. This happens when new NeTEx data
is available. When the _transitGraph_ is build is complete, the graph become available to the OTP
JourneyPlanner in the same environment. The JourneyPlanner instances in the environment then need
to be restarted to pick up the new _transitGraph_.

OTP has a _serialization-version-id_(SID). Graphs with different SIDs can not be read by another
version of OTP. This apply to building transitGraph(depend on the streetGraph) and to 
JourneyPlaner(read transitGraph). Two versions of OTP may have the same serialization-version-id.
When rolling out a new version of OTP the JourneyPlanner will fail to deploy, if a graph does not 
exist. The GraphBuilder will fail to build a _transitGraph_. To fix this issue a _streetGraph_ must
be build, then a _transitGraph_, and then JourneyPlanner can be deploied. This apply to all 
environments.

It takes about 1 hour to build the streetGraph and 15 minutes to build the transitGraph. There are
two ways to deploy a new version of OTP in an environment - depending on if the 
serialization-version-id. 

## How-to change the OTP configuration
1. Check out the `main_conig` branch. Change the config, commit and puch changes.
2. Run the [![Release new OTP version 🛠️](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml/badge.svg)](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml) to start the OTP release pipline.


## How-to roll out OTP with THE SAME serialization-version-id as the current running version

New docker images is automatically rolled out in the DEV environment. Both GraphBuilder and 
JourneyPlanner is rolled out - no manual steps needed. To roll out in TEST and PROD all you need
to do, is to approve the promotion to the next environment.

## How-to roll out OTP with a new _serialization-version-id_

This is a bit more complicated. The reason is that new graphs need to be build before we can roll
out JourneyPlanner. In DEV the rollout will fail on the JourneyPlanner, because JournyPlanner is
not able to read the _transitGraph_. You can use Ninkasi to build the graphs (first 
_streetGraph_, and then _transitGraph_). In DEV you may use the regular GraphBuilder (not candidate)
to do this - it is a little simpler, and it has no side-effects.

### To roll out in TEST and PROD

To avoid failure in the data pipeline and failure when rolling out JourneyPlanner you need to build
graphs BEFORE you promote OTP from one environment to the next (DEV to TEST, and TEST to PROD). The
first step is to promote the _Candidate GraphBuilder_ (the deployment is named otp2-gb-rc in
Harness). Then use Ninkasi to build a candidate _streetGraph_ and a candidate _transitGraph_. When
both graphs are build successfully you can roll out GraphBuilder and JourneyPlanner.


- [OTP Planning request performance in Grafana](https://grafana.entur.org/d/X1pi-Jxnz/otp-apis-performance-operations?orgId=1)
