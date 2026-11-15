"""Runner-only mutations: prove the new bridge assertions fail without it."""
import contextlib
import io
import json
from pathlib import Path
import unittest
import test_adapter

proof = []
for name, mutate, tests in (
    ("mapping", lambda: setattr(test_adapter.adapter.SessionIndex, "put", lambda *a: None), ["test_B_AGENT_UH_001_mapping_is_atomic_private_and_keeps_other_sessions"]),
    ("identity", lambda: setattr(test_adapter.adapter, "session_id", lambda frame, old, new: frame), [
        "test_B_AGENT_UH_002_framing_preserves_tools_and_only_changes_envelope_ids",
        "test_B_AGENT_UH_003_close_keeps_mapping_delete_removes_it_and_load_restarts",
    ]),
    ("step-input", lambda: setattr(test_adapter.adapter, "present_step", lambda frame: frame), ["test_B_AGENT_UH_004_step_input_keeps_original_code_and_gives_helper_a_command"]),
):
    original_put, original_ids, original_step = test_adapter.adapter.SessionIndex.put, test_adapter.adapter.session_id, test_adapter.adapter.present_step
    mutate()
    stream = io.StringIO()
    suite = unittest.TestSuite(test_adapter.AdapterTest(name) for name in tests)
    result = unittest.TextTestRunner(stream=stream).run(suite)
    test_adapter.adapter.SessionIndex.put, test_adapter.adapter.session_id = original_put, original_ids
    test_adapter.adapter.present_step = original_step
    assert len(result.failures) + len(result.errors) == len(tests), stream.getvalue()
    proof.append({"mutation": name, "tests": result.testsRun, "failedAsExpected": len(result.failures) + len(result.errors)})
    Path("/facts/adapter-red-" + name + ".txt").write_text(stream.getvalue())
Path("/facts/adapter-red-proof.json").write_text(json.dumps(proof, indent=2) + "\n")
