import unittest

from .common import DriverFailure
from .live_fixture import COMMAND, TIMEOUT_MS, tick_progress


class LiveFixtureTests(unittest.TestCase):
    def state(self, output):
        return dict(
            status="running",
            input=dict(command=COMMAND, timeout=TIMEOUT_MS),
            metadata=dict(output=output),
        )

    def test_counts_actual_sequential_tool_output_only(self):
        self.assertEqual(tick_progress(self.state("FQ9_TICK:1\nFQ9_TICK:2\n")), 2)
        self.assertEqual(tick_progress(self.state("FQ9_TICK:1\nFQ9_TIC")), 1)
        self.assertEqual(tick_progress(self.state("")), 0)

    def test_missing_timeout_changed_command_fake_or_out_of_order_output_refused(self):
        for output in (
            "DONE",
            "FQ9_TICK:2\n",
            "FQ9_TICK:1\nFQ9_TICK:1\n",
            "secret\n",
            "FQ9_TICK:91\n",
        ):
            with self.subTest(output=output), self.assertRaises(DriverFailure):
                tick_progress(self.state(output))
        for key, value in (("timeout", 120000), ("command", COMMAND + " &")):
            state = self.state("FQ9_TICK:1\n")
            state["input"][key] = value
            with self.assertRaises(DriverFailure):
                tick_progress(state)


if __name__ == "__main__":
    unittest.main()
