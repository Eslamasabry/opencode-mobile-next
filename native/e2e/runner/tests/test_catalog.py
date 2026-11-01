"""Catalog parsing: the 39 flows of e2e-flows.md and the 240 portable_e2e rows."""
import unittest

from runner import catalog


class CatalogTest(unittest.TestCase):
    def test_e2e_flows_lists_39_flows_with_rows(self):
        flows = catalog.load_flows()
        self.assertEqual(39, len(flows))
        self.assertEqual(["B-NAV-017", "B-NAV-018", "B-NAV-024", "B-NAV-037"], flows["E2E-01"]["rows"])
        self.assertEqual(11, len(flows["E2E-39"]["rows"]))

    def test_union_of_flow_rows_is_exactly_the_portable_e2e_rows(self):
        flows = catalog.load_flows()
        union = set()
        for f in flows.values():
            union.update(f["rows"])
        self.assertEqual(catalog.portable_rows(), union)
        self.assertEqual(240, len(union))

    def test_groups_cover_every_flow_once(self):
        seen = [f for g in catalog.GROUPS.values() for f in g["flows"]]
        self.assertEqual(sorted(seen), sorted(catalog.load_flows()))
        self.assertEqual(len(seen), len(set(seen)))


if __name__ == "__main__":
    unittest.main()
