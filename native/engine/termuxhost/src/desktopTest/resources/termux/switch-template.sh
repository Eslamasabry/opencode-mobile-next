CURRENT_PORT=57643
CURRENT_OPERATION=fixture
{{before}}
printf '%s' {{_quote(password)}} > "$OC_DIR/switch-password-fixture"
switch_runtime {{_quote(target)}}
