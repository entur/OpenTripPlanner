#!/usr/bin/env python3

import json
import os
import re
import subprocess
import sys


## ------------------------------------------------------------------------------------ ##
##                                 Global constants                                     ##
## ------------------------------------------------------------------------------------ ##

POM_FILE_NAME = 'pom.xml'
# GitHub label to indicate that a PR needs to be bumped
LBL_BUMP_SER_VER_ID = 'bump serialization id'
SER_VER_ID_PROPERTY = 'otp.serialization.version.id'
SER_VER_ID_PROPERTY_PTN = SER_VER_ID_PROPERTY.replace('.', r'\.')
SER_VER_ID_PATTERN = re.compile(
    '<' + SER_VER_ID_PROPERTY_PTN + r'>\s*(.*)\s*</' + SER_VER_ID_PROPERTY_PTN + '>')
STATE_FILE = '.custom_release_resume_state.json'


## ------------------------------------------------------------------------------------ ##
##                                      Classes                                         ##
## ------------------------------------------------------------------------------------ ##

# Configuration read from the 'release_env.json' file
class Config:
    def __init__(self):
        self.upstream_remote = None
        self.release_remote = None
        self.release_branch = None
        self.ext_branches = None
        self.include_prs_label = None
        self.ser_ver_id_prefix = None

    def release_path(self, branch):
        return f'{self.release_remote}/{branch}'

    def release_branch_path(self):
        return self.release_path(self.release_branch)

    def __str__(self):
        return (f"<"
                f"upstream_remote: '{self.upstream_remote}', "
                f"release_remote: '{self.release_remote}', "
                f"release_branch: '{self.release_branch}', "
                f"ext_branches: {self.ext_branches}, "
                f"include_prs_label: '{self.include_prs_label}', "
                f"ser_ver_id_prefix: '{self.ser_ver_id_prefix}'>")


# CLI Arguments and Options
class CliOptions:

    def __init__(self):
        self.base_revision = None
        self.dry_run = False
        self.debugging = False
        self.hotfix = False
        self.bump_ser_ver_id = False
        self.skip_prs = False

    def verify(self):
        if self.hotfix and self.base_revision:
            error("No arguments allowed with option '--hotfix'")

    # Return the script <base revision> argument if set, if not use 'HEAD'(--hotfix)
    def release_base(self):
        return self.base_revision if self.base_revision else 'HEAD'

    def __str__(self):
        return (f"<"
                f"base_revision: {self.base_revision}, "
                f"dry_run: {self.dry_run}, "
                f"debugging: {self.debugging}, "
                f"hotfix: '{self.hotfix}', "
                f"bump_ser_ver_id: '{self.bump_ser_ver_id}', "
                f"skip_prs: '{self.skip_prs}'>")


# The execution state of the script + the CLI arguments
class ScriptState:

    def __init__(self):
        self.current_ser_ver_id = None
        self.new_ser_ver_id = None
        self.major_version = None
        self.current_version = None
        self.new_version = None
        self.prs_to_merge = {}
        self.prs_bump_ser_ver_id = False
        self.gotoStep = False
        self.step = None

    def new_version_tag(self):
        return f'v{self.new_version}'

    def new_version_description(self):
        return f'Version {self.new_version} ({self.new_ser_ver_id})'

    def is_ser_ver_id_new(self):
        return self.new_ser_ver_id != self.current_ser_ver_id

    def run(self, step):
        if not self.gotoStep:
            debug(f'Run step: {step}')
            return True
        if self.step == step:
            self.gotoStep = False
            debug(f'Resume step: {step}')
            return True
        else:
            debug(f'Skip step: {step}')
            return False


## ------------------------------------------------------------------------------------ ##
##                                  Global Variables                                    ##
## ------------------------------------------------------------------------------------ ##

config: Config = Config()
options = CliOptions()
state = ScriptState()


