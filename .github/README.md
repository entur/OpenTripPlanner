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
<p align="center">
  <b>
    OTP Build Report ・ 
    <a href="http://otpreport.dev.entur.org">DEV</a> ・
    <a href="http://otpreport.staging.entur.org">TEST</a> ・
    <a href="http://otpreport.entur.org">PROD</a>
  </b>
</p>

This is Entur's fork of the [GitHub OpenTripPlanner project](https://github.com/opentripplanner/OpenTripPlanner).
This repository contains only a minimal set of changes for the Entur deployment. Most of the 
Entur-specific content is related to continuous integration and deployment configuration. We use
the code from the `dev-2.x` branch in the upstream repository as is.

> ✏️ &nbsp;**Tip!**  Edit this file in the **_main_config_** branch.
 

# Entur OTP Continuous Integration and Deployment Overview

Here is an introduction to the Entur CI and CD process. For details, see the [release script](/script/custom-release.py)
and the [release documentation readme](/script/CUSTOM_RELEASE_README.md). The release script is part of the upstream project, 
so the documentation is generic, not Entur-specific.

## The Entur Release Branch

The `main` branch contains all [tagged releases](https://github.com/opentripplanner/OpenTripPlanner/tags), 
like `v2.9.0-entur-157`. All releases are "stand-alone" synthetic releases. **Nothing in the `main` 
branch is retained** or carried over to the next release unless it is a hotfix release. Usually,
the upstream `dev-2.x` branch is used as a base for a release. The `main` branch is reset before a
new release is made. A continuous line of releases is created by merging in the previous release
with an _empty_ merge. Nothing from the previous release is included in the new release, but a
reference to it is kept in the Git ancestor tree. 
 
 > **Note!** Do not commit "permanent changes" to the `main` branch. The next release will reset 
 >           the `main` branch, and your commit will be lost.


## The Entur Configuration  
  
The `main_config` branch contains the Entur-specific configuration. This branch is merged into the
`main` branch before a release is made. Most of the Entur-specific files are stored in the `/entur`
folder. The [custom release extension script](/script/custom-release-extension) moves these files
to the correct location before the release is made. The Entur-specific GitHub workflows are in the 
[/.github/workflows](/.github/workflows) folder, not in the _entur_ folder. This is because the 
GitHub Action Bot does not have rights to move or change these files for security reasons. GitHub
action workflow scripts from the upstream project are deleted. Keeping the Entur configuration in
one place helps avoid merge conflicts and keeps our changes separate from the upstream project.


### How to Change the OTP Configuration
1. Check out the `main_config` branch. Change the configuration, commit, and push the changes.
   These changes will be included in the next release (built nightly) or you may trigger a new
   build manually.
2. To trigger the build, run [Release new OTP version 🛠️](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml). 
   The entire Entur OTP release pipeline will run.


## Pending Pull Requests

PRs labeled with [`Entur Test`](https://github.com/opentripplanner/OpenTripPlanner/pulls?q=is%3Aopen+is%3Apr+label%3A%22Entur+Test%22)
in the OpenTripPlanner GitHub repository are merged into the release by the release script. This
allows us to test features at Entur before the PR is accepted and merged upstream. To 
include/exclude a PR from the next release, add/remove the `Entur Test` label.

## How to Make a Release 🛠️ or Hotfix 🌶️

Instructions for making releases and hotfixes are described in the [release documentation readme](/script/CUSTOM_RELEASE_README.md).
At Entur, we use the [GitHub Workflow](https://github.com/entur/OpenTripPlanner/actions) to build
new releases - both hotfixes and regular releases. See [Release new OTP version 🛠️](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml). 
A build is scheduled every night, and it can also be triggered manually to start the OTP CI/CD pipeline.

### How to Make a Regular Release 🛠️
1. Go to the [Release new OTP version 🛠️](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml) action.
2. Click `Run workflow`.

### How to Make a Hotfix 🌶️
1. First, prepare the `main` branch in the Entur GitHub repository. Reset the branch to the 
   version you want to use as a base: 
```bash
git reset --hard v2.9.0-entur-157
```
2. Merge in the configuration branch if the configuration has changed (and you want the changes):
```bash
git merge main_config
```
3. Make any other modifications you need.
4. Go to the [Release new OTP version 🛠️](https://github.com/entur/OpenTripPlanner/actions/workflows/entur-a-otp-release.yml) action.
5. Select: **[x] Force bumping ser.ver.id** - if you are not using the same serialization version ID as the base version.
6. Select: **[x] 🌶️ Hotfix!**
7. Click `Run workflow`.

> **Note!** You must run the workflow on the `main` branch. Using another branch will fail.
> 

### Build New Releases on Your Local Machine

> **Note!** If you run the release script on your local machine, the release notes will not be available on GitHub.
>

If you need to change the release base commit or make a hotfix, you can run the release script on
your local machine. After the new OTP application release is pushed to GitHub, the OTP CI/CD
pipeline will automatically pick up the new release, build a new Docker image, and deploy the new
version to the development environment.

First, configure the Git remote repositories:

    `otp` : https://github.com/opentripplanner/OpenTripPlanner.git
    `entur` : https://github.com/entur/OpenTripPlanner.git

Use `git remote rename origin entur` to set the names.


# Release Pipeline

## Overview

1. [GitHub Actions](https://github.com/entur/OpenTripPlanner/actions) is used to make the release 
   and build the Docker image. The configuration is included in the application release.
2. The next step is to roll out the Docker image. The same image is used for both:
   a. OTP GraphBuilder 
   b. OTP JourneyPlanner 
3. There is also a data pipeline that is triggered when new transit data is available.

GraphBuilder is used to build the _streetGraph_ (baseGraph) and the _transitGraph_. The 
_streetGraph_ is built nightly from new OpenStreetMap data, while the _transitGraph_ reads in the
_streetGraph_ and adds transit data from NeTEx files on top of it. This happens when new NeTEx data
is available. When the _transitGraph_ build is complete, the graph becomes available to the OTP
JourneyPlanner in the same environment. The JourneyPlanner instances in the environment then need
to be restarted to pick up the new _transitGraph_.

OTP has a _serialization-version-id_ (SID). Graphs with different SIDs cannot be read by another
version of OTP. This applies to building the transitGraph (dependent on the streetGraph) and to 
JourneyPlanner (reading the transitGraph). Two versions of OTP may have the same serialization-version-id.
When rolling out a new version of OTP, the JourneyPlanner will fail to deploy if a graph does not 
exist. The GraphBuilder will fail to build a _transitGraph_. To fix this issue, a _streetGraph_ 
must be built, then a _transitGraph_, and then JourneyPlanner can be deployed. This applies to all 
environments.

It takes about 1 hour to build the streetGraph and 15 minutes to build the transitGraph. There are
two ways to deploy a new version of OTP in an environment - depending on the 
serialization-version-id. 


## How to Roll Out OTP with THE SAME Serialization-Version-ID as the Current Running Version

A new Docker image is automatically rolled out in the DEV environment. Both GraphBuilder and 
JourneyPlanner are rolled out - no manual steps are needed. To roll out in TEST and PROD, all you
need to do is approve the promotion to the next environment. Remember to check if the target 
environment contains both streetGraph and transitGraph for the given SID.


## How to Roll Out OTP with a New _Serialization-Version-ID_

This is a bit more complicated. The reason is that new graphs need to be built before we can roll
out JourneyPlanner. In DEV, the rollout will fail on the JourneyPlanner because JourneyPlanner is
not able to read the _transitGraph_. You can use Ninkasi to build the graphs (first _streetGraph_,
and then _transitGraph_). 


### To Roll Out in DEV

In DEV, you may use the regular GraphBuilder (not candidate) to do this - it is a little simpler,
and it has no side effects.


### To Roll Out in TEST and PROD

To avoid failure in the data pipeline and failure when rolling out JourneyPlanner, you need to
build graphs BEFORE you promote OTP from one environment to the next (DEV to TEST, and TEST to
PROD). The first step is to promote the _Candidate GraphBuilder_ (the deployment is named
otp2-gb-rc in Harness). Then use Ninkasi to build a candidate _streetGraph_ and a candidate
_transitGraph_. When both graphs are built successfully, you can roll out GraphBuilder and
JourneyPlanner.

- [OTP Planning Request Performance in Grafana](https://grafana.entur.org/d/X1pi-Jxnz/otp-apis-performance-operations?orgId=1)
