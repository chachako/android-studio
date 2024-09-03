"""Implements shared functions for selective presubmit."""

import dataclasses
import logging
import os
import pathlib
import re
import tempfile
from typing import Iterator, List, Sequence

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import bazel_diff
from tools.base.bazel.ci import gce


_HASH_FILE_BUCKET = 'adt-byob'
_HASH_FILE_NAME = 'bazel-diff-hashes/{bid}-{target}.json'
_MAX_RUNS_PER_TEST = 200
_LOCAL_REPOSITORIES = [
    'intellij'
]


@dataclasses.dataclass
class SelectivePresubmitResult:
  """Represents the result of attempting a selective presubmit."""
  found: bool
  targets: List[str]
  flags: List[str]


def _generate_hash_file(build_env: bazel.BuildEnv) -> str:
  """Generates the hash file for the current build."""
  hash_file_path = os.path.join(build_env.dist_dir, 'bazel-diff-hashes.json')
  bazel_diff.generate_hash_file(
      build_env,
      _LOCAL_REPOSITORIES,
      hash_file_path,
  )
  return hash_file_path


def _find_impacted_targets(build_env: bazel.BuildEnv) -> List[str] | None:
  """Finds the targets impacted by the current change.

  Args:
    build_env: The build environment.

  Returns:
    The list of impacted targets if the base hash file was found, or None
    otherwise.
  """
  with tempfile.TemporaryDirectory() as temp_dir:
    temp_path = pathlib.Path(temp_dir)

    current_hashes = _generate_hash_file(build_env)
    base_hashes = temp_path / 'base-hashes.json'
    reference_bid = gce.get_reference_build_id(
        build_env.build_number,
        build_env.build_target_name,
    )
    logging.info('Found reference build ID: %s', reference_bid)
    object_name = _HASH_FILE_NAME.format(
        bid=reference_bid,
        target=build_env.build_target_name,
    )
    exists = gce.download_from_gcs(
        _HASH_FILE_BUCKET,
        object_name,
        str(base_hashes),
    )
    if not exists:
      logging.info('Base hash file %s not found', object_name)
      return None
    logging.info('Base hash file %s found', object_name)

    impacted_targets = temp_path / 'impacted-targets.txt'
    bazel_diff.get_impacted_targets(
        build_env,
        base_hashes,
        current_hashes,
        impacted_targets,
    )
    return impacted_targets.read_text().splitlines()


def _find_impacted_test_targets(
    build_env: bazel.BuildEnv,
    base_targets: Sequence[str],
    test_flag_filters: str,
) -> List[str]:
  """Finds the test targets impacted by the current change.

  The comprehensive list of impacted targets found by bazel-diff is filtered
  using the test flag filters and the base targets.

  Args:
    build_env: The build environment.
    base_targets: The base set of targets used for filtering.
    test_flag_filters: The test flag filters used for filtering.

  Returns:
    A list of targets that should be tested.
  """
  targets = _find_impacted_targets(build_env)
  if targets is None:
    # If impacted targets could not be generated, use the base targets.
    logging.info('Using base targets')
    return base_targets

  filters = test_flag_filters.split(',') if test_flag_filters else []
  filters.append('-manual')

  include_query = []
  exclude_query = []
  for target in base_targets:
    if target[0] == '-':
      target = target[1:]
      exclude_query.append(target)
    else:
      include_query.append(target)

    for test_filter in filters:
      if test_filter[0] == '-':
        exclude_query.append(f'attr(tags, "{test_filter[1:]}", {target})')
      else:
        include_query.append(f'attr(tags, "{test_filter}", {target})')

    exclude_query.append(f'attr(target_compatible_with, "@platforms//:incompatible", {target})')

  query = (
      ' union '.join(include_query) +
      ' except ' +
      ' except '.join(exclude_query)
  )
  result = build_env.bazel_query(query)

  return list(set(targets) & set(result.stdout.decode('utf-8').splitlines()))