## ------------------------------------------------------------------------------------ ##
##                                        Main                                          ##
## ------------------------------------------------------------------------------------ ##
def main():
    setup_and_verify()

    # Prepare release
    if not options.hotfix:
        reset_release_branch_to_base_revision()
        merge_in_labeled_prs()
        merge_in_ext_branches()
        merge_in_old_release_with_no_changes()

    run_maven_test()
    set_maven_pom_version()
    set_ser_ver_id()
    commit_new_versions()
    tag_release()
    push_release_branch_and_tag()


## ------------------------------------------------------------------------------------ ##
##                                 Top level functions                                  ##
## ------------------------------------------------------------------------------------ ##

def setup_and_verify():
    section('Setting up release process and verifying the environment')
    parse_and_verify_cli_arguments_and_options()
    if os.path.exists(STATE_FILE):
        resume()
    else:
        load_config()
        verify_script_run_from_root()
        verify_git_installed()
        verify_maven_installed()
        verify_release_base_and_release_branch_exist()
        # verify_no_local_git_changes()
        fetch_all_git_remotes()
        resolve_version_number()
        resolve_new_version()
        list_labeled_prs()
        resolve_new_ser_ver_id()
    print_setup()


def reset_release_branch_to_base_revision():
    section('Reset release branch to base revision ...')
    git_im('checkout', '-B', config.release_branch, options.base_revision)


def merge_in_labeled_prs():
    section('Merge in labeled PRs ...')
    for pr in state.prs_to_merge:
        # A temp branch is needed here since the PR is in the upstream remote repo
        temp_branch = f'temp-pullrequest-{pr}'
        if section_w_resume(temp_branch, f'Merge in PR #{pr}'):
            git('fetch', config.upstream_remote, f'pull/{pr}/head:{temp_branch}')
            git_im('merge', temp_branch)
            git('branch', '-D', temp_branch)


def merge_in_ext_branches():
    if len(config.ext_branches) == 0:
        info('\nNo config branch configured, mering is skipped.')
        return
    if section_w_resume('merge_in_ext_branches', 'Merge in config branch ...'):
        for branch in config.ext_branches:
            git_im('merge', config.release_path(branch))
            info(f'Config branch merged: {config.release_path(branch)}')


def set_maven_pom_version():
    section('Set Maven project version ...')
    execute('mvn', 'versions:set', f'-DnewVersion={state.new_version}',
            '-DgenerateBackupPoms=false')
    info(f'New version set: {state.new_version}')


def set_ser_ver_id():
    section('Set serialization.version.id ...')
    new_ser_ver_id_element = f'<{SER_VER_ID_PROPERTY}>{state.new_ser_ver_id}</{SER_VER_ID_PROPERTY}>'

    with open(POM_FILE_NAME, 'r') as input_stream:
        pom_file = input_stream.read()
        pom_file = re.sub(SER_VER_ID_PATTERN, new_ser_ver_id_element, pom_file, 1)
    with open(POM_FILE_NAME, 'w') as output:
        output.write(pom_file)
    prefix = 'New' if (state.is_ser_ver_id_new()) else 'Same'
    info(f'{prefix} serialization.version.id set: {state.new_ser_ver_id}')


def commit_new_versions():
    section('Commit new version with version and serialization version id set ...')
    git_im('commit', '--all', '-m', state.new_version_description())
    info(f'Commit done: {state.new_version_description()}')


def tag_release():
    section(f'Tag release with {state.new_version} ...')
    git_im('tag', '-a', state.new_version_tag(), '-m', state.new_version_description())
    info(f'Tag done: {state.new_version_tag()}')


def push_release_branch_and_tag():
    section('Push new release with pom.xml versions and new tag')
    git_im('push', '-f', f'{config.release_remote}', f'v{state.new_version}',
        f'{config.release_branch}')
    info(f'Release pushed to: {config.release_branch_path()}')
    delete_script_state()
    info('\nRELEASE SUCCESS!\n')


# Merge the old version into the new version. This only keep a reference to the old version, the
# resulting git tree of the merge is that of the new branch head, effectively ignoring all changes
# from the old release. This creates a continuous line of releases in the release branch.
def merge_in_old_release_with_no_changes():
    section('Merge old release into the release branch ...')
    git_im('merge', '-s', 'ours', config.release_branch_path(), '-m',
           'Merge old release into the release branch - NO CHANGES COPIED OVER')
    info('Merged - NO CHANGES COPIED OVER')


