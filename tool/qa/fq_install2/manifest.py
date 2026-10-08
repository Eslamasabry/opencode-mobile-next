"""Offline artifact receipts. Hashes/defines must come from reviewed builds."""
import json
import re
from pathlib import Path

FLAG = 'OC_QA_AGENT_INSTALL_MIN_FREE_BYTES'
FLOOR = 8589934592


def load(path, case):
    raw = Path(path).read_bytes()
    if len(raw) > 16384:
        raise ValueError('artifact_manifest_too_large')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result: raise ValueError('duplicate_artifact_field')
            result[key] = value
        return result
    value = json.loads(raw, object_pairs_hook=unique)
    if type(value) is not dict or set(value) not in ({'normal'}, {'normal', 'guard'}):
        raise ValueError('invalid_artifact_manifest')
    for name, artifact in value.items():
        if type(artifact) is not dict or set(artifact) != {'apk', 'build', 'sha256', 'sourceRevision', 'dartDefines'}:
            raise ValueError('invalid_artifact_receipt')
        if (type(artifact['apk']) is not str or not Path(artifact['apk']).is_absolute() or
            type(artifact['build']) is not int or not 2197 <= artifact['build'] <= 99999 or
            type(artifact['sha256']) is not str or not re.fullmatch('[0-9a-f]{64}', artifact['sha256']) or
            type(artifact['sourceRevision']) is not str or not re.fullmatch('[0-9a-f]{40}', artifact['sourceRevision'])):
            raise ValueError('invalid_artifact_identity')
        defines = artifact['dartDefines']
        if type(defines) is not dict or set(defines) - {FLAG}:
            raise ValueError('invalid_artifact_defines')
        expected = FLOOR if name == 'guard' else 0
        if type(defines.get(FLAG, 0)) is not int or defines.get(FLAG, 0) != expected:
            raise ValueError('unexpected_artifact_guard')
    if case == 'low-storage' and 'guard' not in value:
        raise ValueError('flagged_guard_artifact_required')
    if 'guard' in value and value['guard']['sha256'] == value['normal']['sha256']:
        raise ValueError('artifact_guard_matches_normal')
    return value
