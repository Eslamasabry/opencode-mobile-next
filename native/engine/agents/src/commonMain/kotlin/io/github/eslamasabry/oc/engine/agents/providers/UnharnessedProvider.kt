package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Runtime copies stay in this agent's profile-owned directory; stdin only, no output or ambient
 * credentials.
 */
public object UnharnessedProvider {
    public fun input(provider: OwnProvider?, key: String = ""): String =
        AgentJson.encode(
            JsonObject(
                mapOf(
                    "endpoint" to JsonPrimitive(provider?.endpoint.orEmpty()),
                    "model" to JsonPrimitive(provider?.model.orEmpty()),
                    "key" to JsonPrimitive(key),
                )
            )
        ) + "\n"

    public fun script(owner: OwnerId): String {
        require(Regex("^[A-Za-z0-9_-]{1,80}$").matches(owner.raw))
        return scriptAt("/home/oc/.oc-profiles/${owner.raw}/unharnessed")
    }

    internal fun scriptAt(path: String): String {
        require(
            path.startsWith("/") &&
                path.none { it == '\'' || it == '"' || it == '\\' || it.isISOControl() }
        )
        return "umask 077\nexec python3 -c '" + BODY.replace("@ROOT@", path) + "'\n"
    }

    private val BODY =
        """import json, os, pathlib, tempfile, sys
root = pathlib.Path("@ROOT@")
try:
    data = json.loads(sys.stdin.readline())
    endpoint, model, key = (data[name] for name in ("endpoint", "model", "key"))
    assert all(isinstance(value, str) for value in (endpoint, model, key))
    for parent in (root, *root.parents):
        assert not parent.is_symlink()
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    def write(name, value):
        target = root / name
        assert not target.is_symlink()
        fd, temporary = tempfile.mkstemp(prefix=".provider-", dir=root)
        try:
            with os.fdopen(fd, "w") as stream:
                json.dump(value, stream)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, target)
        finally:
            pathlib.Path(temporary).unlink(missing_ok=True)
    def read(name):
        target = root / name
        assert not target.is_symlink()
        if not target.exists(): return {}
        value = json.loads(target.read_text())
        assert isinstance(value, dict)
        return value
    models, settings = read("models.json"), read("settings.json")
    providers = models.get("providers", {})
    assert isinstance(providers, dict)
    if not endpoint:
        providers.pop("phone-openai", None)
        if providers: models["providers"] = providers
        else: models.pop("providers", None)
        if settings.get("defaultProvider") == "phone-openai":
            settings.pop("defaultProvider", None)
            settings.pop("defaultModel", None)
        for name, value in (("models.json", models), ("settings.json", settings)):
            if value: write(name, value)
            else: (root / name).unlink(missing_ok=True)
        (root / "phone-provider.json").unlink(missing_ok=True)
    else:
        providers["phone-openai"] = {
            "baseUrl": endpoint, "apiKey": key or "local", "api": "openai-completions",
            "models": [{"id": model, "name": model, "reasoning": False, "input": ["text"],
                        "contextWindow": 128000, "maxTokens": 16384}]}
        models["providers"] = providers
        settings.update({"defaultProvider": "phone-openai", "defaultModel": model})
        write("models.json", models)
        write("settings.json", settings)
        write("phone-provider.json", {"endpoint": endpoint, "model": model})
except Exception:
    sys.exit(3)
"""
}