## ------------------------------------------------------------------------------------ ##
##                                   Setup and verify                                   ##
## ------------------------------------------------------------------------------------ ##

def parse_and_verify_cli_arguments_and_options():
    args = []
    for arg in sys.argv[1:]:
        if re.match(r'(-h|--help)', arg):
            print_help()
        elif re.match(r'(--dryRun)', arg):
            options.dry_run = True
        elif re.match(r'(--debug)', arg):
            options.debugging = True
        elif re.match(r'(--hotfix)', arg):
            options.hotfix = True
        elif re.match(r'(--serVerId)', arg):
            options.bump_ser_ver_id = True
        elif re.match(r'(--skipPRs)', arg):
            options.skip_prs = True
        else:
            options.base_revision = arg
            args.append(arg)
    if len(args) > 1:
        error(f'Expected one argument, <base-revision>, but got: {args}')
    options.verify()


def resume():
    section('Resume')
    print('''
      Do you want to resume the previous execution of the script?
       - You must first fix the merge conflict or unit-test failing.
       - Then commit your changes.
       - Then run this script again, the script will automatically resume the release process
         at the same place it failed.

      Do you want to resume the release process?
    ''')
    answer = 'answer'
    p = re.compile(r'[\syYxX]')
    while not p.match(answer):
        answer = input("Press 'y' to continue, 'x' to exit: ")
    if answer == 'x':
        exit(0)
    read_script_state()


def load_config():
    section('Load configuration...')
    with open('script/release_env.json', 'r') as f:
        doc = json.load(f)
        config.upstream_remote = doc['upstream_remote']
        config.release_remote = doc['release_remote']
        config.release_branch = doc['release_branch']
        config.ext_branches = doc['ext_branches']
        config.include_prs_label = doc['include_prs_label']
        config.ser_ver_id_prefix = doc['ser_ver_id_prefix']
        debug(f'Config loaded: {config}')

    if len(config.ser_ver_id_prefix) != 2:
        error(
            f"Configure the 'ser_ver_id_prefix'. The prefix must be exactly two characters long. "
            f"Value: <{config.ser_ver_id_prefix}>")


def verify_script_run_from_root():
    if sys.argv[0] != 'script/custom-release.py':
        error(f'Run script from root directory.')


def verify_git_installed():
    execute('git', '--version', quiet=False)


def verify_maven_installed():
    execute('mvn', '--version', quiet=False)


def verify_release_base_and_release_branch_exist():
    if options.hotfix:
        info('Verify release branch/commit exist ...')
    else:
        info('Verify base revision and release branch/commit exist ...')
        git('rev-parse', '--quiet', '--verify', options.release_base(),
            error_msg='Base revision not found!')
    git('rev-parse', '--quiet', '--verify', config.release_branch_path(),
        error_msg='Release branch not found!')


def verify_no_local_git_changes():
    info('Verify no local changes exist ...')
    git_im('diff-index', '--quiet', 'HEAD', error_msg='There are local changes!')


# Make sure all remote repos are up-to date
def fetch_all_git_remotes():
    git_im('fetch', '--all', error_msg='Git fetch all remotes failed!')


def resolve_version_number():
    info(f'Resolve version number ...')
    version_qualifier = config.release_remote
    state.major_version = read_major_version_from_pom(version_qualifier, options.release_base())


def resolve_new_version():
    info('Resolve new version number ...')
    p = git('tag', '--list', '--sort=-v:refname', error_msg='Fetch git tags failed!')
    tags = p.stdout.splitlines()

    prefix = f'{state.major_version}-{config.release_remote}-'
    pattern = re.compile('v' + prefix.replace('.', r'\.') + r'(\d+)')
    max_tag_version = max((int(m.group(1)) for tag in tags if (m := pattern.match(tag))), default=0)
    state.current_version = prefix + str(max_tag_version)
    state.new_version = prefix + str(1 + max_tag_version)


