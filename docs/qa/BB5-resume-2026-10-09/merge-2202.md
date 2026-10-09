# BB5 / final-pass 2202 merge

Merged feat/genui-fe (6170a2337) without rebase. Kept the device-proven owner-preserving fixture, bounded startup settlement and observer, actual SystemUI tap, strict GenUI source admission, and fixed resume diagnostics. The driver now requires QA 2202 plus normal 2202 and no longer requests downgrade installation. The previously qualified 2198 artifacts are historical evidence, not valid inputs to the updated driver.

Failing-first: with the local VERSION=2198 retained, test_bb5_uses_distinct_reviewed_qa_artifact_and_restores_normal failed (blocked versus pass). After adopting the 2202 contract, the complete focused command passed: 99 tests.

```
PYTHONPATH=tool/qa python3 -m unittest tool.qa.test_bb5_runtime_acceptance tool.qa.test_bb5_native_guard tool.qa.test_final_pass tool.qa.test_final_pass_2202 tool.qa.test_final_pass_install tool.qa.test_final_pass_misc tool.qa.test_final_pass_protocols
```

The integration test invokes final_pass_install against the real driver's parsed constants, checks the distinct QA artifact argument, and accepts the exact normal-2202 restoration receipt. final_pass.py dispatches BB5 through this adapter. No device or APK rebuild occurred for this merge; no new device qualification is claimed. BB7 draft work is excluded.
