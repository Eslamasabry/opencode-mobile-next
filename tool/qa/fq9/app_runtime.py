"""Reversible runtime selection through the app's This phone UI only.

The caller owns the device lock and baseline identity. No preference writes,
service commands, installs, providers, or native setup entry points are used.
"""

import json
from pathlib import Path

from tool.qa.bd7_device_ui import Bd7Ui, _rect
from .common import DriverFailure, PACKAGE
from .observations import service_foreground
from .runtime import FGS

FAIL_CODES = frozenset(
    {
        "runtime_switch_unavailable",
        "runtime_switch_failed",
        "runtime_switch_timeout",
        "runtime_restore_failed",
        "runtime_not_idle",
        "runtime_identity_unknown",
    }
)
ENGINES = frozenset({"opencode", "opencode2"})
ROOT = Path(__file__).resolve().parents[3]
COPY = [
    json.loads((ROOT / f"lib/l10n/app_{lang}.arb").read_text()) for lang in ("en", "ar")
]


class AppRuntimeUi:
    def __init__(self, device):
        self.device = device
        self.ui = Bd7Ui(lambda args, **kwargs: device.adb(*args, **kwargs))

    def labels(self, key, engine=None):
        return {
            copy[key].replace(
                "{runtime}",
                copy["setupRuntimeOne" if engine == "opencode" else "setupRuntimeTwo"],
            )
            if engine is not None
            else copy[key]
            for copy in COPY
        }

    def nodes(self):
        return [
            node
            for node in self.ui.nodes()
            if node.get("package") == PACKAGE
            and node.get("visible-to-user", "true") != "false"
        ]

    def host_labels(self):
        labels = self.labels("phoneServerCardTitle")
        for copy in COPY:
            for key in ("setupRuntimeOne", "setupRuntimeTwo"):
                labels.add(
                    copy["phoneSetupOpenPhoneRuntime"]
                    .replace("{name}", copy["phoneServerCardTitle"])
                    .replace("{runtime}", copy[key])
                )
        return labels

    def find(self, nodes, labels, *, clickable=False):
        if clickable:
            nodes = [node for node in nodes if node.get("clickable") == "true"]
        matches = [self.ui._find(label, nodes) for label in labels]
        return min(
            (
                node
                for node in matches
                if node is not None and node.get("enabled", "true") == "true"
            ),
            key=lambda node: (_rect(node)[2] - _rect(node)[0])
            * (_rect(node)[3] - _rect(node)[1]),
            default=None,
        )

    def tap(self, node):
        x, y = self.ui.centre(node)
        self.device.adb("shell", "input", "tap", str(x), str(y), timeout=5)

    def server_switcher(self, nodes):
        # KitShellControls appends the authored action to a private server name
        # and status in one semantics label. Match only that fixed suffix.
        matches = [node for node in nodes if node.get("clickable") == "true"
                   and any(self.ui.text(node).endswith(", " + label)
                           for label in self.labels("kitTopBarSwitchServer"))]
        return matches[0] if len(matches) == 1 else None

    def scoped_more(self, nodes):
        """Choose only the menu in the unambiguously named in-app card."""
        choices = []
        for node in nodes:
            if not any(
                self.ui._matches(node, label)
                for label in self.labels("phoneServerCardMore")
            ):
                continue
            parent = self.ui._parents.get(node)
            for _ in range(7):
                if parent is None:
                    break
                children = [child for child in parent.iter() if child in nodes]
                named = any(
                    any(self.ui._matches(child, label) for label in self.host_labels())
                    for child in children
                )
                menus = sum(
                    any(
                        self.ui._matches(child, label)
                        for label in self.labels("phoneServerCardMore")
                    )
                    for child in children
                )
                if named and menus == 1:
                    choices.append(node)
                    break
                parent = self.ui._parents.get(parent)
        return choices[0] if len(choices) == 1 else None

    def switch(self, target):
        if target not in ENGINES or not self.device.locked:
            raise DriverFailure("runtime_switch_unavailable")
        try:
            self.device.launch()
            target_labels = self.labels("phoneServerCardSwitchTo", target)
            for _ in range(12):
                nodes = self.nodes()
                action = self.find(nodes, target_labels)
                page = self.find(nodes, self.labels("phoneServerCardTitle"))
                # The This phone page has the switch row and a real title. A
                # menu can expose the same label; require Manage first there.
                manage = self.find(nodes, self.labels("thisPhoneManage"))
                if action is not None and page is not None and manage is None:
                    self.tap(action)
                    break
                if manage is not None:
                    self.tap(manage)
                elif (more := self.scoped_more(nodes)) is not None:
                    self.tap(more)
                elif (
                    switcher := self.find(nodes, self.labels("serverSwitcherOpen"))
                    or self.server_switcher(nodes)
                ) is not None:
                    self.tap(switcher)
                elif page is not None:
                    self.ui.scroll("down")
                elif (chats := self.find(nodes, self.labels("shellTabChats"), clickable=True)) is not None:
                    self.tap(chats)
                elif (back := self.find(nodes, {"Back", "رجوع"})) is not None:
                    self.tap(back)
                else:
                    raise DriverFailure("runtime_switch_unavailable")
                self.device.sleep(0.3)
            else:
                raise DriverFailure("runtime_switch_unavailable")
            deadline = self.device.monotonic() + 10
            while self.device.monotonic() < deadline:
                nodes = self.nodes()
                title = self.find(nodes, self.labels("setupSwitchConfirmTitle", target))
                detail = self.find(nodes, self.labels("setupSwitchConfirmDetail"))
                confirm = self.find(nodes, self.labels("setupSwitchConfirm", target))
                if title is not None and detail is not None and confirm is not None:
                    self.tap(confirm)
                    return
                self.device.sleep(0.3)
            raise DriverFailure("runtime_switch_unavailable")
        except DriverFailure:
            raise
        except Exception:
            raise DriverFailure("runtime_switch_failed") from None