def list_labeled_prs():
    if options.skip_prs or (not config.include_prs_label):
        info('Skip merging in GitHub PRs.')
        return
    info('Get PRs to include and their labels from GitHub - This requires authentication ...')

    # The query body needs to be on one line, for an unknown reason.
    query_text = ('query ReadOpenPullRequests { '
                  'repository(owner:\\"opentripplanner\\", name:\\"OpenTripPlanner\\") { '
                  f'pullRequests(first: 100, states: OPEN, labels: \\"{config.include_prs_label}\\") ' 
                  '{ nodes { number, labels(first: 20) { nodes { name } } } } } } }')
    post_body = '''
    {
      "query":"''' + query_text + '''",
      "operationName":"ReadOpenPullRequests"
    }
    '''
    git_hub_access_token = os.environ['GIT_HUB_ACCESS_TOKEN']
    result = execute('curl', '-H', f'Authorization: Bearer {git_hub_access_token}', '-X', 'POST',
                     '-d', post_body, 'https://api.github.com/graphql',
                     error_msg='GitHub GraphQL Query failed!', quiet_err=True)

    # Example response
    #   {"data":{"repository":{"pullRequests":{"nodes":[{"number":2222,"labels":{"nodes":[{"name":"bump serialization id"},{"name":"Entur Test"}]}}]}}}}
    json_doc = json.loads(result.stdout)
    for node in json_doc['data']['repository']['pullRequests']['nodes']:
        pr_number = node['number']
        pr_labels = []
        labels = node['labels']['nodes']
        # GitHub labels are not case-sensitive, hence the '(?i)'
        ptn = re.compile(f'(?i)({LBL_BUMP_SER_VER_ID}|{config.include_prs_label})')
        for label in labels:
            lbl_name = label['name']
            if ptn.match(lbl_name):
                pr_labels.append(lbl_name)
        state.prs_to_merge[str(pr_number)] = pr_labels

    state.prs_bump_ser_ver_id = any(
        LBL_BUMP_SER_VER_ID in labels for labels in state.prs_to_merge.values())


def resolve_new_ser_ver_id():
    info('Resolve the new serialization version id ...')
    curr_release_hash = git_show_ref(git_tag(state.current_version))
    curr_ser_ver_id = read_ser_ver_id_from_pom_file(curr_release_hash)
    bump_ser_ver_id = options.bump_ser_ver_id

    # If none of the PRs have the 'bump serialization id' label set, then find the upstream
    # serialization-version-id for the release-base and the current version. If these
    # serialization-version-ids are different, then we need to bump the new release serialization
    # version id. To find the *upstream* ids we step back in the git history looking for an id
    # not matching the project serialization version id prefix - this is assumed to be the latest
    # serialization version from the upstream project.
    if not bump_ser_ver_id:
        info('  - Find upstream serialization version id for current release ...')
        curr_upstream_id = find_upstream_ser_ver_id_in_history(curr_release_hash)

        info(f'  - Find base serialization version id ...')
        base_hash = git_show_ref(options.release_base())
        base_upstream_id = find_upstream_ser_ver_id_in_history(base_hash)

        # Update serialization version id in release if serialization version id has changed
        bump_ser_ver_id = curr_upstream_id != base_upstream_id
        info(
            f'  - The current upstream serialization.ver.id is {curr_upstream_id} '
            f'and the base upstream id is {base_upstream_id}.')

    state.current_ser_ver_id = curr_ser_ver_id
    if bump_ser_ver_id:
        state.new_ser_ver_id = bump_release_ser_ver_id(curr_ser_ver_id)
    else:
        state.new_ser_ver_id = curr_ser_ver_id


# Find the serialization-version-id for the upstream git project using the git log starting
# with the given revision (abort if not found in previous 20 commits)
def find_upstream_ser_ver_id_in_history(revision: str):
    output = git('log', '-20', '--format=oneline', revision).stdout
    for line in output.splitlines():
        git_hash = line.split()[0]
        ser_ver_id = read_ser_ver_id_from_pom_file(git_hash)
        if not ser_ver_id.startswith(config.ser_ver_id_prefix):
            return ser_ver_id
    error(f'No ser.ver.id found for revision {revision} and previous 20 commits.')