def _parse_gerrit_tags(
    build_env: bazel.BuildEnv,
    filter_tag: str,
) -> Iterator[str]:
  """Yields tag values from Gerrit change tags.

  Tag values are expected to be in one of two formats:
    - <ab_target>:<value>
    - <value>

  Args:
    bazel_env: The build environment.
    filter_tag: The tag to look for.

  Yields:
    The tag values from the targeted Gerrit changes.
  """
  for gerrit_change in gce.get_gerrit_changes(build_env.build_number):
    for tag, value in gerrit_change.tags:
      if tag.lower() != filter_tag.lower():
        continue
      logging.info('Found %s tag: %s', tag, value)
      # AB target names only contain word characters and hyphens.
      match = re.fullmatch(r'^([\w-]+):(.+)$', value)
      if match:
        ab_target, value = match.group(1), match.group(2)
        if ab_target != build_env.build_target_name:
          continue
      yield value


def generate_and_upload_hash_file(build_env: bazel.BuildEnv) -> None:
  """Generates and uploads the hash file for the current build to GCS."""
  object_name = _HASH_FILE_NAME.format(
      bid=build_env.build_number,
      target=build_env.build_target_name,
  )
  hash_file_path = _generate_hash_file(build_env)
  gce.upload_to_gcs(hash_file_path, _HASH_FILE_BUCKET, object_name)
  logging.info('Uploaded hash file to GCS with object name: %s', object_name)


def find_test_targets(
    build_env: bazel.BuildEnv,
    base_targets: Sequence[str],
    test_flag_filters: str,
) -> SelectivePresubmitResult:
  """Returns the result of selecting test targets for the current build.

  Tags in the CL description are used to customize the behavior of the
  presubmit, e.g.:
    - Presubmit-Test: default
      - Tests all default targets regardless of whether they are impacted.
    - Presubmit-Test: studio-linux:default
      - Tests all default targets on studio-linux regardless of whether they are
        impacted.
    - Presubmit-Test: //tools/base:some_test
      - Explicitly tests //tools/base:some_test on all platforms.
    - Presubmit-Test: studio-win://tools/base:some_test
      - Explicitly tests //tools/base:some_tests only on studio-win.

  Tags can be repeated in one description and across multiple changes.
  """
  # Parse Presubmit-Test tags.
  use_base_targets = False
  explicit_targets = []
  for value in _parse_gerrit_tags(build_env, 'Presubmit-Test'):
    # "default" is a special value that indicates that all default targets
    # should be tested.
    if value.lower() == 'default':
      use_base_targets = True
      continue

    # Add any targets that are explicitly requested.
    explicit_targets.append(value)

  if use_base_targets:
    impacted_targets = base_targets
  else:
    impacted_targets = _find_impacted_test_targets(
        build_env,
        base_targets,
        test_flag_filters,
    )

  found = impacted_targets != base_targets
  impacted_target_count = len(impacted_targets) if found else 0
  targets = impacted_targets + explicit_targets
  change = gce.get_gerrit_changes(build_env.build_number)[0]

  if found:
    logging.info('Found %d impacted targets', impacted_target_count)
  else:
    logging.info('Not using selective presubmit')

  flags = [
      f'--build_metadata=selective_presubmit_found={found}',
      f'--build_metadata=selective_presubmit_impacted_target_count={impacted_target_count}',
      f'--build_metadata=gerrit_owner={change.owner}',
      f'--build_metadata=gerrit_change_id={change.change_id}',
      f'--build_metadata=gerrit_change_number={change.change_number}',
      f'--build_metadata=gerrit_change_patchset={change.patchset}',
  ]
  if change.topic:
    flags.append(f'--build_metadata=gerrit_topic={change.topic}')
  return SelectivePresubmitResult(
      found=found,
      targets=targets,
      flags=flags,
  )


def generate_runs_per_test_flags(build_env: bazel.BuildEnv) -> List[str]:
  """Returns the flags used to specify the number of runs per test."""
  flags = []
  for value in _parse_gerrit_tags(build_env, 'Presubmit-Runs-Per-Test'):
    target, runs = value.split('@')
    runs = int(runs)

    # Limit the number of runs per test.
    if runs > _MAX_RUNS_PER_TEST:
      raise ValueError(f'Exceeded maximum runs per test: {runs} > {_MAX_RUNS_PER_TEST}')

    # Prevent wildcards.
    if target.endswith('...') or target.endswith(':all'):
      raise ValueError(f'Wildcard target not allowed: {target}')

    logging.info('Running %s with %d runs per test', target, runs)
    flags.append(f'--runs_per_test={value}')

  return flags