class RuntimeTransaction:
    """Keep OC1 selected through seed + upgrade; restore after normal APK."""

    def __init__(self, device, *, ui=None, timeout=240):
        self.device = device
        self.ui = ui or AppRuntimeUi(device)
        self.timeout = timeout
        self.prior = None
        self.armed = False
        self.restored = False
        self.prepared = False

    def observe(self):
        self.device.close_protocol()
        self.device._socket = None
        try:
            self.device._connect("opencode")
            return "opencode"
        except DriverFailure:
            if getattr(self.device, "_runtime_mismatch", None) != {
                "expected": "opencode1",
                "observed": "opencode2",
            }:
                raise
        self.device.close_protocol()
        self.device._socket = None
        self.device._connect("opencode2")
        return "opencode2"

    def idle(self, engine):
        self.device.require_idle_setup()
        if service_foreground(self.device.services(), FGS):
            raise DriverFailure("runtime_not_idle")
        status = self.device.protocol(
            "GET", "/session/status" if engine == "opencode" else "/api/session/active"
        )
        if engine == "opencode2":
            status = status.get("data") if type(status) is dict else None
        if type(status) is not dict or any(
            type(value) is not dict or value.get("type") != "idle"
            for value in status.values()
        ):
            raise DriverFailure("runtime_not_idle")

    def wait(self, target):
        deadline = self.device.monotonic() + self.timeout
        while self.device.monotonic() < deadline:
            try:
                if self.observe() == target:
                    self.idle(target)
                    return
            except DriverFailure:
                pass
            self.device.sleep(1)
        raise DriverFailure("runtime_switch_timeout")

    def begin(self):
        if not self.device.locked or self.prior is not None:
            raise DriverFailure("runtime_switch_unavailable")
        self.device.require_idle_setup()
        self.prior = self.observe()
        self.idle(self.prior)
        self.prepared = True
        if self.prior == "opencode":
            return self.prior
        # Arm before any tap: UI can begin a switch before its caller returns.
        self.armed = True
        self.device.mutated = True
        self.ui.switch("opencode")
        self.wait("opencode")
        return self.prior

    def restore(self):
        if not self.prepared and not self.armed:
            return
        try:
            try:
                deadline = self.device.monotonic() + self.timeout
                while True:
                    try:
                        current = self.observe()
                        # No mutation is required when the original runtime is
                        # already back. An unrelated new turn must not make
                        # that observation look like failed restoration.
                        if current != self.prior:
                            self.idle(current)
                        break
                    except DriverFailure:
                        if self.device.monotonic() >= deadline:
                            raise
                        self.device.sleep(1)
                if current != self.prior:
                    self.ui.switch(self.prior)
                    self.wait(self.prior)
                self.restored = True
                self.armed = False
            finally:
                self.device.close_protocol()
        except Exception:
            raise DriverFailure("runtime_restore_failed") from None

    def facts(self):
        return {"prior": self.prior, "selected": "opencode", "restored": self.restored}