def print_setup():
    section('Configuration')
    info(f'''
CLI Arguments
  - Base revision for release ... : {options.base_revision}

CLI Options
  - Bump ser.ver.id ............. : {options.bump_ser_ver_id}
  - Dry run  .................... : {options.dry_run}
  - Debugging ................... : {options.debugging}
  - Hotfix ...................... : {options.hotfix}

Config
  - Upstream git repo remote name : {config.upstream_remote}
  - Release to remote git repo .. : {config.release_remote}
  - Release branch .............. : {config.release_branch}
  - Configuration branches ...... : {config.ext_branches}
  - Ser.ver.id prefix ........... : {config.ser_ver_id_prefix}
''')
    if config.include_prs_label:
        info(f'PRs to merge')
        for pr in state.prs_to_merge:
            info(f'  - {pr} with labels {state.prs_to_merge[pr]}')
    info(f'''
Release info
  - Project major version ....... : {state.major_version}
  - Current version ............. : {state.current_version}
  - New version ................. : {state.new_version}
  - Current ser.ver.id .......... : {state.current_ser_ver_id}
  - New ser.ver.id .............. : {state.new_ser_ver_id}
''')


## ------------------------------------------------------------------------------------ ##
##                                        Resume                                        ##
## ------------------------------------------------------------------------------------ ##

def read_script_state():
    info('The script will resume and print the state of the previous process.')
    global options, config, state
    with open(STATE_FILE, 'r') as f:
        doc = json.load(f)
        options.__dict__ = doc['options']
        config.__dict__ = doc['config']
        state.__dict__ = doc['state']
        state.gotoStep = True


def save_script_state(step):
    state.step = step
    doc = {'options': options.__dict__, 'config': config.__dict__, 'state': state.__dict__}
    with open(STATE_FILE, 'w') as f:
        json.dump(doc, f)


# Delete the script state file - this avoids going into resume the nest time the
# script is run.
def delete_script_state():
    execute('rm', '-f', STATE_FILE)


## ------------------------------------------------------------------------------------ ##
##                                   Utility functions                                  ##
## ------------------------------------------------------------------------------------ ##

# Get the full git hash for a qualified branch name, tag or hash
def git_show_ref(ref):
    if re.compile(r'[0-9a-f]{40}').match(ref):
        return ref
    output = execute('git', 'show-ref', ref).stdout
    return output.split()[0]


# Create a tag name used in git for a given version
def git_tag(version):
    return f'v{version}'


def bump_release_ser_ver_id(current_id):
    value = int(current_id[3:])
    v = config.ser_ver_id_prefix + '-{:04d}'.format(value + 1)
    debug(f'New serialization version id: {v}')
    return v


def read_major_version_from_pom(version_qualifier: str, revision: str):
    pom_xml = execute('git', 'show', f'{revision}:{POM_FILE_NAME}').stdout
    token_ptn = re.compile(r'<version>(.*)</version>')
    ver_ptn = re.compile(r'(\d+\.\d+\.\d+)-(' + version_qualifier + r'-\d+|SNAPSHOT)')

    # Find the first <version>... in the pom_file.
    match = token_ptn.search(pom_xml)
    if match:
        debug(f'Version found: {match.group(1)}')
        match = ver_ptn.match(match.group(1).strip())
        if match:
            debug('Main version number: {m.group(1)}')
            return match.group(1)
    error(f"Version not found in '{POM_FILE_NAME}'.")


def read_ser_ver_id_from_pom_file(git_hash):
    pom_xml = execute('git', 'show', f'{git_hash}:{POM_FILE_NAME}').stdout
    m = SER_VER_ID_PATTERN.search(pom_xml)
    return m.group(1)


def run_maven_test():
    if section_w_resume('run_maven_test', 'Run unit tests'):
        # Do not use execute here, this takes more than 10 seconds, and we want the output to be piped
        subprocess.run(['mvn', 'clean', 'test'])


def git(*cmd, error_msg=None):
    return execute('git', *cmd, error_msg=error_msg)


