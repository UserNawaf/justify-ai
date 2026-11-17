# Extra templates

Every `*.yml` file in this folder is loaded at start-up (`TEMPLATES_DIR`, mounted at `/templates` in Docker
Compose). A file with the same `id` as a bundled template overrides it. No code changes needed.

```yaml
id: access-request            # lowercase, digits, dashes
name: Access request           # shown as "Request type" in the result
description: Request access to a system.
fields:                        # order = order in the result
  - key: system                # lowercase, digits, underscores
    label: System
    required: true             # required fields are asked for when missing
    description: Which system or application. Shown to the model.
    question: Which system do you need access to?
  - key: role
    label: Role / permission
    required: true
    question: Which role or permission level do you need?
```

Restart the backend after adding a file. Invalid files stop the start-up with a clear error.
