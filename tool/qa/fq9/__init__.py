"""Offline-tested pre-release checklist drivers; default CLI is read-only planning."""


_discovering = False


def load_tests(loader, standard_tests, pattern):
    """Load these tests under one canonical module name.

    The drivers import each other as ``tool.qa.fq9`` and the tests patch them
    by that dotted name. ``discover -s tool/qa`` would otherwise also load the
    same files a second time as top-level ``fq9``, and the patches would miss
    the code under test. Discover them from the repository root instead.
    """
    global _discovering
    if _discovering:
        return standard_tests
    from pathlib import Path

    here = Path(__file__).resolve().parent
    _discovering = True
    try:
        return loader.discover(
            str(here), pattern or "test_*.py", top_level_dir=str(here.parents[2])
        )
    finally:
        _discovering = False