# Git impact should be used if the command change or update the system, this command
# is NOT run if the '--dryRun' flag is set.
def git_im(*cmd, error_msg=None):
    return execute('git', *cmd, error_msg=error_msg, impact=True)


def execute(*cmd, quiet=True, quiet_err=False, error_msg=None, impact=False):
    if options.dry_run and impact:
        info(f'=> {cmd}  (--dryRun SKIPPED)')
        return
    debug(f'Run command: {cmd}')
    p = subprocess.run(args=list(cmd), capture_output=True, text=True, timeout=20)
    debug(f'  <= {p.returncode}')

    if p.stdout and not quiet:
        info(p.stdout)
    if p.stderr and not quiet_err:
        info(p.stderr)
    if p.returncode != 0:
        if error_msg:
            error(f'{error_msg} Command {cmd} failed.')
        else:
            error(f'Command {cmd} failed.')
    return p


## ------------------------------------------------------------------------------------ ##
##                                     Log functions                                    ##
## ------------------------------------------------------------------------------------ ##

def section_w_resume(step: str, msg: str) -> bool:
    if state.run(step):
        save_script_state(step)
        section(msg)
        return True
    else:
        save_script_state(step)
        section(f'{msg}  (SKIP STEP)')
        return False


def section(msg: str):
    hr = '-------------------------------------------------------------------------------------------'
    print('')
    print(hr)
    print(f'  {msg}')
    print(hr)


def info(msg):
    print(f'{msg}')


def error(msg):
    print(f'\nERROR {msg}\n')
    sys.stdout.flush()
    exit(1)


def debug(msg):
    if options.debugging:
        print(f'DEBUG {msg}')


## ------------------------------------------------------------------------------------ ##
##                                     Help function                                    ##
## ------------------------------------------------------------------------------------ ##

def print_help():
    section('Help')
    print(f"""
    This script is used to create a new release in a downstream fork of OTP. It will set both
    the Maven version(2.7.0-entur-23) and the serialization-version-id(EN-0020) to a unique id
    using the fork project name. For the script to work the git remote name must match the
    GitHub 'owner' name.

    Release process overview
      1. The configured release-branch is reset hard to the <base-revision>.
      2. Then the labeled PRs are merged into the release-branch, if configured.
      3. The config-branch is rebased onto the release-branch.
      4. The pom.xml file is updated with a new version and serialization version id.
      5. The release is tested, tagged and pushed to Git repo.

    See the RELEASE_README.md for more details.

    Usage
      script/prepare_release.py [options] [<base-revision>]

    Arguments
        <base-revision> : The base branch or commit to use as the base for the release. The
                          'otp/dev-2.x' is the most common base branch to use, but you can
                          create a new release on top of any <commit>.
                          This parameter is required unless option --hotfix is used.

    Options
      -h, --help : Print this help.
      --debug    : Run script with debug output enabled.
      --dryRun   : Run script locally, nothing is pushed to remote server.
      --hotfix   : Create a new release from the current local Git repo HEAD. It updates the
                   maven-project-version and the serialization-version-id, creates a new tag
                   and push the release. You should apply all fixes and commit BEFORE running
                   this script. Can not be used with the <base-revision> argument set.
      --serVerId : Force incrementation of the serialization version id.
      --skipPRs  : Skip PRs labeled with the configured 'include_prs_label'.

    Examples
      # script/prepare_release.py otp/dev-2.x
      # script/prepare_release.py 0715be88
      # script/prepare_release.py --dryRun --debug entur/my-feature-branch
      # script/prepare_release.py --hotfix --serVerId


    Failure
      If a release fails, you may resume the release process after fixing the problem by running
      the custom-release script again. The script will automatically detect that the script failed
      and resume the release. Typical errors are merge conflicts and unit-test failures. Remember
      to commit you changes after the problem is fixed.

      If you do not want to resume, an instead start over delete the {STATE_FILE} and run the
      script again. You also need to delete the tag, if the release was tagged - this is last step
      before the release is pushing to the remove git repo.
    """)
    exit(0)


if __name__ == '__main__':
    main()
