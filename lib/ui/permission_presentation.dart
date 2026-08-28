String permissionRequestTitle(String permission) => switch (permission) {
  'bash' => 'Run a shell command',
  'edit' => 'Edit a file',
  'read' => 'Read a file',
  'external_directory' => 'Access an external directory',
  'doom_loop' => 'Continue after repeated failures',
  _ when permission.trim().isEmpty => 'Permission needed',
  _ => 'Use $permission',
};
